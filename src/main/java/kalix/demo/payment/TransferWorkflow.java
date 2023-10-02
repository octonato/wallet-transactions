package kalix.demo.payment;

import kalix.demo.Done;
import kalix.demo.transactions.TransactionMediator;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.workflow.Workflow;
import kalix.javasdk.workflow.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Id("transferId")
@TypeId("transfer")
@RequestMapping("/transfer/workflow/{transferId}")
public class TransferWorkflow extends Workflow<TransferWorkflow.State> {

  final private Logger logger = LoggerFactory.getLogger(getClass());
  final private ComponentClient componentClient;
  final private String transferId;

  public TransferWorkflow(@Autowired ComponentClient componentClient,
                          @Autowired WorkflowContext context) {
    this.componentClient = componentClient;
    this.transferId = context.workflowId();
  }

  enum Status {
    INITIATED,
    TRANSACTION_CREATED,
    WITHDRAW_REQUESTED,
    DEPOSIT_REQUESTED,
    CANCELLED,
    COMPLETED
  }

  public record State(Transfer transfer, Status status) {
    public State complete() {
      return new State(transfer, Status.COMPLETED);
    }

    public State transactionCreated() {
      return new State(transfer, Status.TRANSACTION_CREATED);
    }

    public State withdrawRequested() {
      return new State(transfer, Status.WITHDRAW_REQUESTED);
    }

    public State depositRequested() {
      return new State(transfer, Status.DEPOSIT_REQUESTED);
    }

    public State cancelled() {
      return new State(transfer, Status.CANCELLED);
    }
  }

  public record WorkflowStatus(Transfer transfer, Status status) {
    static WorkflowStatus of(State state) {
      return new WorkflowStatus(state.transfer, state.status);
    }
  }

  @GetMapping
  public Effect<WorkflowStatus> getStatus() {
    if (currentState() == null)
      return effects().error("", io.grpc.Status.Code.NOT_FOUND);
    else
      return effects().reply(WorkflowStatus.of(currentState()));
  }

  @PutMapping
  public Effect<Done> startTransfer(@RequestBody Transfer transfer) {
    if (transfer.amount() <= 0) {
      return effects().error("transfer amount should be greater than zero");
    } else if (currentState() != null) {
      return effects().error("transfer already started");
    } else {

      State initialState = new State(transfer, Status.INITIATED);

      return effects()
        .updateState(initialState)
        .transitionTo(
          "create-transaction",
          new TransactionMediator.Create(
            List.of(
              TransactionMediator.Participant.of(transfer.from(), Wallet.class),
              TransactionMediator.Participant.of(transfer.to(), Wallet.class)
            )
          )
        )
        .thenReply(new Done());
    }
  }

  @PostMapping("/complete")
  public Effect<Done> complete() {
    if (currentState() == null) {
      return effects().reply(new Done());
    } else {
      return effects()
        .updateState(currentState().complete())
        .end()
        .thenReply(new Done());
    }
  }


  @Override
  public WorkflowDef<State> definition() {

    var createTransaction =
      step("create-transaction")
        .call(
          TransactionMediator.Create.class,
          cmd -> componentClient
            .forEventSourcedEntity(transferId)
            .call(TransactionMediator::create)
            .params(cmd)
        )
        .andThen(TransactionMediator.TransactionStatus.class, res -> {
            Wallet.Withdraw withdrawInput = new Wallet.Withdraw(currentState().transfer().amount(), transferId);
            return effects()
              .updateState(currentState().transactionCreated())
              .transitionTo("withdraw", withdrawInput);
          });

    var withdraw =
      step("withdraw")
        .call(
          Wallet.Withdraw.class,
          cmd -> componentClient
            .forEventSourcedEntity(currentState().transfer.from())
            .call(Wallet::withdraw)
            .params(cmd)
        )
        .andThen(Wallet.WalletStatus.class, res -> {
            Wallet.Deposit depositInput = new Wallet.Deposit(currentState().transfer().amount(), transferId);
            return effects()
              .updateState(currentState().withdrawRequested())
              .transitionTo("deposit", depositInput);
          });

    var deposit =
      step("deposit")
        .call(
          Wallet.Deposit.class,
          cmd -> componentClient
            .forEventSourcedEntity(currentState().transfer.to())
            .call(Wallet::deposit)
            .params(cmd)
        )
        .andThen(Wallet.WalletStatus.class, __ ->
          effects()
            .updateState(currentState().depositRequested())
            .end());

    var cancel =
      step("cancel")
        .call(() -> componentClient
          .forEventSourcedEntity(transferId)
          .call(TransactionMediator::cancel)
        )
        .andThen(Done.class, res ->
          effects()
            .updateState(currentState().cancelled())
            .end());

    return workflow()
      .addStep(createTransaction)
      .addStep(withdraw)
      .addStep(deposit)
      .addStep(cancel)
      .defaultStepRecoverStrategy(
        RecoverStrategy.maxRetries(5).failoverTo("cancel")
      );
  }
}
