package kalix.demo.payment;

import kalix.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class WalletTest {

  @Test
  public void testDeposit() {
    var wallet = EventSourcedTestKit.of(Wallet::new);
    wallet.call(Wallet::create);
    {
      var result = wallet.call(w -> w.deposit(new Wallet.Deposit(100.0, "foo")));
      result.getNextEventOfType(Wallet.Event.DepositInitiated.class);

      var status = result.getReply();
      assertEquals(0.0, status.balance());
      assertEquals(1, status.pendingTransactions().size());
    }

    {
      var result = wallet.call(w -> w.execute("foo"));
      result.getNextEventOfType(Wallet.Event.BalanceIncreased.class);

      var state = (Wallet.State) result.getUpdatedState();
      assertEquals(100.0, state.balance());
      assertEquals(0, state.pendingTransactions().size());
      assertEquals(1, state.executedTransactions().size());
    }

    {
      var result = wallet.call(w -> w.complete("foo"));
      result.getNextEventOfType(Wallet.Event.TransactionCompleted.class);

      var state = (Wallet.State) result.getUpdatedState();
      assertEquals(100.0, state.balance());
      assertEquals(0, state.pendingTransactions().size());
      assertEquals(0, state.executedTransactions().size());
    }
  }

  @Test
  public void testDoubleDepositBeforeExecution() {
    var wallet = EventSourcedTestKit.of(Wallet::new);
    wallet.call(Wallet::create);
    Wallet.Deposit foo = new Wallet.Deposit(100.0, "foo");
    {
      var result = wallet.call(w -> w.deposit(foo));
      result.getNextEventOfType(Wallet.Event.DepositInitiated.class);

      var status = result.getReply();
      assertEquals(0.0, status.balance());
      assertEquals(1, status.pendingTransactions().size());
    }

    {
      var result = wallet.call(w -> w.deposit(foo));
      assertFalse(result.didEmitEvents());

      var status = result.getReply();
      assertEquals(0.0, status.balance());
      assertEquals(1, status.pendingTransactions().size());
    }
  }

  @Test
  public void testDoubleDepositAfterExecution() {
    var wallet = EventSourcedTestKit.of(Wallet::new);
    wallet.call(Wallet::create);

    Wallet.Deposit foo = new Wallet.Deposit(100.0, "foo");
    {
      var result = wallet.call(w -> w.deposit(foo));
      result.getNextEventOfType(Wallet.Event.DepositInitiated.class);

      var status = result.getReply();
      assertEquals(0.0, status.balance());
      assertEquals(1, status.pendingTransactions().size());
    }

    {
      var result = wallet.call(w -> w.execute("foo"));
      result.getNextEventOfType(Wallet.Event.BalanceIncreased.class);

      var state = (Wallet.State) result.getUpdatedState();
      assertEquals(100.0, state.balance());
      assertEquals(0, state.pendingTransactions().size());
      assertEquals(1, state.executedTransactions().size());
    }

    {
      var result = wallet.call(w -> w.deposit(foo));
      assertFalse(result.didEmitEvents());

      var state = (Wallet.State) result.getUpdatedState();
      assertEquals(100.0, state.balance());
      assertEquals(0, state.pendingTransactions().size());
      assertEquals(1, state.executedTransactions().size());
    }
  }

  @Test
  public void testCancelledDeposit() {
    var wallet = EventSourcedTestKit.of(Wallet::new);
    wallet.call(Wallet::create);
    {
      var result = wallet.call(w -> w.deposit(new Wallet.Deposit(100.0, "foo")));
      result.getNextEventOfType(Wallet.Event.DepositInitiated.class);

      var status = result.getReply();
      assertEquals(0.0, status.balance());
      assertEquals(1, status.pendingTransactions().size());
    }

    {
      var result = wallet.call(w -> w.cancel("foo"));
      result.getNextEventOfType(Wallet.Event.TransactionCancelled.class);

      var state = (Wallet.State) result.getUpdatedState();
      assertEquals(0.0, state.balance());
      assertEquals(0, state.pendingTransactions().size());
      assertEquals(0, state.executedTransactions().size());
    }
  }
}
