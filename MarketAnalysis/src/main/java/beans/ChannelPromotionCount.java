package beans;

/**
 * @author : Ashiamd email: ashiamd@foxmail.com
 * @date : 2021/2/5 5:25 AM
 */
public class ChannelPromotionCount {
    private String channel;
    private String behavior;
    private String windowEnd;
    private Long count;

    @Override
    public String toString() {
        return "beans.ChannelPromotionCount{" +
                "channel='" + channel + '\'' +
                ", behavior='" + behavior + '\'' +
                ", windowEnd='" + windowEnd + '\'' +
                ", count=" + count +
                '}';
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getBehavior() {
        return behavior;
    }

    public void setBehavior(String behavior) {
        this.behavior = behavior;
    }

    public String getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(String windowEnd) {
        this.windowEnd = windowEnd;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    public ChannelPromotionCount() {
    }

    public ChannelPromotionCount(String channel, String behavior, String windowEnd, Long count) {
        this.channel = channel;
        this.behavior = behavior;
        this.windowEnd = windowEnd;
        this.count = count;
    }
}
