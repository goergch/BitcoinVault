package iuno.tdm.vault;

import io.swagger.model.*;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.DefaultRiskAnalysis;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Created by goergch on 23.05.17.
 */
public class UserWallet {

    private UUID id;
    private String userId;
    private Wallet wallet;
    private Context context;
    private static final String PREFIX = "Vault";
    private static final Logger logger = LoggerFactory.getLogger(UserWallet.class);
    private File walletFile;
    private HashMap<UUID, Payout> payouts = new HashMap<>();


    private PeerGroup peerGroup;

    public UserWallet(String userId, Context context, PeerGroup peerGroup) throws IOException {
        this.peerGroup = peerGroup;
        id = UUID.randomUUID();
        this.userId = userId;
        this.context = context;
        wallet = new Wallet(context);
        String workDir = System.getProperty("user.home") + "/." + PREFIX;
        new File(workDir).mkdirs();
        walletFile = new File(workDir, PREFIX + id + ".wallet");
        try {
            wallet.autosaveToFile(walletFile, 5, TimeUnit.SECONDS, null).saveNow();
        } catch (IOException e) {
            logger.error(String.format("creating wallet file failed: %s", e.getMessage()));
            throw new IOException(String.format("creating wallet file failed: %s", e.getMessage()));
        }

    }


    public UserWallet(String walletId, String userId, Context context, String walletFileName, PeerGroup peerGroup) throws IOException, UnreadableWalletException {
        this.peerGroup = peerGroup;
        id = UUID.fromString(walletId);
        this.userId = userId;
        this.context = context;
        walletFile = new File(walletFileName);
        wallet = Wallet.loadFromFile(walletFile);

        try {
            wallet.autosaveToFile(walletFile, 5, TimeUnit.SECONDS, null).saveNow();
        } catch (IOException e) {
            logger.error(String.format("creating wallet file failed: %s", e.getMessage()));
            throw new IOException(String.format("creating wallet file failed: %s", e.getMessage()));
        }

    }

    public void deleteWallet() {
        if (walletFile.exists()) {
            walletFile.delete();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public File getWalletFile() {
        return walletFile;
    }

    public Coin getBalance() {
        return wallet.getBalance(Wallet.BalanceType.ESTIMATED);
    }

    public Coin getConfirmedBalance() {
        return wallet.getBalance(Wallet.BalanceType.AVAILABLE);
    }

    public Wallet getWallet() {
        return wallet;
    }

    public String getFreshAddress() {
        return wallet.freshReceiveAddress().toBase58();
    }


    public String getPublicSeed() {
        return wallet.getActiveKeyChain().getWatchingKey().serializePubB58(context.getParams());
    }

    public TransactionOutput[] getTransactionOutputs() {
        List<TransactionOutput> transactionOutputs = wallet.getUnspents();
        return transactionOutputs.toArray(new TransactionOutput[transactionOutputs.size()]);
    }

    private SendRequest getSendRequestforPayout(Payout payout, Coin balance) {
        // fail if wallet contains less than twice the dust value
        if (wallet.getBalance().isLessThan(DefaultRiskAnalysis.MIN_ANALYSIS_NONDUST_OUTPUT.multiply(2))) {
            throw new IllegalArgumentException("Wallet is empty"); // TODO This error is not about an illegal argument!
        }

        SendRequest sendRequest;
        if (payout.getEmptyWallet()) {
            sendRequest = SendRequest.emptyWallet(Address.fromBase58(context.getParams(), payout.getPayoutAddress()));

        } else {
            sendRequest = SendRequest.to(Address.fromBase58(context.getParams(), payout.getPayoutAddress()),
                    Coin.valueOf(payout.getAmount()));
        }

        return sendRequest;
    }

    public Payout addPayout(Payout payout) {
        payout.setPayoutId(UUID.randomUUID());
        SendRequest sendRequest = getSendRequestforPayout(payout, wallet.getBalance());
        logger.debug(sendRequest.tx.getFee().toString());

        try {
            wallet.completeTx(sendRequest);
            wallet.commitTx(sendRequest.tx);
            peerGroup.broadcastTransaction(sendRequest.tx).broadcast();
        } catch (InsufficientMoneyException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        payouts.put(payout.getPayoutId(), payout);

        return payout;
    }

    public Payout getPayout(UUID payoutId) {
        if (!payouts.containsKey(payoutId)) {
            throw new NullPointerException("There is no payout with the id " + payoutId);
        }

        return payouts.get(payoutId);
    }

    public io.swagger.model.Transaction[] getTransactionsForPayout(UUID payoutId) {
        return null;
    }

    public UUID[] getPayoutIDs() {
        UUID[] uuids = payouts.keySet().toArray(new UUID[payouts.keySet().size()]);
        return uuids;
    }

    public PayoutCheck checkPayout(Payout payout) {
        payout.setPayoutId(UUID.randomUUID());
        SendRequest sendRequest = getSendRequestforPayout(payout, wallet.getBalance(Wallet.BalanceType.ESTIMATED));

        Coin remaining = Coin.ZERO;
        Coin fee = Coin.ZERO;
        try {
            wallet.completeTx(sendRequest);
            remaining = wallet.getBalance().subtract(sendRequest.tx.getValueSentFromMe(wallet).subtract(sendRequest.tx.getValueSentToMe(wallet)));
            fee = sendRequest.tx.getFee();
        } catch (InsufficientMoneyException e) {
            remaining = e.missing.multiply(-1);
            fee = wallet.getBalance().add(e.missing).subtract(Coin.valueOf(payout.getAmount()));

        }

        PayoutCheck pc = new PayoutCheck()
                .fee((int) fee.value)
                .remaining((int) remaining.value);
        return pc;
    }
}
