package KafkaAkka.KakfaAkka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import akka.actor.ActorSystem;
import akka.japi.function.Function;
import akka.kafka.ProducerMessage;
import akka.kafka.ProducerSettings;
import akka.kafka.javadsl.Producer;
import akka.stream.javadsl.Source;

abstract class AkkaKafkaProducerExample {
  protected final ActorSystem system = ActorSystem.create("example");

  protected final ProducerSettings<byte[], String> producerSettings = ProducerSettings
    .create(system, new ByteArraySerializer(), new StringSerializer())
    .withBootstrapServers("localhost:9092");
  
}

class PlainSinkExample extends AkkaKafkaProducerExample {
  public void demo() {
	  final KafkaProducer<byte[], String> producerSettings11 = ProducerSettings
			    .create(system, new ByteArraySerializer(), new StringSerializer())
			    .withBootstrapServers("localhost:9092").createKafkaProducer();
	  
    Source.range(1, 10000)
      .map(n -> n.toString()).map(elem -> new ProducerRecord<byte[], String>("topic3", elem))
      
      .to(Producer.plainSink(producerSettings));
  }
}

class ProducerFlowExample extends AkkaKafkaProducerExample {
  public void demo() {
	  
    Source.range(1, 10000)
      .map(n -> new ProducerMessage.Message<byte[], String, Integer>(
        new ProducerRecord<>("topic3", n.toString()), n))
      .via(Producer.flow(producerSettings))
      .map(result -> {
        ProducerRecord record = result.message().record();
        System.out.println(record);
        return result;
      });
  }
}