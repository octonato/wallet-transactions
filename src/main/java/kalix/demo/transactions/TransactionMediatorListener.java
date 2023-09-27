package kalix.demo.transactions;

import io.vavr.collection.List;
import kalix.demo.Done;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Subscribe.EventSourcedEntity(value = TransactionMediator.class, ignoreUnknown = true)
public class TransactionMediatorListener extends Action {

  final private ComponentClient componentClient;
  final private TransactionAdapterProvider adapterProvider;

  final private Logger logger = LoggerFactory.getLogger(getClass());
  final private CompletionStage<Done> doneCompletionStage = CompletableFuture.completedStage(new Done());

  public TransactionMediatorListener(@Autowired ComponentClient componentClient,
                                     @Autowired TransactionAdapterProvider adapterProvider) {
    this.componentClient = componentClient;
    this.adapterProvider = adapterProvider;
  }


  final public Effect<Done> onEvent(TransactionMediator.Event.Initialized evt) {

    logger.info(evt.toString());

    // if a single call fails, event processing fails and needs to be retried
    var allExecuted =
      List
        .ofAll(evt.participants())
        .map(participant ->
          adapterProvider
            .forType(participant.type()).get()
            .initialized(componentClient, participant.id(), evt.transactionId())
        ).foldLeft(
          doneCompletionStage,
          (agg, fut) -> agg.thenCompose(__ -> fut)
        );


    return effects().asyncReply(allExecuted);
  }

  final public Effect<Done> onEvent(TransactionMediator.Event.Cancelled evt) {
    logger.info(evt.toString());
    // if a single call fails, event processing fails and needs to be retried
    var allExecuted =
      List
        .ofAll(evt.participants())
        .map(participant ->
          adapterProvider
            .forType(participant.type()).get()
            .cancel(componentClient, participant.id(), evt.transactionId())
        ).foldLeft(
          doneCompletionStage,
          (agg, fut) -> agg.thenCompose(__ -> fut)
        );


    return effects().asyncReply(allExecuted);
  }

  final public Effect<Done> onEvent(TransactionMediator.Event.Completed evt) {
    logger.info(evt.toString());
    // if a single call fails, event processing fails and needs to be retried
    var allExecuted =
      List
        .ofAll(evt.participants())
        .map(participant ->
          adapterProvider
            .forType(participant.type()).get()
            .complete(componentClient, participant.id(), evt.transactionId())
        ).foldLeft(
          doneCompletionStage,
          (agg, fut) -> agg.thenCompose(__ -> fut)
        );


    return effects().asyncReply(allExecuted);
  }
}
