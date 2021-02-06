package beans;

/**
 * @author : Ashiamd email: ashiamd@foxmail.com
 * @date : 2021/2/6 5:29 PM
 */
public class ReceiptEvent {
    private String txId;
    private String payChannel;
    private Long timestamp;

    @Override
    public String toString() {
        return "ReceiptEvent{" +
                "txId='" + txId + '\'' +
                ", payChannel='" + payChannel + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    public String getTxId() {
        return txId;
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public String getPayChannel() {
        return payChannel;
    }

    public void setPayChannel(String payChannel) {
        this.payChannel = payChannel;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public ReceiptEvent() {
    }

    public ReceiptEvent(String txId, String payChannel, Long timestamp) {
        this.txId = txId;
        this.payChannel = payChannel;
        this.timestamp = timestamp;
    }
}
