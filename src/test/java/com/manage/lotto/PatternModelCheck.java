package com.manage.lotto;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.ml.*;
import com.manage.lotto.repository.LottoHistoryRepository;
import com.manage.lotto.service.*;
import smile.classification.RandomForest;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.*;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.transaction.event.TransactionalApplicationListenerMethodAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

public class PatternModelCheck {
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static class CountingTrainer extends LottoPatternExperimentService {
        final AtomicInteger calls = new AtomicInteger();
        boolean fail;
        CountingTrainer(LottoHistoryRepository repo) { super(repo, new LottoFeatureExtractor(), new LottoPatternFeatures()); }
        @Override public RandomForest train(List<LottoHistory> histories, boolean pattern, long seed) {
            calls.incrementAndGet();
            if (fail) throw new IllegalStateException("simulated training failure");
            return super.train(histories, pattern, seed);
        }
    }
    static LottoHistory draw(int t, Random random) {
        List<Integer> balls = IntStream.rangeClosed(1, 45).boxed().collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(balls, random);
        return PatternExperimentCheck.draw(t, balls.subList(0,6));
    }
    public static void main(String[] args) throws Exception {
        List<LottoHistory> histories = new ArrayList<>(); Random random = new Random(123);
        for (int t=1;t<=405;t++) histories.add(draw(t,random));
        LottoHistoryRepository repo = (LottoHistoryRepository) Proxy.newProxyInstance(LottoHistoryRepository.class.getClassLoader(), new Class[]{LottoHistoryRepository.class},
                (proxy, method, arguments) -> { if(method.getName().equals("findAllByOrderByDrwNoAsc")) return List.copyOf(histories); throw new AssertionError("Unexpected bounded query: " + method.getName()); });
        CountingTrainer trainer = new CountingTrainer(repo);
        Path dir=Files.createTempDirectory("pattern-model-check-"); Path file=dir.resolve("model.bin");
        LottoPatternModelService service = new LottoPatternModelService(repo,trainer,file.toString());
        LottoPatternModelService restarted = null;
        try {
            service.refresh();
            check(trainer.calls.get()==1 && service.status().historyCount()==405,"all 405 draws used");
            check(service.predict().trainingSamples()==17325,"405 draw training samples");
            byte[] original=Files.readAllBytes(file);
            var prediction=service.predict(); service.predict(); service.refresh();
            check(trainer.calls.get()==1 && Arrays.equals(original,Files.readAllBytes(file)),"prediction/unchanged data never trains");
            restarted=new LottoPatternModelService(repo,trainer,file.toString()); restarted.load(); restarted.refresh();
            check(restarted.predict().equals(prediction) && trainer.calls.get()==1,"restart loads persisted forest");
            histories.add(draw(406,random));
            check(service.predict().staleModel() && service.predict().nextDrawNo()==407,"latest features with previous model");
            service.refresh();
            check(trainer.calls.get()==2 && service.status().historyCount()==406 && !service.predict().staleModel(),"new draw trains once");
            histories.set(0,draw(1,random)); service.refresh();
            check(trainer.calls.get()==3,"same-count same-latest number correction detected");
            byte[] saved=Files.readAllBytes(file); var old=service.status();
            trainer.fail=true; histories.add(draw(407,random));
            try { service.refresh(); throw new AssertionError("failure swallowed"); } catch(IllegalStateException expected) { }
            check(Arrays.equals(saved,Files.readAllBytes(file)) && service.status().trainedAt().equals(old.trainedAt()) && service.predict().staleModel(),"failure preserves file and usable previous model");
            trainer.fail=false;
            // Exercise the actual @TransactionalEventListener adapter: rollback must not enqueue.
            try (GenericApplicationContext context=new GenericApplicationContext()) {
                context.registerBean("models",LottoPatternModelService.class,()->service); context.refresh();
                var method=LottoPatternModelService.class.getMethod("onHistoryChanged",LottoHistoryChanged.class);
                var listener=new TransactionalApplicationListenerMethodAdapter("models",LottoPatternModelService.class,method) {
                    @Override protected Object getTargetBean() { return service; }
                };
                int before=trainer.calls.get();
                TransactionSynchronizationManager.setActualTransactionActive(true);
                TransactionSynchronizationManager.initSynchronization();
                listener.onApplicationEvent(new PayloadApplicationEvent<>(context,new LottoHistoryChanged()));
                for(var sync:TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
                TransactionSynchronizationManager.clearSynchronization(); TransactionSynchronizationManager.setActualTransactionActive(false);
                check(trainer.calls.get()==before,"rollback never trains");
                TransactionSynchronizationManager.setActualTransactionActive(true);
                TransactionSynchronizationManager.initSynchronization();
                listener.onApplicationEvent(new PayloadApplicationEvent<>(context,new LottoHistoryChanged()));
                check(trainer.calls.get()==before,"no training before commit");
                for(var sync:TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
                TransactionSynchronizationManager.clearSynchronization(); TransactionSynchronizationManager.setActualTransactionActive(false);
                long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
                while(service.status().training() && System.nanoTime()<deadline) Thread.sleep(50);
                check(trainer.calls.get()==before+1 && service.status().historyCount()==407,"commit trains asynchronously");
            }
            System.out.println("PASS: all 405 draws, persisted forest reload, inference without training, unchanged reuse, new/corrected data, failure preservation, commit/rollback events");
        } finally {
            service.close(); if(restarted!=null)restarted.close();
            Files.deleteIfExists(file); Files.deleteIfExists(dir);
        }
    }
}
