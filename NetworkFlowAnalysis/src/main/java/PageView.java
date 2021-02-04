
import beans.PageViewCount;
import beans.UserBehavior;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.runtime.operators.util.AssignerWithPeriodicWatermarksAdapter;

import org.apache.flink.util.Collector;

import java.net.URL;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @author : Ashiamd email: ashiamd@foxmail.com
 * @date : 2021/2/5 3:11 AM
 */
public class PageView {
    public static void main(String[] args) throws Exception {
        // 1. 创建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 设置并行度为4
        env.setParallelism(4);

        // 2. 从csv文件中获取数据
        URL resource = PageView.class.getResource("/UserBehavior.csv");
        DataStream<String> inputStream = env.readTextFile(resource.getPath());

        // 3. 转换成POJO,分配时间戳和watermark
        DataStream<UserBehavior> userBehaviorDataStream = inputStream.map(line -> {
            String[] fields = line.split(",");
            return new UserBehavior(new Long(fields[0]), new Long(fields[1]), new Integer(fields[2]), fields[3], new Long(fields[4]));
        }).assignTimestampsAndWatermarks(new AssignerWithPeriodicWatermarksAdapter.Strategy<>(
                new BoundedOutOfOrdernessTimestampExtractor<UserBehavior>(Time.of(200, TimeUnit.MILLISECONDS)) {
                    @Override
                    public long extractTimestamp(UserBehavior element) {
                        return element.getTimestamp() * 1000L;
                    }
                }
        ));

        // 4. 分组开窗聚合，得到每个窗口内各个商品的count值
        DataStream<Tuple2<String, Long>> pvResultStream0 = userBehaviorDataStream
                // 过滤只保留pv行为
                .filter(userBehavior -> "pv".equals(userBehavior.getBehavior()))
                .map(new MapFunction<UserBehavior, Tuple2<String, Long>>() {
                    @Override
                    public Tuple2<String, Long> map(UserBehavior value) throws Exception {
                        return new Tuple2<>("pv", 1L);
                    }
                })
                // 按照商品ID分组
                .keyBy(item -> item.f0)
                // 1小时滚动窗口
                .window(TumblingEventTimeWindows.of(Time.hours(1)))
                .sum(1);

        //  并行任务改进，设计随机key，解决数据倾斜问题
        SingleOutputStreamOperator<PageViewCount> pvStream = userBehaviorDataStream.filter(data -> "pv".equals(data.getBehavior()))
                .map(new MapFunction<UserBehavior, Tuple2<Integer, Long>>() {
                    @Override
                    public Tuple2<Integer, Long> map(UserBehavior value) throws Exception {
                        Random random = new Random();
                        return new Tuple2<>(random.nextInt(10), 1L);
                    }
                })
                .keyBy(data -> data.f0)
                .window(TumblingEventTimeWindows.of(Time.hours(1)))
                .aggregate(new PvCountAgg(), new PvCountResult());

        // 将各分区数据汇总起来
        DataStream<PageViewCount> pvResultStream = pvStream
                .keyBy(PageViewCount::getWindowEnd)
                .process(new TotalPvCount());
//                .sum("count");

        pvResultStream.print();

        env.execute("pv count job");
    }

    // 实现自定义预聚合函数
    public static class PvCountAgg implements AggregateFunction<Tuple2<Integer, Long>, Long, Long> {
        @Override
        public Long createAccumulator() {
            return 0L;
        }

        @Override
        public Long add(Tuple2<Integer, Long> value, Long accumulator) {
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

    // 实现自定义窗口
    public static class PvCountResult implements WindowFunction<Long, PageViewCount, Integer, TimeWindow> {
        @Override
        public void apply(Integer integer, TimeWindow window, Iterable<Long> input, Collector<PageViewCount> out) throws Exception {
            out.collect( new PageViewCount(integer.toString(), window.getEnd(), input.iterator().next()) );
        }
    }

    // 实现自定义处理函数，把相同窗口分组统计的count值叠加
    public static class TotalPvCount extends KeyedProcessFunction<Long, PageViewCount, PageViewCount> {
        // 定义状态，保存当前的总count值
        ValueState<Long> totalCountState;

        @Override
        public void open(Configuration parameters) throws Exception {
            totalCountState = getRuntimeContext().getState(new ValueStateDescriptor<Long>("total-count", Long.class));
        }

        @Override
        public void processElement(PageViewCount value, Context ctx, Collector<PageViewCount> out) throws Exception {
            Long totalCount = totalCountState.value();
            if(null == totalCount){
                totalCount = 0L;
                totalCountState.update(totalCount);
            }
            totalCountState.update( totalCount + value.getCount() );
            ctx.timerService().registerEventTimeTimer(value.getWindowEnd() + 1);
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<PageViewCount> out) throws Exception {
            // 定时器触发，所有分组count值都到齐，直接输出当前的总count数量
            Long totalCount = totalCountState.value();
            out.collect(new PageViewCount("pv", ctx.getCurrentKey(), totalCount));
            // 清空状态
            totalCountState.clear();
        }
    }
}
