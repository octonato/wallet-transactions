package kalix.demo.domain;

import kalix.demo.Done;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

public class TransactionListener extends Action {

  final private ComponentClient componentClient;

  public TransactionListener(@Autowired ComponentClient componentClient) {
    this.componentClient = componentClient;
  }


  @Subscribe.EventSourcedEntity(Wallet.class)
  public Effect<Done> onEvent(Wallet.Deposited evt) {
    var confirmation =
    componentClient
      .forEventSourcedEntity(evt.txId())
      .call(Transaction::registerDeposit)
      .params(new Transaction.RegisterDeposit(evt.walletId(), evt.eventIt(), evt.amount()))
      .execute();
    return effects().asyncReply(confirmation);

  }

  @Subscribe.EventSourcedEntity(Wallet.class)
  public Effect<Done> onEvent(Wallet.Withdrew evt) {
    var confirmation =
      componentClient
        .forEventSourcedEntity(evt.txId())
        .call(Transaction::registerWithdraw)
        .params(new Transaction.RegisterWithdraw(evt.walletId(), evt.eventId(), evt.amount()))
        .execute();
    return effects().asyncReply(confirmation);
  }


  @Subscribe.EventSourcedEntity(value = Transaction.class)
  public Effect<Done> onEvent(Transaction.TransactionUsageConfirmed evt) {
    return effects().reply(new Done());
  }

  @Subscribe.EventSourcedEntity(value = Transaction.class)
  public Effect<Done> onEvent(Transaction.DuplicateWithdrawDetected evt) {
    var txId =
    componentClient
      .forEventSourcedEntity(UUID.randomUUID().toString())
      .call(Transaction::createId)
      .execute();

    var res =
    txId.thenCompose( id ->
      componentClient.forEventSourcedEntity(evt.walletId())
        .call(Wallet::deposit)
        .params(new Wallet.Deposit(evt.amount(), id.value()))
        .execute()
      );

    return effects().asyncReply(res);
  }

  @Subscribe.EventSourcedEntity(value = Transaction.class)
  public Effect<Done> onEvent(Transaction.DuplicateDepositDetected evt) {
    var txId =
      componentClient
        .forEventSourcedEntity(UUID.randomUUID().toString())
        .call(Transaction::createId)
        .execute();

    var res =
      txId.thenCompose( id ->
        componentClient.forEventSourcedEntity(evt.walletId())
          .call(Wallet::withdraw)
          .params(new Wallet.Withdraw(evt.amount(), id.value()))
          .execute()
      );

    return effects().asyncReply(res);
  }

  @Subscribe.EventSourcedEntity(value = Transaction.class)
  public Effect<Done> onEvent(Transaction.TransactionCreated evt) {
    return effects().reply(new Done());
  }
}
