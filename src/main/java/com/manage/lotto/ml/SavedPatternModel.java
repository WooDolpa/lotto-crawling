package com.manage.lotto.ml;

import smile.classification.RandomForest;

import java.io.Serializable;
import java.time.Instant;

/**
 * @param hash 학습에 사용한 당첨 이력의 SHA-256 지문
 */
public record SavedPatternModel(String version, String hash, int baseDrawNo, int historyCount,
                                Instant trainedAt, RandomForest model) implements Serializable {}
