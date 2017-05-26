package iuno.tdm.vault;

import org.bitcoinj.core.*;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.wallet.KeyChain;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.management.Sensor;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
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

    public UserWallet(String userId, Context context) throws IOException {
        id = UUID.randomUUID();
        this.userId = userId;
        this.context = context;
        wallet = new Wallet(context);
        String workDir = System.getProperty("user.home") + "/." + PREFIX;
        new File(workDir).mkdirs();
        walletFile = new File(workDir, PREFIX + id +".wallet");
        try {
            wallet.autosaveToFile(walletFile, 5, TimeUnit.SECONDS, null).saveNow();
        } catch (IOException e) {
            logger.error(String.format("creating wallet file failed: %s", e.getMessage()));
            throw new IOException(String.format("creating wallet file failed: %s", e.getMessage()));
        }

    }


    public UserWallet(String walletId, String userId, Context context, String walletFileName) throws IOException, UnreadableWalletException {
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

    public void deleteWallet(){
        if(walletFile.exists()){
            walletFile.delete();
        }
    }

    public UUID getId(){
        return id;
    }

    public String getUserId(){
        return userId;
    }

    public File getWalletFile(){
        return walletFile;
    }

    public Coin getBalance(){
        return wallet.getBalance();
    }

    public Wallet getWallet(){
        return wallet;
    }

    public String getFreshAddress(){
        return wallet.freshReceiveAddress().toBase58();
    }

    public String getLastAddress(){
        return wallet.currentReceiveAddress().toBase58();
    }

    public Transaction payoutCredit(Address payoutAddress){
        SendRequest sendRequest = SendRequest.emptyWallet(payoutAddress);
        try {
            wallet.completeTx(sendRequest);
            wallet.commitTx(sendRequest.tx);
            return sendRequest.tx;

        } catch (InsufficientMoneyException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getPublicSeed(){
        return wallet.getActiveKeyChain().getWatchingKey().serializePubB58(context.getParams());
    }

    public TransactionOutput[] getTransactionOutputs(){
        List<TransactionOutput> transactionOutputs = wallet.getUnspents();
        return transactionOutputs.toArray(new TransactionOutput[transactionOutputs.size()]);
    }
}
