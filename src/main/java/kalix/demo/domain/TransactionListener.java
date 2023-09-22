package kalix.demo.domain;

import kalix.demo.Done;
import kalix.demo.domain.Transaction.Event.TransactionCreated;
import kalix.demo.domain.Transaction.Event.TransactionAlreadyCreated;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.springframework.beans.factory.annotation.Autowired;

@Subscribe.EventSourcedEntity(value = Transaction.class, ignoreUnknown = true)
public class TransactionListener extends Action {

  final private ComponentClient componentClient;

  public TransactionListener(@Autowired ComponentClient componentClient) {
    this.componentClient = componentClient;
  }


  public Effect<Done> onEvent(TransactionCreated evt) {
    var call =
      componentClient
        .forEventSourcedEntity(evt.walletId())
        .call(Wallet::execute)
        .params(evt.commandId());

    return effects().forward(call);
  }

  public Effect<Done> onEvent(TransactionAlreadyCreated evt) {
    var call =
      componentClient
        .forEventSourcedEntity(evt.walletId())
        .call(Wallet::cancel)
        .params(evt.commandId());

    return effects().forward(call);
  }

}
