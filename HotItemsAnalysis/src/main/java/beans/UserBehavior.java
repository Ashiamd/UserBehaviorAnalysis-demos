package beans;

/**
 * @author : Ashiamd email: ashiamd@foxmail.com
 * @date : 2021/2/4 6:01 PM
 */
public class UserBehavior {
    private Long uerId;

    @Override
    public String toString() {
        return "UserBehavior{" +
                "uerId=" + uerId +
                ", itemId=" + itemId +
                ", categoryId=" + categoryId +
                ", behavior='" + behavior + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    public Long getUerId() {
        return uerId;
    }

    public void setUerId(Long uerId) {
        this.uerId = uerId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getBehavior() {
        return behavior;
    }

    public void setBehavior(String behavior) {
        this.behavior = behavior;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public UserBehavior(Long uerId, Long itemId, Integer categoryId, String behavior, Long timestamp) {
        this.uerId = uerId;
        this.itemId = itemId;
        this.categoryId = categoryId;
        this.behavior = behavior;
        this.timestamp = timestamp;
    }

    public UserBehavior() {
    }

    private Long itemId;
    private Integer categoryId;
    private String behavior;
    private Long timestamp;
}
