package kalix.demo.domain;

import kalix.demo.Done;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@TypeId("wallet")
@Id("walletId")
@RequestMapping("/wallets/{walletId}")
public class Wallet extends EventSourcedEntity<Wallet.State, Wallet.Event> {

  private static Logger logger = LoggerFactory.getLogger(Wallet.class);


  private String walletId;

  public Wallet(EventSourcedEntityContext context) {
    this.walletId = context.entityId();
  }


  public record State(Double balance, Double reserved, Map<String, PendingCommand> pendingCommands) {

    State increaseBalance(Double amount) {
      return new State(balance + amount, reserved, pendingCommands);
    }

    State decreaseBalance(Double amount) {
      return new State(balance - amount, reserved, pendingCommands);
    }

    State reserve(Double amount) {
      logger.info("Reserving funds '{}'", amount);
      return new State(balance - amount, reserved + amount, pendingCommands);
    }

    State unreserve(Double amount) {
      logger.info("Unreserving funds '{}'", amount);
      return new State(balance + amount, reserved - amount, pendingCommands);
    }

    State execute(String commandId) {
      var cmd = pendingCommands.remove(commandId);
      if (cmd instanceof PendingWithdraw) {
        return unreserve(cmd.amount()).decreaseBalance(cmd.amount());
      } else {
        return increaseBalance(cmd.amount());
      }
    }

    State cancel(String commandId) {
      var cmd = pendingCommands.remove(commandId);
      if (cmd instanceof PendingWithdraw) {
        return unreserve(cmd.amount());
      }
      return this;
    }

    PendingCommand getCommand(String commandId) {
      return pendingCommands.get(commandId);
    }

    boolean isPendingCommand(String commandId) {
      return pendingCommands.containsKey(commandId);
    }

    State addPendingCommand(PendingCommand cmd) {
      pendingCommands.put(cmd.commandId(), cmd);
      if (cmd instanceof PendingWithdraw) {
        return reserve(cmd.amount());
      }
      return this;
    }

    State removePendingCommand(String commandId) {
      pendingCommands.remove(commandId);
      return this;
    }

    public Set<String> pendingTransactions() {
      return pendingCommands.keySet();
    }

    public boolean hasBalance(Double amount) {
      return balance - amount >= 0;
    }
  }

  public record WalletStatus(Double balance, Double reservedFunds, Map<String, PendingCommand> pendingCommands) {
    static WalletStatus of(State state) {
      return new WalletStatus(state.balance, state.reserved, state.pendingCommands);
    }
  }

  interface Event {
  }

  interface PendingCommand {
    String commandId();

    Double amount();
  }

  public record PendingDeposit(Double amount, String commandId) implements PendingCommand {
  }

  public record PendingWithdraw(Double amount, String commandId) implements PendingCommand {
  }

  public record DepositInitiated(Double amount, String commandId, String walletId) implements Event {
  }

  public record WithdrawInitiated(Double amount, String commandId, String walletId) implements Event {
  }

  public record Deposited(Double amount, String commandId, String walletId) implements Event {
  }


  public record Withdrew(Double amount, String commandId, String walletId) implements Event {
  }


  public record Cancelled(String commandId) implements Event {
  }


  public record Deposit(Double amount, String commandId) {
  }

  public record Withdraw(Double amount, String commandId) {
  }

  public record Balance(Double amount) {
  }

  @Override
  public State emptyState() {
    return new State(0.0, 0.0, new HashMap<>());
  }

  @PostMapping("/deposit")
  public Effect<WalletStatus> deposit(@RequestBody Deposit cmd) {
    if (currentState().isPendingCommand(cmd.commandId)) {
      return effects().reply(WalletStatus.of(currentState()));

    } else {
      logger.info("Deposit requested: tx '{}', amount '{}'", cmd.commandId, cmd.amount);
      return effects()
        .emitEvent(new DepositInitiated(cmd.amount, cmd.commandId, this.walletId))
        .thenReply(WalletStatus::of);
    }
  }

  @EventHandler
  public State onEvent(DepositInitiated evt) {
    return currentState()
      .addPendingCommand(new PendingDeposit(evt.amount, evt.commandId));
  }


  @PostMapping("/withdraw")
  public Effect<WalletStatus> withdraw(@RequestBody Withdraw cmd) {
    if (currentState().isPendingCommand(cmd.commandId)) {
      return effects().reply(WalletStatus.of(currentState()));

    } else if (currentState().hasBalance(cmd.amount)) {
      logger.info("Withdraw requested: tx '{}', amount '{}'", cmd.commandId, cmd.amount);
      return effects()
        .emitEvent(new WithdrawInitiated(cmd.amount, cmd.commandId, this.walletId))
        .thenReply(WalletStatus::of);

    } else {
      logger.info("Insufficient balance in '{}' to withdraw amount '{}'", this.walletId, cmd.amount);
      return effects().error("Insufficient balance");
    }
  }

  @EventHandler
  public State onEvent(WithdrawInitiated evt) {
    return currentState()
      .addPendingCommand(new PendingWithdraw(evt.amount, evt.commandId));
  }

  @PostMapping("/execute/{commandId}")
  public Effect<Done> execute(@PathVariable String commandId) {
    if (currentState().isPendingCommand(commandId)) {

      var cmd = currentState().getCommand(commandId);
      if (cmd instanceof PendingDeposit) {
        logger.info("Deposit executed: tx '{}', amount '{}'", cmd.commandId(), cmd.amount());
        return effects()
          .emitEvent(new Deposited(cmd.amount(), cmd.commandId(), this.walletId))
          .thenReply(__ -> new Done());

      } else if (cmd instanceof PendingWithdraw) {
        logger.info("Withdraw executed: tx '{}', amount '{}'", cmd.commandId(), cmd.amount());
        return effects()
          .emitEvent(new Withdrew(cmd.amount(), cmd.commandId(), this.walletId))
          .thenReply(__ -> new Done());
      }

    }
    return effects().reply(new Done());
  }


  @EventHandler
  public State onEvent(Deposited evt) {
    return currentState().execute(evt.commandId);
  }

  @EventHandler
  public State onEvent(Withdrew evt) {
    return currentState().execute(evt.commandId);
  }


  @PostMapping("/cancel/{commandId}")
  public Effect<Done> cancel(@PathVariable String commandId) {
    logger.info("Cancelling pending command '{}'", currentState().getCommand(commandId));
    return effects()
      .emitEvent(new Cancelled(commandId))
      .thenReply(__ -> new Done());
  }

  @EventHandler
  public State onEvent(Cancelled evt) {
    return currentState().cancel(evt.commandId);
  }


  @GetMapping
  public Effect<WalletStatus> getStatus() {
    return effects().reply(WalletStatus.of(currentState()));
  }


  @GetMapping("/pending")
  public Effect<Set<String>> getPendingTransactions() {
    return effects().reply(currentState().pendingTransactions());
  }



}
