package kalix.demo.payment;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.collection.List;
import kalix.demo.Done;
import kalix.demo.payment.Wallet.Event.*;
import kalix.javasdk.StatusCode;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;


@TypeId("wallet")
@Id("walletId")
@RequestMapping("/wallet-entities/{walletId}")
public class Wallet extends EventSourcedEntity<Wallet.State, Wallet.Event> {

  private static Logger logger = LoggerFactory.getLogger(Wallet.class);


  private String walletId;

  public Wallet(EventSourcedEntityContext context) {
    this.walletId = context.entityId();
  }


  public record State(Double balance, Double reserved, Map<String, PendingTransaction> pendingTransactions,
                      List<String> executedTransactions) {

    State increaseBalance(Double amount) {
      return new State(balance + amount, reserved, pendingTransactions, executedTransactions);
    }

    State decreaseBalance(Double amount) {
      return new State(balance - amount, reserved, pendingTransactions, executedTransactions);
    }

    State reserve(Double amount) {
      logger.info("Reserving funds '{}'", amount);
      return new State(balance - amount, reserved + amount, pendingTransactions, executedTransactions);
    }

    State unReserve(Double amount) {
      logger.info("Un-reserving funds '{}'", amount);
      return new State(balance + amount, reserved - amount, pendingTransactions, executedTransactions);
    }

    State execute(String transactionId) {
      return pendingTransactions
        .get(transactionId)
        .map(cmd -> {
          if (cmd.isWithdraw()) {
            return removePendingTransaction(cmd)
              .addExecuted(cmd)
              .decreaseBalance(cmd.amount());
          } else {
            return removePendingTransaction(cmd)
              .addExecuted(cmd)
              .increaseBalance(cmd.amount());
          }
        })
        .getOrElse(this);
    }

    State cancelTransaction(String transactionId) {
      return pendingTransactions
        .get(transactionId)
        .map(this::removePendingTransaction)
        .getOrElse(this);
    }

    State completeTransaction(String transactionId) {
      return new State(balance, reserved, pendingTransactions, executedTransactions.remove(transactionId));
    }

    PendingTransaction getTransaction(String transactionId) {
      return pendingTransactions.get(transactionId).get();
    }

    boolean isPendingTransaction(String transactionId) {
      return pendingTransactions.containsKey(transactionId);
    }

    boolean alreadySeen(String transactionId) {
      return pendingTransactions.containsKey(transactionId) || executedTransactions.contains(transactionId);
    }

    State addPendingTransaction(PendingTransaction cmd) {
      var newState = new State(balance, reserved, pendingTransactions.put(cmd.transactionId(), cmd), executedTransactions);
      if (cmd.isWithdraw()) {
        return newState.reserve(cmd.amount());
      }
      return newState;
    }


    State removePendingTransaction(PendingTransaction pending) {
      return pendingTransactions
        .get(pending.transactionId())
        .map(cmd -> {
          var newState = new State(balance, reserved, pendingTransactions.remove(cmd.transactionId()), executedTransactions);
          if (cmd.isWithdraw()) {
            return newState.unReserve(cmd.amount());
          } else {
            return newState;
          }
        })
        .getOrElse(this);
    }

    State addExecuted(PendingTransaction pending) {
      return new State(balance, reserved, pendingTransactions, executedTransactions.append(pending.transactionId));
    }

    public boolean hasBalance(Double amount) {
      return balance - amount >= 0;
    }
  }

  public record WalletStatus(Double balance, Double reservedFunds,
                             java.util.List<PendingTransaction> pendingTransactions) {
    static WalletStatus of(State state) {
      return new WalletStatus(state.balance, state.reserved, state.pendingTransactions.values().asJava());
    }
  }

  enum TransactionType {
    DEPOSIT,
    WITHDRAW
  }

  record PendingTransaction(Double amount, String transactionId, TransactionType transactionType) {
    boolean isWithdraw() {
      return transactionType == TransactionType.WITHDRAW;
    }

    boolean isDeposit() {
      return !isWithdraw();
    }
  }


  sealed interface Event {

    record Created() implements Event {
    }

    record DepositInitiated(Double amount, String transactionId, String walletId) implements Event {
    }

    record WithdrawInitiated(Double amount, String transactionId, String walletId) implements Event {
    }

    record BalanceIncreased(Double amount, String transactionId, String walletId) implements Event {
    }

    record BalanceDecreased(Double amount, String transactionId, String walletId) implements Event {
    }


    record TransactionCancelled(String transactionId) implements Event {
    }

    record TransactionCompleted(String transactionId) implements Event {
    }
  }

  public record Deposit(Double amount, String transactionId) {
  }

  public record Withdraw(Double amount, String transactionId) {
  }

  @GetMapping
  public Effect<WalletStatus> getStatus() {
    if (currentState() == null)
      return notFound();
    else
      return effects().reply(WalletStatus.of(currentState()));
  }


  @PostMapping()
  public Effect<Done> create() {
    if (currentState() == null)
      return effects().emitEvent(new Created()).thenReply(__ -> new Done());
    else
      return effects().error("Wallet '" + walletId + "' exists already");
  }


  @PostMapping("/deposit")
  public Effect<WalletStatus> deposit(@RequestBody Deposit cmd) {
    if (currentState() == null) {
      return notFound();
    } else if (currentState().alreadySeen(cmd.transactionId)) {
      return effects().reply(WalletStatus.of(currentState()));

    } else {
      logger.info("Deposit requested  on '{}': amount '{}', transaction '{}'", walletId, cmd.amount, cmd.transactionId);
      return effects()
        .emitEvent(new DepositInitiated(cmd.amount, cmd.transactionId, this.walletId))
        .thenReply(WalletStatus::of);
    }
  }

  private <T> Effect<T> notFound() {
    return effects().error("Wallet doesn't exist", StatusCode.ErrorCode.NOT_FOUND);
  }


  @PostMapping("/withdraw")
  public Effect<WalletStatus> withdraw(@RequestBody Withdraw cmd) {
    if (currentState() == null) {
      return notFound();
    } else if (currentState().alreadySeen(cmd.transactionId)) {
      return effects().reply(WalletStatus.of(currentState()));

    } else if (currentState().hasBalance(cmd.amount)) {
      logger.info("Withdraw requested  on '{}': amount '{}, transaction '{}''", walletId, cmd.amount, cmd.transactionId);
      return effects()
        .emitEvent(new WithdrawInitiated(cmd.amount, cmd.transactionId, this.walletId))
        .thenReply(WalletStatus::of);

    } else {
      logger.info("Insufficient balance in '{}' to withdraw amount '{}'", this.walletId, cmd.amount);
      return effects().error("Insufficient balance");
    }
  }


  @PostMapping("/execute/{transactionId}")
  public Effect<Done> execute(@PathVariable String transactionId) {
    if (currentState() == null) {
      return notFound();
    } else if (currentState().isPendingTransaction(transactionId)) {

      var cmd = currentState().getTransaction(transactionId);
      if (cmd.isDeposit()) {
        logger.info("Deposit executed  on '{}': transaction '{}', amount '{}'", walletId, cmd.transactionId(), cmd.amount());
        return effects()
          .emitEvent(new BalanceIncreased(cmd.amount(), cmd.transactionId(), this.walletId))
          .thenReply(__ -> new Done());

      } else {
        logger.info("Withdraw executed  on '{}': transaction '{}', amount '{}'", walletId, cmd.transactionId(), cmd.amount());
        return effects()
          .emitEvent(new BalanceDecreased(cmd.amount(), cmd.transactionId(), this.walletId))
          .thenReply(__ -> new Done());
      }

    }
    return effects().reply(new Done());
  }


  @PostMapping("/transaction/cancel/{transactionId}")
  public Effect<Done> cancel(@PathVariable String transactionId) {
    if (currentState() == null) {
      return notFound();
    } else if (currentState().isPendingTransaction(transactionId)) {
      logger.info("Transaction cancelled '{}' on '{}'", currentState().getTransaction(transactionId), walletId);
      return effects()
        .emitEvent(new TransactionCancelled(transactionId))
        .thenReply(__ -> new Done());
    } else {
      return effects().reply(new Done());
    }
  }

  @PostMapping("/transaction/complete/{transactionId}")
  public Effect<Done> complete(@PathVariable String transactionId) {
    logger.info("Transaction completed '{}' on '{}'", transactionId, walletId);
    return effects()
      .emitEvent(new TransactionCompleted(transactionId))
      .thenReply(__ -> new Done());
  }

  @EventHandler
  public State onEvent(Created evt) {
    return new State(0.0, 0.0, HashMap.empty(), List.empty());
  }

  @EventHandler
  public State onEvent(DepositInitiated evt) {
    return currentState()
      .addPendingTransaction(new PendingTransaction(evt.amount, evt.transactionId, TransactionType.DEPOSIT));
  }

  @EventHandler
  public State onEvent(WithdrawInitiated evt) {
    return currentState()
      .addPendingTransaction(new PendingTransaction(evt.amount, evt.transactionId, TransactionType.WITHDRAW));
  }

  @EventHandler
  public State onEvent(BalanceIncreased evt) {
    return currentState().execute(evt.transactionId);
  }

  @EventHandler
  public State onEvent(BalanceDecreased evt) {
    return currentState().execute(evt.transactionId);
  }

  @EventHandler
  public State onEvent(TransactionCancelled evt) {
    return currentState().cancelTransaction(evt.transactionId);
  }


  @EventHandler
  public State onEvent(TransactionCompleted evt) {
    return currentState().completeTransaction(evt.transactionId);
  }


}
