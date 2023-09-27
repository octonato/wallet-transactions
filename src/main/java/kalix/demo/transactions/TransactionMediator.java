package kalix.demo.transactions;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import kalix.demo.Done;
import kalix.javasdk.StatusCode;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;


@TypeId("transaction-mediator")
@Id("id")
@RequestMapping("/transactions/{id}")
public class TransactionMediator
  extends EventSourcedEntity<TransactionMediator.State, TransactionMediator.Event> {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private final String transactionId;

  public TransactionMediator(EventSourcedEntityContext context) {
    this.transactionId = context.entityId();
  }

  public record Participant(String id, Class<?> type, boolean joined, boolean executed) {

    public static Participant of(String id, Class<?> type) {
      return new Participant(id, type, false, false);
    }


    Participant asJoined() {
      return new Participant(id, type, true, executed);
    }


    Participant asExecuted() {
      return new Participant(id, type, joined, true);
    }
  }

  enum Status {
    WAITING,
    INITIATED,
    CANCELLED,
    COMPLETED
  }

  public record State(String transactionId, Map<String, Participant> participants, Status status) {

    State cancel() {
      return new State(transactionId, participants, Status.CANCELLED);
    }

    State complete() {
      return new State(transactionId, participants, Status.COMPLETED);
    }

    boolean isCancelled() {
      return status == Status.CANCELLED;
    }

    boolean allJoined() {
      return participants.forAll(p -> p._2.joined);
    }

    static State newInstance(String transactionId, java.util.List<Participant> participants) {
      return List.ofAll(participants).foldLeft(
        new State(transactionId, HashMap.empty(), Status.WAITING),
        (st, el) -> new State(transactionId, st.participants.put(el.id, el), st.status));
    }

    boolean isLastToJoin(String participantId) {
      return participants
        .remove(participantId)
        .forAll(p -> p._2.joined);
    }

    boolean isLastToExecute(String participantId) {
      return participants
        .remove(participantId)
        .forAll(p -> p._2.executed);
    }

    State participantJoined(String participantId) {
      var updatedParticipants =
        participants
          .replaceAll((id, participant) -> id.equals(participantId) ? participant.asJoined() : participant);
      return new State(transactionId, updatedParticipants, status);
    }

    State participantExecuted(String participantId) {
      var updatedParticipants =
        participants
          .replaceAll((id, participant) -> id.equals(participantId) ? participant.asExecuted() : participant);
      return new State(transactionId, updatedParticipants, status);
    }

    public boolean hasJoined(String participantId) {
      return participants.get(participantId).exists(p -> p.joined);
    }

    public boolean hasExecuted(String participantId) {
      return participants.get(participantId).exists(p -> p.executed);
    }
  }


  public record Create(java.util.List<Participant> participants) {
  }

  sealed interface Event {

    record Created(String transactionId, java.util.List<Participant> participants) implements Event {
    }

    record ParticipantJoined(String transactionId, String participantId) implements Event {
    }

    record Initialized(String transactionId, java.util.List<Participant> participants) implements Event {
    }

    record ParticipantExecuted(String transactionId, Participant participant) implements Event {
    }

    record Completed(String transactionId, java.util.List<Participant> participants) implements Event {
    }

    record Cancelled(String transactionId, java.util.List<Participant> participants) implements Event {
    }
  }

  public record TransactionStatus(String transactionId, java.util.List<Participant> participants, Status status) {
    static TransactionStatus of(State state) {
      return new TransactionStatus(state.transactionId, state.participants.values().asJava(), state.status);
    }
  }

  @GetMapping
  public Effect<TransactionStatus> getStatus() {
    return effects().reply(TransactionStatus.of(currentState()));
  }

  @PostMapping
  public Effect<TransactionStatus> create(@RequestBody Create cmd) {
    if (currentState() == null) {
      logger.info("Creating transaction: '{}' for '{}'", transactionId, cmd);
      return effects()
        .emitEvent(new Event.Created(transactionId, cmd.participants))
        .thenReply(TransactionStatus::of);

    } else if (currentState().isCancelled()) {
      logger.info("Cancelled transaction: '{}'", transactionId);
      return effects().error("Transaction exists but was cancelled");

    } else if (currentState().allJoined()) {
      return effects().error("Transaction already in progress");

    } else {
      // TODO: check if existing transaction contain same participants
      logger.info("Transaction already created: '{}'", transactionId);
      return effects().reply(TransactionStatus.of(currentState()));
    }
  }

  @EventHandler
  public State onEvent(Event.Created evt) {
    return State.newInstance(transactionId, evt.participants);
  }

  @PostMapping("/{participantId}/join")
  public Effect<Done> join(@PathVariable String participantId) {

    logger.info("Participant '{}' requested to join transaction '{}'", participantId, transactionId);

    if (currentState().isCancelled()) {
      // needs to ignore the join as it can trigger an Initialized event
      // ultimately, the entity joining it will receive a cancel commands as well
      logger.info("Joining after cancelling: transaction '{}', participant {}", transactionId, participantId);
      return effects().reply(new Done());

    } else if (currentState().participants.containsKey(participantId)) {

      if (currentState().hasJoined(participantId)) {
        logger.info("Participant already joined: transaction '{}', participant {}", transactionId, participantId);
        // just ignore if already joined
        return effects().reply(new Done());

      } else {

        var joinedEvent = new Event.ParticipantJoined(transactionId, participantId);
        logger.info("Participant joined: transaction '{}', participant {}", transactionId, participantId);
        // if last to join, we should also mark the transaction as completed
        if (currentState().isLastToJoin(participantId)) {
          logger.info("All participants joined: transaction '{}'", transactionId);

          var allParticipants = currentState().participants.values().toJavaList();
          var completedEvent = new Event.Initialized(transactionId, allParticipants);

          return effects()
            .emitEvents(java.util.List.of(joinedEvent, completedEvent))
            .thenReply(__ -> new Done());

        } else {
          return effects()
            .emitEvent(joinedEvent)
            .thenReply(__ -> new Done());
        }

      }
    } else {
      // joined by unknown should be ignored to not block the projection
      return effects().reply(new Done());
    }
  }

  @EventHandler
  public State onEvent(Event.ParticipantJoined evt) {
    return currentState().participantJoined(evt.participantId);
  }

  @EventHandler
  public State onEvent(Event.Initialized evt) {
    return currentState();
  }


  @PostMapping("/{participantId}/executed")
  public Effect<Done> executed(@PathVariable String participantId) {

    if (currentState().isCancelled()) {
      logger.info("Cancelled transaction '{}' was executed by participant '{}'. THIS IS A BUG!", transactionId, participantId);
      return effects().reply(new Done());

    } else if (currentState().participants.containsKey(participantId)) {

      var participant = currentState().participants.get(participantId).get();
      if (currentState().hasExecuted(participantId)) {
        logger.info("Participant '{}' already executed transaction '{}'", participantId, transactionId);
        // just ignore if already joined
        return effects().reply(new Done());

      } else {

        logger.info("Participant '{}' executed transaction '{}'", participantId, transactionId);
        var executedEvent = new Event.ParticipantExecuted(transactionId, participant);
        // if last to join, we should also mark the transaction as completed
        if (currentState().isLastToExecute(participantId)) {
          logger.info("All participants executed: transaction '{}'", transactionId);

          var allParticipants = currentState().participants.values().toJavaList();
          var completedEvent = new Event.Completed(transactionId, allParticipants);

          return effects()
            .emitEvents(java.util.List.of(executedEvent, completedEvent))
            .thenReply(__ -> new Done());

        } else {
          return effects()
            .emitEvent(executedEvent)
            .thenReply(__ -> new Done());
        }

      }
    } else {
      // joined by unknown should be ignored to not block the projection
      return effects().reply(new Done());
    }
  }

  @EventHandler
  public State onEvent(Event.ParticipantExecuted evt) {
    return currentState().participantExecuted(evt.participant.id);
  }


  @EventHandler
  public State onEvent(Event.Completed evt) {
    return currentState().complete();
  }

  @PostMapping("/cancel")
  public Effect<Done> cancel() {
    if (currentState() == null) {
      return effects().error("Transaction doesn't exist: " + transactionId, StatusCode.ErrorCode.NOT_FOUND);

    } else if (currentState().allJoined()) {
      logger.info("Attempt to cancel an in-progress transaction: '{}'", transactionId);
      return effects().error("Transaction already in-progress");

    } else {
      var allParticipants = currentState().participants.values().toJavaList();
      return effects()
        .emitEvent(new Event.Cancelled(transactionId, allParticipants))
        .thenReply(__ -> new Done());
    }
  }

  @EventHandler
  public State onEvent(Event.Cancelled evt) {
    return currentState().cancel();
  }


}
