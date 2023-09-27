package kalix.demo.payment;

import kalix.demo.Done;
import kalix.demo.transactions.TransactionAdapter;
import kalix.javasdk.client.ComponentClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionStage;

@Component
public class WalletAdapter implements TransactionAdapter {


  @Override
  public boolean adapterFor(Class<?> clazz) {
    return clazz.equals(Wallet.class);
  }

  @Override
  public CompletionStage<Done> initialized(ComponentClient componentClient,
                                           String participantId,
                                           String transactionId) {
    return componentClient
      .forEventSourcedEntity(participantId)
      .call(Wallet::execute)
      .params(transactionId)
      .execute();
  }

  @Override
  public CompletionStage<Done> cancel(ComponentClient componentClient,
                                      String participantId,
                                      String transactionId) {
    return componentClient
      .forEventSourcedEntity(participantId)
      .call(Wallet::cancel)
      .params(transactionId)
      .execute();
  }

  @Override
  public CompletionStage<Done> complete(ComponentClient componentClient,
                                        String participantId,
                                        String transactionId) {
    return componentClient
      .forEventSourcedEntity(participantId)
      .call(Wallet::complete)
      .params(transactionId)
      .execute();
  }
}
