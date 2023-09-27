package kalix.demo.transactions;

import io.vavr.control.Option;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import  io.vavr.collection.List;

@Component
public class TransactionAdapterProvider {

  final private ApplicationContext appContext;

  public TransactionAdapterProvider(@Autowired ApplicationContext appContext) {
    this.appContext = appContext;
  }

  public TransactionAdapter forName(String name) {
    return appContext.getBean(name, TransactionAdapter.class);
  }

  public Option<TransactionAdapter> forType(Class<?> clazz) {
    var allAdapters = List.ofAll(appContext.getBeansOfType(TransactionAdapter.class).values());
    return allAdapters.find(adapter -> adapter.adapterFor(clazz));
  }
}
