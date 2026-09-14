package com.manage.lotto;

import com.manage.lotto.dto.*;
import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.repository.LottoHistoryRepository;
import com.manage.lotto.service.*;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.*;

public class LottoRegistrationCheck {
    static LottoHistoryCreateRequest request(List<Integer> n, Integer bonus) {
        return new LottoHistoryCreateRequest(1241, LocalDate.of(2026,9,19), n, bonus, null, null, null);
    }
    static void invalid(LottoHistoryCreateRequest r) {
        try { r.validate(); throw new AssertionError("invalid request accepted"); }
        catch(ResponseStatusException expected) { if(expected.getStatusCode().value()!=400)throw new AssertionError(); }
    }
    public static void main(String[] args) {
        var valid=request(List.of(42,3,34,11,27,18),1); valid.validate();
        invalid(request(List.of(1,1,2,3,4,5),6)); invalid(request(List.of(0,2,3,4,5,6),7));
        invalid(request(List.of(46,2,3,4,5,6),7)); invalid(request(List.of(1,2,3,4,5),7));
        invalid(request(List.of(1,2,3,4,5,6),6)); invalid(request(List.of(1,2,3,4,5,6),null));
        invalid(request(Arrays.asList(null,2,3,4,5,6),7));
        invalid(new LottoHistoryCreateRequest(null,valid.drawDate(),valid.numbers(),1,null,null,null));
        invalid(new LottoHistoryCreateRequest(1,null,valid.numbers(),1,null,null,null));
        invalid(new LottoHistoryCreateRequest(1,valid.drawDate(),valid.numbers(),1,-1L,null,null));
        AtomicBoolean exists=new AtomicBoolean(), fail=new AtomicBoolean(); AtomicInteger events=new AtomicInteger(), inserts=new AtomicInteger();
        AtomicReference<LottoHistory> saved=new AtomicReference<>();
        LottoHistoryRepository repo=(LottoHistoryRepository)Proxy.newProxyInstance(LottoHistoryRepository.class.getClassLoader(),new Class[]{LottoHistoryRepository.class},
                (p,m,a)->{if(m.getName().equals("existsByDrwNo"))return exists.get();throw new AssertionError("unexpected repository call");});
        EntityManager em=(EntityManager)Proxy.newProxyInstance(EntityManager.class.getClassLoader(),new Class[]{EntityManager.class},
                (p,m,a)->{if(m.getName().equals("persist")){inserts.incrementAndGet();saved.set((LottoHistory)a[0]);return null;}if(m.getName().equals("flush")){if(fail.get())throw new DataIntegrityViolationException("simulated concurrent duplicate");return null;}throw new AssertionError("merge must not be used");});
        ApplicationEventPublisher publisher=event->{if(!(event instanceof LottoHistoryChanged))throw new AssertionError();events.incrementAndGet();};
        var service=new LottoHistoryRegistrationService(repo,em,publisher);
        var result=service.register(valid);
        if(!result.numbers().equals(List.of(3,11,18,27,34,42))||saved.get().getTotSellamnt()!=0L||saved.get().getFirstWinamnt()!=0L||events.get()!=1)throw new AssertionError("valid registration");
        exists.set(true);
        try{service.register(valid);throw new AssertionError("duplicate accepted");}catch(ResponseStatusException expected){if(expected.getStatusCode().value()!=409)throw new AssertionError();}
        if(inserts.get()!=1||events.get()!=1)throw new AssertionError("duplicate wrote data");
        exists.set(false);fail.set(true);
        try{service.register(valid);throw new AssertionError("failure accepted");}catch(DataIntegrityViolationException expected){}
        if(events.get()!=1)throw new AssertionError("failed save published training event");
        System.out.println("PASS: required fields, ranges, duplicates, bonus exclusion, optional amounts, sorted insert-only save, no event on failure");
    }
}
