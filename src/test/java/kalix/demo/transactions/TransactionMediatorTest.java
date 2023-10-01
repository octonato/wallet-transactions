package kalix.demo.transactions;

import kalix.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


public class TransactionMediatorTest {

  record TestTarget(){}
  @Test
  void creationIsIdempotent() {

    var mediator = EventSourcedTestKit.of(TransactionMediator::new);
    var createCmd = new TransactionMediator.Create(List.of(TransactionMediator.Participant.of("foo", TestTarget.class)));

    {
      var result = mediator.call(m -> m.create(createCmd));
      result.getNextEventOfType(TransactionMediator.Event.Created.class);
      var status = result.getReply();
      assertEquals(1, status.participants().size());
    }

    {
      var result = mediator.call(m -> m.create(createCmd));
      assertFalse(result.didEmitEvents());
      var status = result.getReply();
      assertEquals(1, status.participants().size());
    }
  }

  @Test
  void transactionFullCycle() {

    var mediator = EventSourcedTestKit.of(TransactionMediator::new);
    var createCmd = new TransactionMediator.Create(List.of(
      TransactionMediator.Participant.of("foo", TestTarget.class),
      TransactionMediator.Participant.of("bar", TestTarget.class)));

    {
      var result = mediator.call(m -> m.create(createCmd));
      result.getNextEventOfType(TransactionMediator.Event.Created.class);
      var status = result.getReply();
      assertEquals(2, status.participants().size());
    }

    {
      var result = mediator.call(m -> m.join("foo"));
      result.getNextEventOfType(TransactionMediator.Event.ParticipantJoined.class);

      var state = (TransactionMediator.State) result.getUpdatedState();
      assertEquals(2, state.participants().size());

      var fooParticipant = state.participants().values().head();
      assertTrue(fooParticipant.joined());
      assertFalse(fooParticipant.executed());
    }

    {
      var result = mediator.call(m -> m.join("bar"));
      result.getNextEventOfType(TransactionMediator.Event.ParticipantJoined.class);
      result.getNextEventOfType(TransactionMediator.Event.Initialized.class);

      var state = (TransactionMediator.State) result.getUpdatedState();
      assertEquals(2, state.participants().size());

      var barParticipant = state.participants().values().tail().head();
      assertTrue(barParticipant.joined());
      assertFalse(barParticipant.executed());
    }

    {
      var result = mediator.call(m -> m.executed("foo"));
      result.getNextEventOfType(TransactionMediator.Event.ParticipantExecuted.class);

      var state = (TransactionMediator.State) result.getUpdatedState();
      assertEquals(2, state.participants().size());

      var fooParticipant = state.participants().values().head();
      assertTrue(fooParticipant.joined());
      assertTrue(fooParticipant.executed());
    }

    {
      var result = mediator.call(m -> m.executed("bar"));
      result.getNextEventOfType(TransactionMediator.Event.ParticipantExecuted.class);
      result.getNextEventOfType(TransactionMediator.Event.Completed.class);

      var state = (TransactionMediator.State) result.getUpdatedState();
      assertEquals(2, state.participants().size());

      var barParticipant = state.participants().values().tail().head();
      assertTrue(barParticipant.joined());
      assertTrue(barParticipant.executed());
    }

    { // fail if re-created after complete
      var result = mediator.call(m -> m.create(createCmd));
      assertFalse(result.didEmitEvents());
      assertTrue(result.isError());
      assertEquals("Transaction already in progress", result.getError());
    }
  }

  @Test
  void transactionCancelledCycle() {

    var mediator = EventSourcedTestKit.of(TransactionMediator::new);
    var createCmd = new TransactionMediator.Create(List.of(
      TransactionMediator.Participant.of("foo", TestTarget.class),
      TransactionMediator.Participant.of("bar", TestTarget.class)));

    {
      var result = mediator.call(m -> m.create(createCmd));
      result.getNextEventOfType(TransactionMediator.Event.Created.class);
      var status = result.getReply();
      assertEquals(2, status.participants().size());
    }

    {
      var result = mediator.call(m -> m.join("foo"));
      result.getNextEventOfType(TransactionMediator.Event.ParticipantJoined.class);

      var state = (TransactionMediator.State) result.getUpdatedState();
      assertEquals(2, state.participants().size());

      var fooParticipant = state.participants().values().head();
      assertTrue(fooParticipant.joined());
      assertFalse(fooParticipant.executed());
    }

    {
      var result = mediator.call(TransactionMediator::cancel);
      result.getNextEventOfType(TransactionMediator.Event.Cancelled.class);

      var state = (TransactionMediator.State) result.getUpdatedState();
      assertEquals(2, state.participants().size());

      var fooParticipant = state.participants().values().get(0);
      assertTrue(fooParticipant.joined());
      assertFalse(fooParticipant.executed());

      var barParticipant = state.participants().values().get(1);
      assertFalse(barParticipant.joined());
      assertFalse(barParticipant.executed());
    }

    { // fail if re-created after complete
      var result = mediator.call(m -> m.create(createCmd));
      assertFalse(result.didEmitEvents());
      assertTrue(result.isError());
      assertEquals("Transaction exists but was cancelled", result.getError());
    }
  }
}
