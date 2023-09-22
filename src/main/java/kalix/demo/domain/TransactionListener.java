package kalix.demo.domain;

import kalix.demo.Done;
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

  public Effect<Done> onEvent(Transaction.TransactionCreated evt) {
    var confirmation =
      componentClient
        .forEventSourcedEntity(evt.walletId())
        .call(Wallet::execute)
        .params(evt.commandId())
        .execute();

    return effects().asyncReply(confirmation);
  }

  public Effect<Done> onEvent(Transaction.TransactionAlreadyCreated evt) {
    var confirmation =
      componentClient
        .forEventSourcedEntity(evt.walletId())
        .call(Wallet::cancel)
        .params(evt.commandId())
        .execute();

    return effects().asyncReply(confirmation);
  }

}
