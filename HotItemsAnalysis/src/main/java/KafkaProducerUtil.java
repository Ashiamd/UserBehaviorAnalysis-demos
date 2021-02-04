import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.util.Properties;

/**
 * @author : Ashiamd email: ashiamd@foxmail.com
 * @date : 2021/2/4 11:53 PM
 */
public class KafkaProducerUtil {
    public static void main(String[] args) throws Exception {
        writeToKafka("hotitems");
    }

    public static void writeToKafka(String topic)throws Exception{
        // Kafka配置
        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "localhost:9092");
        properties.setProperty("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.setProperty("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        // 定义一个Kafka Producer
        KafkaProducer<String, String> kafkaProducer = new KafkaProducer<String, String>(properties);

        // 用缓冲方式来读取文本
        BufferedReader bufferedReader = new BufferedReader(new FileReader("/Users/ashiamd/mydocs/docs/study/javadocument/javadocument/IDEA_project/UserBehaviorAnalysis/HotItemsAnalysis/src/main/resources/UserBehavior.csv"));
        String line;
        while((line = bufferedReader.readLine())!=null){
            ProducerRecord<String,String> producerRecord = new ProducerRecord<>(topic,line );
            // 用producer发送数据
            kafkaProducer.send(producerRecord);
        }
        kafkaProducer.close();
    }
}
