package com.manage.lotto.importer;

import com.manage.lotto.domain.LottoHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 동행복권 회차별 당첨 번호 조회 API 클라이언트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DonghaengApiClient {

    private static final String API_URL = "https://www.dhlottery.co.kr/common.do?method=getLottoNumber&drwNo=";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    /**
     * 회차 당첨 정보 조회
     *
     * @return 요청이 실패했거나 아직 추첨되지 않은 회차면 empty
     */
    public Optional<LottoHistory> fetchDraw(int drwNo) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + drwNo))
                .header("User-Agent", USER_AGENT)
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("{}회차 요청 실패 (HTTP 상태 코드: {})", drwNo, response.statusCode());
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(response.body());
        if (!"success".equalsIgnoreCase(root.path("returnValue").asText(""))) {
            log.info("{}회차 데이터가 없거나 최신 회차에 도달했습니다.", drwNo);
            return Optional.empty();
        }

        List<Integer> numbers = List.of(
                root.path("drwtNo1").asInt(),
                root.path("drwtNo2").asInt(),
                root.path("drwtNo3").asInt(),
                root.path("drwtNo4").asInt(),
                root.path("drwtNo5").asInt(),
                root.path("drwtNo6").asInt());

        return Optional.of(LottoHistory.of(
                root.path("drwNo").asInt(),
                numbers,
                root.path("bnusNo").asInt(),
                root.path("firstWinamnt").asLong(),
                root.path("firstPrzwnerCo").asInt()));
    }
}
