package kalix.demo.domain;

import kalix.demo.Done;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@TypeId("transaction")
@Id("id")
@RequestMapping("/transactions/{id}")
public class Transaction extends EventSourcedEntity<Transaction.State, Transaction.Event> {

  private Logger logger = LoggerFactory.getLogger(Transaction.class);

  public record Id(String value) {
  }

  public record State(String id, Optional<String> eventId, List<String> duplicates) {

    State addEventId(String eventId) {
      if (this.eventId.isEmpty()) {
        return new State(id, Optional.of(eventId), List.of());
      } else {
        var newDuplicatesList = new ArrayList<>(duplicates);
        newDuplicatesList.add(eventId);
        return new State(id, this.eventId, newDuplicatesList);
      }
    }
  }

  public record RegisterDeposit(String walletId, String eventId, Double amount) {
  }

  public record RegisterWithdraw(String walletId, String eventId, Double amount) {
  }

  interface Event {
  }

  public record TransactionCreated(String id) implements Event {
  }

  public record TransactionUsageConfirmed(String id, String eventId) implements Event {
  }

  public record DuplicateWithdrawDetected(String id, String walletId, String eventId, Double amount) implements Event {
  }

  public record DuplicateDepositDetected(String id, String walletId, String eventId, Double amount) implements Event {
  }

  @Override
  public State emptyState() {
    return null;
  }

  @PostMapping
  public Effect<Id> createId() {
    if (currentState() == null) {
      logger.info("Creating transaction: {}", commandContext().entityId());
      return effects()
        .emitEvent(new TransactionCreated(commandContext().entityId()))
        .thenReply(st -> new Id(st.id));
    } else {
      logger.info("Transaction already created: {}", commandContext().entityId());
      return effects().reply(new Id(currentState().id));
    }
  }

  private Optional<Effect<Done>> register(@PathVariable String walletId, @PathVariable String eventId, Double amount) {
     if (currentState().eventId.isEmpty()) {
      logger.info("Registering transaction {} {} {}", commandContext().entityId(), walletId, eventId);
      return Optional.of(effects()
        .emitEvent(new TransactionUsageConfirmed(commandContext().entityId(), eventId))
        .thenReply(st -> new Done()));

    } else if (currentState().eventId.get().equals(eventId)) {
      logger.info("Transaction usage already registered {} {} {}", commandContext().entityId(), walletId, eventId);
      return Optional.of(effects().reply(new Done()));

    } else if (currentState().duplicates.contains(eventId)) {
      logger.info("Duplicate transaction usage already detected {} {} {}", commandContext().entityId(), walletId, eventId);
      return Optional.of(effects().reply(new Done()));
    } else {
      return Optional.empty();
    }
  }

  @PostMapping("/deposit")
  public Effect<Done> registerDeposit(@RequestBody RegisterDeposit cmd) {
    return register(cmd.walletId, cmd.eventId, cmd.amount).orElseGet(() -> {
        logger.info("Duplicated deposit detected: id {}, wallet {}", commandContext().entityId(), cmd.walletId);
        return effects()
          .emitEvent(new DuplicateDepositDetected(commandContext().entityId(), cmd.walletId, cmd.eventId, cmd.amount))
          .thenReply(st -> new Done());
      }
    );
  }

  @PostMapping("/withdraw")
  public Effect<Done> registerWithdraw(@RequestBody RegisterWithdraw cmd) {
    return register(cmd.walletId, cmd.eventId, cmd.amount).orElseGet(() -> {
        logger.info("Duplicated withdraw detected: id {}, wallet {}", commandContext().entityId(), cmd.walletId);
        return effects()
          .emitEvent(new DuplicateWithdrawDetected(commandContext().entityId(), cmd.walletId, cmd.eventId, cmd.amount))
          .thenReply(st -> new Done());
      }
    );
  }


  @EventHandler
  public State onEvent(TransactionCreated evt) {
    return new State(evt.id, Optional.empty(), List.of());
  }

  @EventHandler
  public State onEvent(TransactionUsageConfirmed evt) {
    return currentState().addEventId(evt.eventId);
  }

  @EventHandler
  public State onEvent(DuplicateDepositDetected evt) {
    return currentState().addEventId(evt.id);
  }

  @EventHandler
  public State onEvent(DuplicateWithdrawDetected evt) {
    return currentState().addEventId(evt.id);
  }
}
