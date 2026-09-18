package com.manage.lotto.ml;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * 학습된 A 점수 모델 파일 저장/로드 (설정·파일 이름의 pattern은 이전 패턴 모델에서 유래)
 */
@Component
public class PatternModelStore {

    private final Path file;

    public PatternModelStore(@Value("${lotto.pattern.model-file:models/lotto-pattern.bin}") String file) {
        this.file = Path.of(file).toAbsolutePath();
    }

    public Optional<SavedPatternModel> load() throws IOException, ClassNotFoundException {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(file))) {
            return Optional.of((SavedPatternModel) input.readObject());
        }
    }

    public void save(SavedPatternModel model) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "lotto-pattern-", ".tmp");
        try {
            try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(temp))) {
                output.writeObject(model);
            }
            // Keep the previous file if the filesystem cannot perform an atomic replacement.
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
