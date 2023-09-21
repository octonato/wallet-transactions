package kalix.demo.domain;

import kalix.demo.Done;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@TypeId("wallet")
@Id("walletId")
@RequestMapping("/wallets/{walletId}")
public class Wallet extends EventSourcedEntity<Wallet.State, Wallet.Event> {

  private Logger logger = LoggerFactory.getLogger(Wallet.class);

  public record State(Double balance) {
  }

  interface Event {
  }

  public record Deposited(Double amount, String txId, String eventIt, String walletId) implements Event {
  }

  public record Withdrew(Double amount, String txId, String eventId, String walletId) implements Event {
  }

  public record Deposit(Double amount, String txId) {
  }

  public record Withdraw(Double amount, String txId) {
  }

  public record Balance(Double amount) {
  }

  @Override
  public State emptyState() {
    return new State(0.0);
  }

  @PostMapping("/deposit")
  public Effect<Done> deposit(@RequestBody Deposit cmd) {
    var eventId = UUID.randomUUID().toString();
    logger.info("Deposit: tx {}, amount {}, eventId {}", cmd.txId, cmd.amount, eventId);
    return effects()
      .emitEvent(new Deposited(cmd.amount, cmd.txId, eventId, commandContext().entityId()))
      .thenReply(__ -> new Done());
  }

  @PostMapping("/withdraw")
  public Effect<Done> withdraw(@RequestBody Withdraw cmd) {
    var eventId = UUID.randomUUID().toString();

    if (currentState().balance - cmd.amount < 0)
      return effects().error("No sufficient balance");

    logger.info("Withdraw: tx {}, amount {}, eventId {}", cmd.txId, cmd.amount, eventId);
    return effects()
      .emitEvent(new Withdrew(cmd.amount, cmd.txId, eventId, commandContext().entityId()))
      .thenReply(__ -> new Done());
  }

  @GetMapping
  public Effect<Balance> getBalance() {
    return effects().reply(new Balance(currentState().balance));
  }

  @EventHandler
  public State onEvent(Deposited evt) {
    return new State(currentState().balance + evt.amount);
  }

  @EventHandler
  public State onEvent(Withdrew evt) {
    return new State(currentState().balance - evt.amount);
  }
}
