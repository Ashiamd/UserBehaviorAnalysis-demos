import beans.ApacheLogEvent;
import beans.PageViewCount;
import org.apache.commons.compress.utils.Lists;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.runtime.operators.util.AssignerWithPeriodicWatermarksAdapter;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author : Ashiamd email: ashiamd@foxmail.com
 * @date : 2021/2/5 1:27 AM
 */
public class HotPages {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 读取文件，转换成POJO
//        URL resource = HotPages.class.getResource("/apache.log");
//        DataStream<String> inputStream = env.readTextFile(resource.getPath());

        // 方便测试，使用本地Socket输入数据
        DataStream<String> inputStream = env.socketTextStream("localhost", 7777);


        DataStream<ApacheLogEvent> dataStream = inputStream
                .map(line -> {
                    String[] fields = line.split(" ");
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy:HH:mm:ss");
                    Long timestamp = simpleDateFormat.parse(fields[3]).getTime();
                    return new ApacheLogEvent(fields[0], fields[1], timestamp, fields[5], fields[6]);
                })
                .assignTimestampsAndWatermarks(new AssignerWithPeriodicWatermarksAdapter.Strategy<>(
                        new BoundedOutOfOrdernessTimestampExtractor<ApacheLogEvent>(Time.of(1, TimeUnit.SECONDS)) {
                            @Override
                            public long extractTimestamp(ApacheLogEvent element) {
                                return element.getTimestamp();
                            }
                        }
                ));

        dataStream.print("data");


        // 定义一个侧输出流标签
        OutputTag<ApacheLogEvent> lateTag = new OutputTag<ApacheLogEvent>("late") {
        };

        // 分组开窗聚合
        SingleOutputStreamOperator<PageViewCount> windowAggStream = dataStream
                // 过滤get请求
                .filter(data -> "GET".equals(data.getMethod()))
                .filter(data -> {
                    String regex = "^((?!\\.(css|js|png|ico)$).)*$";
                    return Pattern.matches(regex, data.getUrl());
                })
                // 按照url分组
                .keyBy(ApacheLogEvent::getUrl)
                .window(SlidingEventTimeWindows.of(Time.minutes(10), Time.seconds(5)))
                .allowedLateness(Time.minutes(1))
                .sideOutputLateData(lateTag)
                .aggregate(new PageCountAgg(), new PageCountResult());


        windowAggStream.print("agg");
        windowAggStream.getSideOutput(lateTag).print("late");


        // 收集同一窗口count数据，排序输出
        DataStream<String> resultStream = windowAggStream
                .keyBy(PageViewCount::getWindowEnd)
                .process(new TopNHotPages(3));

        resultStream.print();

        env.execute("hot pages job");
    }

    // 自定义预聚合函数
    public static class PageCountAgg implements AggregateFunction<ApacheLogEvent, Long, Long> {
        @Override
        public Long createAccumulator() {
            return 0L;
        }

        @Override
        public Long add(ApacheLogEvent value, Long accumulator) {
            return accumulator + 1;
        }

        @Override
        public Long getResult(Long accumulator) {
            return accumulator;
        }

        @Override
        public Long merge(Long a, Long b) {
            return a + b;
        }
    }

    // 实现自定义的窗口函数
    public static class PageCountResult implements WindowFunction<Long, PageViewCount, String, TimeWindow> {
        @Override
        public void apply(String url, TimeWindow window, Iterable<Long> input, Collector<PageViewCount> out) throws Exception {
            out.collect(new PageViewCount(url, window.getEnd(), input.iterator().next()));
        }
    }

    // 实现自定义的处理函数
    public static class TopNHotPages extends KeyedProcessFunction<Long, PageViewCount, String> {
        private Integer topSize;

        public TopNHotPages(Integer topSize) {
            this.topSize = topSize;
        }

        // 定义状态，保存当前所有PageViewCount到Map中
//        ListState<PageViewCount> pageViewCountListState;
        MapState<String, Long> pageViewCountMapState;

        @Override
        public void open(Configuration parameters) throws Exception {
//            pageViewCountListState = getRuntimeContext().getListState(new ListStateDescriptor<PageViewCount>("page-count-list", PageViewCount.class));
            pageViewCountMapState = getRuntimeContext().getMapState(new MapStateDescriptor<String, Long>("page-count-map", String.class, Long.class));
        }

        @Override
        public void processElement(PageViewCount value, Context ctx, Collector<String> out) throws Exception {
//            pageViewCountListState.add(value);
            pageViewCountMapState.put(value.getUrl(), value.getCount());
            ctx.timerService().registerEventTimeTimer(value.getWindowEnd() + 1);
            // 注册一个1分钟之后的定时器，用来清空状态
            ctx.timerService().registerEventTimeTimer(value.getWindowEnd() + 60 * 1000L);
        }


        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
            // 先判断是否到了窗口关闭清理时间，如果是，直接清空状态返回
            if (timestamp == ctx.getCurrentKey() + 60 * 1000L) {
                pageViewCountMapState.clear();
                return;
            }


            //            ArrayList<PageViewCount> pageViewCounts = Lists.newArrayList(pageViewCountListState.get().iterator());
            ArrayList<Map.Entry<String, Long>> pageViewCounts = Lists.newArrayList(pageViewCountMapState.entries().iterator());

            pageViewCounts.sort((a, b) -> -Long.compare(a.getValue(), b.getValue()));

            // 格式化成String输出
            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append("============================").append(System.lineSeparator());
            resultBuilder.append("窗口结束时间：").append(new Timestamp(timestamp - 1)).append(System.lineSeparator());

            // 遍历列表，取top n输出
            for (int i = 0; i < Math.min(topSize, pageViewCounts.size()); i++) {
//                PageViewCount pageViewCount = pageViewCounts.get(i);
                Map.Entry<String, Long> pageViewCount = pageViewCounts.get(i);
                resultBuilder.append("NO ").append(i + 1).append(":")
                        .append(" 页面URL = ").append(pageViewCount.getKey())
                        .append(" 浏览量 = ").append(pageViewCount.getValue())
                        .append(System.lineSeparator());
            }
            resultBuilder.append("===============================").append(System.lineSeparator());

            // 控制输出频率
            Thread.sleep(1000L);

            out.collect(resultBuilder.toString());


//            pageViewCountListState.clear();
        }
    }
}
