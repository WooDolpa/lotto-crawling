package com.manage.lotto.ml;

import com.manage.lotto.domain.LottoHistory;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class LottoPatternFeatures {
    public static final int WINDOW = 10;
    public static double x(int n) { return ((n - 1) % 7) * 28.0; }
    public static double y(int n) { return ((n - 1) / 7) * 33.0; }

    // Sorted six-point path: centroid, extent, length, turning angle, row/column counts.
    public static double[] shape(List<Integer> numbers) {
        List<Integer> sorted = numbers.stream().sorted().toList();
        double[] out = new double[20];
        double minX = 168, minY = 198, maxX = 0, maxY = 0;
        for (int n : sorted) {
            double x = x(n), y = y(n);
            out[0] += x / 6; out[1] += y / 6;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            out[6 + (n - 1) / 7]++;
            out[13 + (n - 1) % 7]++;
        }
        out[2] = maxX - minX; out[3] = maxY - minY;
        for (int i = 1; i < sorted.size(); i++) {
            int a = sorted.get(i - 1), b = sorted.get(i);
            double dx = x(b) - x(a), dy = y(b) - y(a);
            out[4] += Math.hypot(dx, dy);
            if (i > 1) {
                int prev = sorted.get(i - 2);
                double px = x(a) - x(prev), py = y(a) - y(prev);
                double cosine = (px * dx + py * dy) / (Math.hypot(px, py) * Math.hypot(dx, dy));
                out[5] += Math.acos(Math.max(-1, Math.min(1, cosine)));
            }
        }
        return out;
    }

    public double[] features(int ball, List<LottoHistory> past, LottoFeatureExtractor base, boolean pattern) {
        double[] original = base.extractBallFeatures(ball, past);
        if (!pattern) return original;
        double[] out = Arrays.copyOf(original, 6 + 2 + WINDOW * 25);
        out[6] = x(ball); out[7] = y(ball);
        for (int lag = 0; lag < WINDOW; lag++) {
            List<Integer> numbers = past.get(past.size() - 1 - lag).getNumbers();
            double[] shape = shape(numbers);
            int offset = 8 + lag * 25;
            System.arraycopy(shape, 0, out, offset, shape.length);
            out[offset + 20] = x(ball) - shape[0];
            out[offset + 21] = y(ball) - shape[1];
            for (int n : numbers) {
                if ((n - 1) / 7 == (ball - 1) / 7) out[offset + 22]++;
                if ((n - 1) % 7 == (ball - 1) % 7) out[offset + 23]++;
            }
            out[offset + 24] = numbers.stream().mapToDouble(n -> Math.hypot(x(n) - x(ball), y(n) - y(ball))).min().orElseThrow();
        }
        return out;
    }

    // 0..1 similarity of sorted path vertices; separate from number matches.
    public static double similarity(List<Integer> a, List<Integer> b) {
        a = a.stream().sorted().toList(); b = b.stream().sorted().toList();
        double distance = 0;
        for (int i = 0; i < 6; i++) distance += Math.hypot(x(a.get(i)) - x(b.get(i)), y(a.get(i)) - y(b.get(i)));
        return 1 - distance / (6 * Math.hypot(168, 198));
    }
}
