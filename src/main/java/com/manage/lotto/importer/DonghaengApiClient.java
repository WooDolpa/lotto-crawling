package com.manage.lotto.importer;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.domain.LottoRules;
import com.manage.lotto.domain.PrizeRank;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 동행복권 회차별 당첨 정보 조회 API 클라이언트
 * <p>
 * 회차 범위를 한 번에 조회하며, 회차마다 당첨 번호·추첨일·1~5등 당첨 정보·총 판매금액을 함께 준다.
 * 예전에 쓰던 {@code common.do?method=getLottoNumber} 주소는 메인 페이지로 리다이렉트되어 더 이상 JSON을 주지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DonghaengApiClient {

    /** 회차 범위 당첨 정보 (srchStrLtEpsd ~ srchEndLtEpsd) */
    private static final String DRAWS_URL = "https://www.dhlottery.co.kr/lt645/selectPstLt645Info.do";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** 전 회차를 한 번에 받으면 응답이 크므로 넉넉하게 둔다 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final ObjectMapper objectMapper;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    /**
     * 회차 범위의 당첨 정보 조회
     *
     * @param startNo 시작 회차
     * @param endNo   종료 회차 (0을 넘기면 빈 목록이 돌아온다)
     * @return 회차 오름차순 목록 (해당 범위에 추첨 결과가 없으면 빈 목록)
     */
    public List<LottoHistory> fetchDraws(int startNo, int endNo) throws IOException, InterruptedException {
        JsonNode root = get(DRAWS_URL + "?srchStrLtEpsd=" + startNo + "&srchEndLtEpsd=" + endNo);

        List<LottoHistory> draws = new ArrayList<>();
        for (JsonNode node : root.path("data").path("list")) {
            toHistory(node).ifPresent(draws::add);
        }
        draws.sort(Comparator.comparing(LottoHistory::getDrwNo));
        log.info("동행복권 API 조회 완료 ({}회 ~ {}회 요청, {}건 수신)", startNo, endNo, draws.size());
        return draws;
    }

    private JsonNode get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("동행복권 API 요청이 실패했습니다. (HTTP 상태 코드: " + response.statusCode() + ")");
        }
        return objectMapper.readTree(response.body());
    }

    /**
     * 응답 한 건을 회차 이력으로 변환 (회차·당첨 번호가 온전하지 않으면 건너뜀)
     */
    private Optional<LottoHistory> toHistory(JsonNode node) {

        Integer drwNo = intOrNull(node, "ltEpsd");
        Integer bonusNo = intOrNull(node, "bnsWnNo");
        List<Integer> numbers = new ArrayList<>();

        for (int i = 1; i <= LottoRules.NUMBERS_PER_DRAW; i++) {
            numbers.add(intOrNull(node, "tm" + i + "WnNo"));
        }
        if (drwNo == null || bonusNo == null || numbers.contains(null)) {
            log.warn("당첨 번호가 불완전한 응답을 건너뜁니다. (회차: {})", drwNo);
            return Optional.empty();
        }

        List<PrizeRank> prizes = new ArrayList<>();
        for (int rank = 1; rank <= LottoRules.PRIZE_RANKS; rank++) {
            prizes.add(new PrizeRank(intOrNull(node, "rnk" + rank + "WnNope"),
                    longOrNull(node, "rnk" + rank + "WnAmt"),
                    longOrNull(node, "rnk" + rank + "SumWnAmt")));
        }

        return Optional.of(LottoHistory.of(drwNo, numbers.stream().sorted().toList(), bonusNo,
                textOrNull(node, "ltRflYmd"), prizes,
                intOrNull(node, "sumWnNope"), longOrNull(node, "wholEpsdSumNtslAmt")));
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private static Long longOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asLong() : null;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }
}
