import beans.OrderEvent;
import beans.ReceiptEvent;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.CoProcessFunction;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.runtime.operators.util.AssignerWithPeriodicWatermarksAdapter;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * @author : Ashiamd email: ashiamd@foxmail.com
 * @date : 2021/2/6 5:34 PM
 */
public class TxPayMatch {

    // 定义侧输出流标签
    private final static OutputTag<OrderEvent> unmatchedPays = new OutputTag<OrderEvent>("unmatched-pays"){};
    private final static OutputTag<ReceiptEvent> unmatchedReceipts = new OutputTag<ReceiptEvent>("unmatched-receipts"){};

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 读取数据并转换成POJO类型
        // 读取订单支付事件数据
        URL orderResource = TxPayMatch.class.getResource("/OrderLog.csv");
        DataStream<OrderEvent> orderEventStream = env.readTextFile(orderResource.getPath())
                .map(line -> {
                    String[] fields = line.split(",");
                    return new OrderEvent(new Long(fields[0]), fields[1], fields[2], new Long(fields[3]));
                })
                .assignTimestampsAndWatermarks(new AssignerWithPeriodicWatermarksAdapter.Strategy<>(
                        new BoundedOutOfOrdernessTimestampExtractor<OrderEvent>(Time.of(200, TimeUnit.MILLISECONDS)) {
                            @Override
                            public long extractTimestamp(OrderEvent element) {
                                return element.getTimestamp() * 1000L;
                            }
                        }
                ))
                // 交易id不为空，必须是pay事件
                .filter(data -> !"".equals(data.getTxId()));

        // 读取到账事件数据
        URL receiptResource = TxPayMatch.class.getResource("/ReceiptLog.csv");
        SingleOutputStreamOperator<ReceiptEvent> receiptEventStream = env.readTextFile(receiptResource.getPath())
                .map(line -> {
                    String[] fields = line.split(",");
                    return new ReceiptEvent(fields[0], fields[1], new Long(fields[2]));
                })
                .assignTimestampsAndWatermarks(new AssignerWithPeriodicWatermarksAdapter.Strategy<>(
                        new BoundedOutOfOrdernessTimestampExtractor<ReceiptEvent>(Time.of(200, TimeUnit.MILLISECONDS)) {
                            @Override
                            public long extractTimestamp(ReceiptEvent element) {
                                return element.getTimestamp() * 1000L;
                            }
                        }
                ));

        // 将两条流进行连接合并，进行匹配处理，不匹配的事件输出到侧输出流
        SingleOutputStreamOperator<Tuple2<OrderEvent, ReceiptEvent>> resultStream = orderEventStream.keyBy(OrderEvent::getTxId)
                .connect(receiptEventStream.keyBy(ReceiptEvent::getTxId))
                .process(new TxPayMatchDetect());

        resultStream.print("matched-pays");
        resultStream.getSideOutput(unmatchedPays).print("unmatched-pays");
        resultStream.getSideOutput(unmatchedReceipts).print("unmatched-receipts");

        env.execute("tx match detect job");
    }

    // 实现自定义CoProcessFunction
    public static class TxPayMatchDetect extends CoProcessFunction<OrderEvent, ReceiptEvent, Tuple2<OrderEvent, ReceiptEvent>> {
        // 定义状态，保存当前已经到来的订单支付事件和到账时间
        ValueState<OrderEvent> payState;
        ValueState<ReceiptEvent> receiptState;

        @Override
        public void open(Configuration parameters) throws Exception {
            payState = getRuntimeContext().getState(new ValueStateDescriptor<OrderEvent>("pay", OrderEvent.class));
            receiptState = getRuntimeContext().getState(new ValueStateDescriptor<ReceiptEvent>("receipt", ReceiptEvent.class));
        }

        @Override
        public void processElement1(OrderEvent pay, Context ctx, Collector<Tuple2<OrderEvent, ReceiptEvent>> out) throws Exception {
            // 订单支付事件来了，判断是否已经有对应的到账事件
            ReceiptEvent receipt = receiptState.value();
            if( receipt != null ){
                // 如果receipt不为空，说明到账事件已经来过，输出匹配事件，清空状态
                out.collect( new Tuple2<>(pay, receipt) );
                payState.clear();
                receiptState.clear();
            } else {
                // 如果receipt没来，注册一个定时器，开始等待
                ctx.timerService().registerEventTimeTimer( (pay.getTimestamp() + 5) * 1000L );    // 等待5秒钟，具体要看数据
                // 更新状态
                payState.update(pay);
            }
        }

        @Override
        public void processElement2(ReceiptEvent receipt, Context ctx, Collector<Tuple2<OrderEvent, ReceiptEvent>> out) throws Exception {
            // 到账事件来了，判断是否已经有对应的支付事件
            OrderEvent pay = payState.value();
            if( pay != null ){
                // 如果pay不为空，说明支付事件已经来过，输出匹配事件，清空状态
                out.collect( new Tuple2<>(pay, receipt) );
                payState.clear();
                receiptState.clear();
            } else {
                // 如果pay没来，注册一个定时器，开始等待
                ctx.timerService().registerEventTimeTimer( (receipt.getTimestamp() + 3) * 1000L );    // 等待3秒钟，具体要看数据
                // 更新状态
                receiptState.update(receipt);
            }
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<Tuple2<OrderEvent, ReceiptEvent>> out) throws Exception {
            // 定时器触发，有可能是有一个事件没来，不匹配，也有可能是都来过了，已经输出并清空状态
            // 判断哪个不为空，那么另一个就没来
            if( payState.value() != null ){
                ctx.output(unmatchedPays, payState.value());
            }
            if( receiptState.value() != null ){
                ctx.output(unmatchedReceipts, receiptState.value());
            }
            // 清空状态
            payState.clear();
            receiptState.clear();
        }
    }
}
