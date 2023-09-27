package kalix.demo.transactions;

import kalix.demo.Done;
import kalix.javasdk.client.ComponentClient;

import java.util.concurrent.CompletionStage;

public interface TransactionAdapter {

  boolean adapterFor(Class<?> clazz);
  
  CompletionStage<Done> initialized(ComponentClient componentClient,
                                    String participantId,
                                    String transactionId);

  CompletionStage<Done> cancel(ComponentClient componentClient,
                               String participantId,
                               String transactionId);

  CompletionStage<Done> complete(ComponentClient componentClient,
                                 String participantId,
                                 String transactionId);
}
