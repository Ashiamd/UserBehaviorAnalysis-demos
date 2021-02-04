package beans;

/**
 * @author : Ashiamd email: ashiamd@foxmail.com
 * @date : 2021/2/5 5:25 AM
 */
public class BlackListUserWarning {
    private Long userId;
    private Long adId;
    private String warningMsg;

    @Override
    public String toString() {
        return "beans.BlackListUserWarning{" +
                "userId=" + userId +
                ", adId=" + adId +
                ", warningMsg='" + warningMsg + '\'' +
                '}';
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getAdId() {
        return adId;
    }

    public void setAdId(Long adId) {
        this.adId = adId;
    }

    public String getWarningMsg() {
        return warningMsg;
    }

    public void setWarningMsg(String warningMsg) {
        this.warningMsg = warningMsg;
    }

    public BlackListUserWarning() {
    }

    public BlackListUserWarning(Long userId, Long adId, String warningMsg) {
        this.userId = userId;
        this.adId = adId;
        this.warningMsg = warningMsg;
    }
}
