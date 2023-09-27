package kalix.demo.payment;

import kalix.demo.Done;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.workflow.Workflow;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Id("transferId")
@TypeId("transfer")
@RequestMapping("/transfer/{transferId}")
public class TransferWorkflow extends Workflow<TransferWorkflow.TransferState> {


  public record TransferState(Transfer transfer){}

  public record Transfer(String transactionId, Double amount, String from, String to) {}

  @PutMapping
  public Effect<Done> startTransfer(@RequestBody Transfer transfer) {
    if (transfer.amount() <= 0) {
      return effects().error("transfer amount should be greater than zero");
    } else if (currentState() != null) {
      return effects().error("transfer already started");
    } else {

      TransferState initialState = new TransferState(transfer);
      Wallet.Withdraw withdrawInput = new Wallet.Withdraw(transfer.amount(), transfer.transactionId);

      return effects()
        .updateState(initialState)
        .transitionTo("withdraw", withdrawInput)
        .thenReply(new Done());
    }
  }

  @Override
  public WorkflowDef<TransferState> definition() {
    return null;
  }
}
