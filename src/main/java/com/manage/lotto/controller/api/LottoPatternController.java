package com.manage.lotto.controller.api;

import com.manage.lotto.service.LottoPatternExperimentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lotto/pattern")
@RequiredArgsConstructor
public class LottoPatternController {
    private final LottoPatternExperimentService service;
    private final com.manage.lotto.service.LottoPatternModelService models;

    @GetMapping("/status")
    public com.manage.lotto.service.LottoPatternModelService.Status status() { return models.status(); }

    @PostMapping("/train")
    @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public com.manage.lotto.service.LottoPatternModelService.Status train() {
        models.requestRefresh(); return models.status();
    }

    @GetMapping("/predict")
    public com.manage.lotto.service.LottoPatternModelService.Prediction predict() { return models.predict(); }

    @PostMapping("/experiment")
    public LottoPatternExperimentService.Report experiment(
            @RequestParam(name = "testDraws", defaultValue = "5") int testDraws,
            @RequestParam(name = "seed", defaultValue = "42") long seed) {
        return service.run(testDraws, seed);
    }
}
