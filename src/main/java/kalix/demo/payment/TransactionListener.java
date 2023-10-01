package kalix.demo.payment;

import kalix.demo.Done;
import kalix.demo.transactions.TransactionMediator;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.springframework.beans.factory.annotation.Autowired;

@Subscribe.EventSourcedEntity(value = TransactionMediator.class, ignoreUnknown = true)
public class TransactionListener extends Action {

  final private ComponentClient componentClient;

  public TransactionListener(@Autowired ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Done> onEvent(TransactionMediator.Event.Completed evt) {
    var call =
      componentClient
      .forWorkflow(evt.transactionId())
      .call(TransferWorkflow::complete);

    return effects().forward(call);
  }
}
