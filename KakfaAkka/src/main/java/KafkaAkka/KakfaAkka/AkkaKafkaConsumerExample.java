package KafkaAkka.KakfaAkka;

import akka.Done;
import akka.actor.ActorSystem;
import akka.kafka.ConsumerMessage;
import akka.kafka.ConsumerMessage.CommittableOffsetBatch;
import akka.kafka.ConsumerSettings;
import akka.kafka.ProducerMessage;
import akka.kafka.ProducerSettings;
import akka.kafka.javadsl.Consumer;
import akka.kafka.javadsl.Producer;
import akka.stream.javadsl.Source;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import scala.concurrent.duration.Duration;

abstract class AkkaKafkaConsumerExample {
  protected final ActorSystem system = ActorSystem.create("example");

  protected final ConsumerSettings<byte[], String> consumerSettings =
      ConsumerSettings.create(system, new ByteArrayDeserializer(), new StringDeserializer(),
          ConsumerSettings.asSet("topic3"))
    .withBootstrapServers("localhost:9092")
    .withGroupId("group1")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

  protected final ProducerSettings<byte[], String> producerSettings =
      ProducerSettings.create(system, new ByteArraySerializer(), new StringSerializer())
    .withBootstrapServers("localhost:9092");

  static class DB {
    public CompletionStage<Done> save(ConsumerRecord<byte[], String> record) {
      throw new IllegalStateException("not implemented");
    }

    public CompletionStage<Long> loadOffset() {
      throw new IllegalStateException("not implemented");
    }

    public CompletionStage<Done> update(String data) {
      throw new IllegalStateException("not implemented");
    }
  }

  static class Rocket {
    public CompletionStage<Done> launch(String destination) {
      throw new IllegalStateException("not implemented");
    }
  }
}

// Consume messages and store a representation, including offset, in DB
class ExternalOffsetStorageExample extends AkkaKafkaConsumerExample {

  public void demo() {
    final DB db = new DB();

    db.loadOffset().thenAccept(fromOffset -> {
      ConsumerSettings<byte[], String> settings = consumerSettings
        .withFromOffset(new TopicPartition("topic1", 1), fromOffset);
      Consumer.plainSource(settings).mapAsync(1, record -> db.save(record));
    });
  }
}

//// Consume messages at-most-once
class AtMostOnceExample extends AkkaKafkaConsumerExample {
  public void demo() {
    final Rocket rocket = new Rocket();

    Consumer.atMostOnceSource(consumerSettings.withClientId("client1"))
      .mapAsync(1, record -> rocket.launch(record.value()));
  }
}

//// Consume messages at-least-once
class AtLeastOnceExample extends AkkaKafkaConsumerExample {
  public void demo() {
    final DB db = new DB();

    Consumer.committableSource(consumerSettings.withClientId("client1"))
      .mapAsync(1, msg -> db.update(msg.value())
        .thenCompose(done -> msg.committableOffset().commitJavadsl()));
  }
}

//// Consume messages at-least-once, and commit in batches
class AtLeastOnceWithBatchCommitExample extends AkkaKafkaConsumerExample {
  public void demo() {
    final DB db = new DB();

    Consumer.committableSource(consumerSettings.withClientId("client1"))
      .mapAsync(1, msg ->
        db.update(msg.value()).thenApply(done -> msg.committableOffset()))
      .batch(10,
        first -> ConsumerMessage.emptyCommittableOffsetBatch().updated(first),
        (batch, elem) -> batch.updated(elem))
      .mapAsync(1, c -> c.commitJavadsl());
  }
}

// Connect a Consumer to Producer
class ConsumerToProducerSinkExample extends AkkaKafkaConsumerExample {
  public void demo() {
    Consumer.committableSource(consumerSettings.withClientId("client1"))
      .map(msg ->
        new ProducerMessage.Message<byte[], String, ConsumerMessage.Committable>(
            new ProducerRecord<>("topic2", msg.value()), msg.committableOffset()))
      .to(Producer.commitableSink(producerSettings));
  }
}

//// Connect a Consumer to Producer
class ConsumerToProducerFlowExample extends AkkaKafkaConsumerExample {
  public void demo() {
    Consumer.committableSource(consumerSettings.withClientId("client1"))
      .map(msg ->
        new ProducerMessage.Message<byte[], String, ConsumerMessage.Committable>(
          new ProducerRecord<>("topic2", msg.value()), msg.committableOffset()))
      .via(Producer.flow(producerSettings))
      .mapAsync(producerSettings.parallelism(), result ->
        result.message().passThrough().commitJavadsl());
  }
}

// Connect a Consumer to Producer, and commit in batches
class ConsumerToProducerWithBatchCommitsExample extends AkkaKafkaConsumerExample {
  public void demo() {
    Source<ConsumerMessage.CommittableOffset, Consumer.Control> source =
      Consumer.committableSource(consumerSettings.withClientId("client1"))
        .map(msg ->
            new ProducerMessage.Message<byte[], String, ConsumerMessage.CommittableOffset>(
                new ProducerRecord<>("topic2", msg.value()), msg.committableOffset()))
        .via(Producer.flow(producerSettings))
        .map(result -> result.message().passThrough());

        source.batch(10,
            first -> ConsumerMessage.emptyCommittableOffsetBatch().updated(first),
            (batch, elem) -> batch.updated(elem))
          .mapAsync(1, c -> c.commitJavadsl());
  }
}

//// Connect a Consumer to Producer, and commit in batches
class ConsumerToProducerWithBatchCommits2Example extends AkkaKafkaConsumerExample {
  public void demo() {
    Source<ConsumerMessage.CommittableOffset, Consumer.Control> source =
      Consumer.committableSource(consumerSettings.withClientId("client1"))
        .map(msg ->
            new ProducerMessage.Message<byte[], String, ConsumerMessage.CommittableOffset>(
                new ProducerRecord<>("topic3", msg.value()), msg.committableOffset()))
        .via(Producer.flow(producerSettings))
        .map(result -> result.message().passThrough());

        source
          .groupedWithin(10, Duration.create(5, TimeUnit.SECONDS))
          .map(group -> foldLeft(group))
          .mapAsync(1, c -> c.commitJavadsl());
  }

  private ConsumerMessage.CommittableOffsetBatch foldLeft(List<ConsumerMessage.CommittableOffset> group) {
    ConsumerMessage.CommittableOffsetBatch batch = ConsumerMessage.emptyCommittableOffsetBatch();
    for (ConsumerMessage.CommittableOffset elem: group) {
      batch = batch.updated(elem);
    }
    return batch;
  }
}



/** Old approach
Runnable oldRunner = new Runnable(){
    public void run(){
        System.out.println("I am running");
    }
};
 	new approach
Runnable java8Runner = () ->{
    System.out.println("I am running");
};*/
