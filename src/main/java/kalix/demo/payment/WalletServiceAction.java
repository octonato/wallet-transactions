package kalix.demo.payment;

import kalix.demo.Done;
import kalix.demo.transactions.TransactionMediator;
import kalix.demo.transactions.TransactionMediator.Participant;
import kalix.javasdk.action.Action;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/wallets")
public class WalletServiceAction extends Action {

  final private Logger logger = LoggerFactory.getLogger(getClass());
  final private ComponentClient componentClient;

  public WalletServiceAction(@Autowired ComponentClient componentClient) {
    this.componentClient = componentClient;
  }


  @PostMapping("/{walletId}")
  public Effect<Done> create(@PathVariable String walletId) {
    var create =
      componentClient
        .forEventSourcedEntity(walletId)
        .call(Wallet::create);

    return effects().forward(create);
  }

  @PostMapping("/{walletId}/deposit")
  public Effect<Wallet.WalletStatus> deposit(@PathVariable String walletId, @RequestBody Wallet.Deposit cmd) {

    logger.info("deposit {}", cmd);
    var createTxCmd = new TransactionMediator.Create(List.of(Participant.of(walletId, Wallet.class)));

    var tx =
      componentClient
        .forEventSourcedEntity(cmd.transactionId())
        .call(TransactionMediator::create)
        .params(createTxCmd)
        .execute();

    var deposit =
      componentClient
        .forEventSourcedEntity(walletId)
        .call(Wallet::deposit)
        .params(cmd);

    var res = tx.thenCompose(done -> deposit.execute());

    return effects().asyncReply(res);
  }

  @PostMapping("/{walletId}/withdraw")
  public Effect<Wallet.WalletStatus> withdraw(@PathVariable String walletId, @RequestBody Wallet.Withdraw cmd) {

    var createTxCmd = new TransactionMediator.Create(List.of(Participant.of(walletId, Wallet.class)));
    var tx =
      componentClient
        .forEventSourcedEntity(cmd.transactionId())
        .call(TransactionMediator::create)
        .params(createTxCmd)
        .execute();

    var deposit =
      componentClient
        .forEventSourcedEntity(walletId)
        .call(Wallet::withdraw)
        .params(cmd);

    var res = tx.thenCompose(done -> deposit.execute());
    return effects().asyncReply(res);
  }

  @GetMapping("/{walletId}")
  public Effect<Wallet.WalletStatus> getStatus(@PathVariable String walletId) {
    var status =
      componentClient
        .forEventSourcedEntity(walletId)
        .call(Wallet::getStatus);

    return effects().forward(status);
  }

  @PostMapping("/transfer/{transactionId}")
  public Effect<TransactionMediator.TransactionStatus> transfer(@PathVariable String transactionId, @RequestBody Transfer cmd) {

    var createTxCmd = new TransactionMediator.Create(
      List.of(
        Participant.of(cmd.from(), Wallet.class),
        Participant.of(cmd.to(), Wallet.class)
      ));
    var tx =
      componentClient
        .forEventSourcedEntity(transactionId)
        .call(TransactionMediator::create)
        .params(createTxCmd)
        .execute();

    componentClient
      .forEventSourcedEntity(cmd.from())
      .call(Wallet::withdraw)
      .params(new Wallet.Withdraw(cmd.amount(), transactionId))
      .execute();

    componentClient
      .forEventSourcedEntity(cmd.to())
      .call(Wallet::deposit)
      .params(new Wallet.Deposit(cmd.amount(), transactionId))
      .execute();

    return effects().asyncReply(tx);
  }

}
