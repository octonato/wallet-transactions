package kalix.demo.domain;

import kalix.demo.Done;
import kalix.demo.domain.Wallet.Event.*;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.springframework.beans.factory.annotation.Autowired;

@Subscribe.EventSourcedEntity(value = Wallet.class, ignoreUnknown = true)
public class WalletListener  extends Action {

  final private ComponentClient componentClient;

  public WalletListener(@Autowired ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Done> onEvent(DepositInitiated evt) {
    var confirmation =
      componentClient
        .forEventSourcedEntity(evt.commandId())
        .call(Transaction::createTransaction)
        .params(evt.walletId())
        .execute();

    return effects().asyncReply(confirmation);
  }

  public Effect<Done> onEvent(Deposited evt) {
    var confirmation =
      componentClient
        .forEventSourcedEntity(evt.commandId())
        .call(Transaction::confirm)
        .params(evt.walletId())
        .execute();

    return effects().asyncReply(confirmation);
  }


  public Effect<Done> onEvent(WithdrawInitiated evt) {
    var confirmation =
      componentClient
        .forEventSourcedEntity(evt.commandId())
        .call(Transaction::createTransaction)
        .params(evt.walletId())
        .execute();

    return effects().asyncReply(confirmation);
  }

  public Effect<Done> onEvent(Withdrew evt) {
    var confirmation =
      componentClient
        .forEventSourcedEntity(evt.commandId())
        .call(Transaction::confirm)
        .params(evt.walletId())
        .execute();

    return effects().asyncReply(confirmation);
  }


}
