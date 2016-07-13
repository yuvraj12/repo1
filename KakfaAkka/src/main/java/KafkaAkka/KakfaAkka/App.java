package KafkaAkka.KakfaAkka;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
//    	ConsumerToProducerWithBatchCommits2Example atMostOnceExample = new  ConsumerToProducerWithBatchCommits2Example();
//    	atMostOnceExample.demo();
//    	ProducerFlowExample producerFlowExample  = new ProducerFlowExample();
//    	producerFlowExample.demo();
    	PlainSinkExample plainSinkExample = new  PlainSinkExample();
    	plainSinkExample.demo();
    	
        System.out.println( "Hello World!" );
    }
}
