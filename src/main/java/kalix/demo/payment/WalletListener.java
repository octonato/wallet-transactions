package kalix.demo.payment;

import kalix.demo.Done;
import kalix.demo.payment.Wallet.Event.*;
import kalix.demo.transactions.TransactionMediator;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Subscribe.EventSourcedEntity(value = Wallet.class, ignoreUnknown = true)
public class WalletListener extends Action {

  final private ComponentClient componentClient;

  final private Logger logger = LoggerFactory.getLogger(getClass());

  public WalletListener(@Autowired ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Done> onEvent(DepositInitiated evt) {
    logger.info(evt.toString());
    var call =
      componentClient
        .forEventSourcedEntity(evt.transactionId())
        .call(TransactionMediator::join)
        .params(evt.walletId());

    return effects().forward(call);
  }

  public Effect<Done> onEvent(BalanceIncreased evt) {
    logger.info(evt.toString());
    var call =
      componentClient
        .forEventSourcedEntity(evt.transactionId())
        .call(TransactionMediator::executed)
        .params(evt.walletId());

    return effects().forward(call);
  }


  public Effect<Done> onEvent(WithdrawInitiated evt) {
    logger.info(evt.toString());
    var call =
      componentClient
        .forEventSourcedEntity(evt.transactionId())
        .call(TransactionMediator::join)
        .params(evt.walletId());

    return effects().forward(call);
  }

  public Effect<Done> onEvent(BalanceDecreased evt) {
    logger.info(evt.toString());
    var call =
      componentClient
        .forEventSourcedEntity(evt.transactionId())
        .call(TransactionMediator::executed)
        .params(evt.walletId());

    return effects().forward(call);
  }

}