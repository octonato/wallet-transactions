package kalix.demo.domain;

import kalix.demo.Done;
import kalix.javasdk.StatusCode;
import kalix.javasdk.annotations.Acl;
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


  public record State(String id, String walletId, boolean confirmed) {
    State confirm() {
      return new State(id, walletId, true);
    }
  }

  interface Event {
  }

  public record TransactionCreated(String commandId, String walletId) implements Event {
  }

  public record TransactionConfirmed(String commandId, String walletId) implements Event {
  }

  public record TransactionAlreadyCreated(String commandId, String walletId) implements Event {
  }

  public record TransactionStatus(String id, String walletId, boolean closed) {

    static TransactionStatus of(State state) {
      return new TransactionStatus(state.id, state.walletId, state.confirmed);
    }
  }


  @Override
  public State emptyState() {
    return null;
  }

  @PostMapping("/{walletId}")
  public Effect<Done> createTransaction(@PathVariable String walletId) {
    if (currentState() == null) {
      logger.info("Creating transaction: '{}'", commandContext().entityId());
      return effects()
        .emitEvent(new TransactionCreated(commandContext().entityId(), walletId))
        .thenReply(st -> new Done());
    } else {
      logger.info("Transaction already created: '{}'", commandContext().entityId());
      return effects()
        .emitEvent(new TransactionAlreadyCreated(commandContext().entityId(), walletId))
        .thenReply(st -> new Done());
    }
  }

  @PostMapping("/confirm/{walletId}")
  public Effect<Done> confirm(@PathVariable String walletId) {
    logger.info("Closing transaction: '{}'", commandContext().entityId());
    return effects()
      .emitEvent(new TransactionConfirmed(commandContext().entityId(), walletId))
      .thenReply(st -> new Done());
  }

  @EventHandler
  public State onEvent(TransactionCreated evt) {
    return new State(evt.commandId, evt.walletId, false);
  }

  @EventHandler
  public State onEvent(TransactionConfirmed evt) {
    return currentState().confirm();
  }

  @EventHandler
  public State onEvent(TransactionAlreadyCreated evt) {
    return currentState();
  }

  @GetMapping
  public Effect<TransactionStatus> getStatus() {
    if (currentState() == null)
      return effects().error("Not available", StatusCode.ErrorCode.NOT_FOUND);
    else
      return effects().reply(TransactionStatus.of(currentState()));
  }
}
