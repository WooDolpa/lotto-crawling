package com.manage.lotto.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.repository.LottoHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.poi.ss.usermodel.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {

    private final LottoHistoryRepository lottoHistoryRepository;
    private final ObjectMapper objectMapper;
    private final org.springframework.context.ApplicationEventPublisher events;

    private static final String DONGHAENG_API_URL = "https://www.dhlottery.co.kr/common.do?method=getLottoNumber&drwNo=";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 특정 회차 번호부터 시작하여 최신 회차까지 동행복권 API 데이터를 수집 및 저장
     *
     * @param limitSync 최대 동기화할 회차 개수 (0 이하일 경우 최신 회차까지 계속 수집)
     * @return 수집된 회차 수
     */
    @Transactional
    public int syncFromDonghaengApi(int limitSync) {
        Optional<LottoHistory> latestOpt = lottoHistoryRepository.findTopByOrderByDrwNoDesc();
        int startDrwNo = latestOpt.map(h -> h.getDrwNo() + 1).orElse(1);

        log.info("동행복권 API 동기화 시작 (시작 회차: {})", startDrwNo);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        int syncedCount = 0;
        int currentDrwNo = startDrwNo;

        while (limitSync <= 0 || syncedCount < limitSync) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(DONGHAENG_API_URL + currentDrwNo))
                        .header("User-Agent", USER_AGENT)
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    log.warn("{}회차 요청 실패 (HTTP 상태 코드: {})", currentDrwNo, response.statusCode());
                    break;
                }

                JsonNode root = objectMapper.readTree(response.body());
                String returnValue = root.path("returnValue").asText("");

                if (!"success".equalsIgnoreCase(returnValue)) {
                    log.info("{}회차 데이터가 없거나 최신 회차에 도달했습니다.", currentDrwNo);
                    break;
                }

                LottoHistory history = LottoHistory.builder()
                        .drwNo(root.path("drwNo").asInt())
                        .drwDate(LocalDate.parse(root.path("drwNoDate").asText(), DateTimeFormatter.ISO_LOCAL_DATE))
                        .drwtNo1(root.path("drwtNo1").asInt())
                        .drwtNo2(root.path("drwtNo2").asInt())
                        .drwtNo3(root.path("drwtNo3").asInt())
                        .drwtNo4(root.path("drwtNo4").asInt())
                        .drwtNo5(root.path("drwtNo5").asInt())
                        .drwtNo6(root.path("drwtNo6").asInt())
                        .bnusNo(root.path("bnusNo").asInt())
                        .totSellamnt(root.path("totSellamnt").asLong())
                        .firstWinamnt(root.path("firstWinamnt").asLong())
                        .firstPrzwnerCo(root.path("firstPrzwnerCo").asInt())
                        .build();

                lottoHistoryRepository.save(history);
                syncedCount++;
                currentDrwNo++;

                // 동행복권 서버 부하 방지 딜레이 (40ms)
                Thread.sleep(40);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("{}회차 동기화 중 오류 발생: {}", currentDrwNo, e.getMessage(), e);
                break;
            }
        }

        log.info("동행복권 API 동기화 완료 (총 {}건 적재)", syncedCount);
        if (syncedCount > 0) events.publishEvent(new LottoHistoryChanged());
        return syncedCount;
    }

    /**
     * 동행복권 엑셀 파일(.xlsx / .xls) 파싱 및 DB 일괄 적재
     */
    @Transactional
    public Map<String, Object> parseAndSaveExcel(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드된 엑셀 파일이 비어 있습니다.");
        }

        List<LottoHistory> historyList = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();
            log.info("엑셀 파일 파싱 시작 (총 행 수: {})", lastRowNum + 1);

            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                // B열 (Index 1): 회차
                Integer drwNo = parseInteger(getCellValue(row.getCell(1)));
                if (drwNo == null || drwNo <= 0) continue;

                // C~H열 (Index 2~7): 당첨 번호 1~6
                Integer no1 = parseInteger(getCellValue(row.getCell(2)));
                Integer no2 = parseInteger(getCellValue(row.getCell(3)));
                Integer no3 = parseInteger(getCellValue(row.getCell(4)));
                Integer no4 = parseInteger(getCellValue(row.getCell(5)));
                Integer no5 = parseInteger(getCellValue(row.getCell(6)));
                Integer no6 = parseInteger(getCellValue(row.getCell(7)));

                // I열 (Index 8): 보너스
                Integer bnusNo = parseInteger(getCellValue(row.getCell(8)));

                // K열 (Index 10): 당첨게임수 (예: "16 명")
                Integer firstPrzwnerCo = parseInteger(getCellValue(row.getCell(10)));

                // L열 (Index 11): 1게임당 당첨금액 (예: "1,791,817,758 원")
                Long firstWinamnt = parseLong(getCellValue(row.getCell(11)));

                if (no1 == null || no2 == null || no3 == null || no4 == null || no5 == null || no6 == null || bnusNo == null) {
                    log.warn("{}행: 당첨번호 데이터가 불완전하여 건너뜁니다.", r + 1);
                    continue;
                }

                // 1회차(2002-12-07) 기준으로 회차별 토요일 추첨일 계산
                LocalDate drwDate = LocalDate.of(2002, 12, 7).plusWeeks(drwNo - 1);

                LottoHistory history = LottoHistory.builder()
                        .drwNo(drwNo)
                        .drwDate(drwDate)
                        .drwtNo1(no1)
                        .drwtNo2(no2)
                        .drwtNo3(no3)
                        .drwtNo4(no4)
                        .drwtNo5(no5)
                        .drwtNo6(no6)
                        .bnusNo(bnusNo)
                        .firstPrzwnerCo(firstPrzwnerCo != null ? firstPrzwnerCo : 0)
                        .firstWinamnt(firstWinamnt != null ? firstWinamnt : 0L)
                        .totSellamnt(0L)
                        .build();

                historyList.add(history);
            }

            lottoHistoryRepository.saveAll(historyList);
            if (!historyList.isEmpty()) events.publishEvent(new LottoHistoryChanged());
            log.info("엑셀 파일 파싱 및 DB 적재 완료 (총 {}건 저장)", historyList.size());
        }

        int totalSaved = historyList.size();
        Integer latestDrwNo = historyList.stream().map(LottoHistory::getDrwNo).max(Integer::compareTo).orElse(null);
        Integer oldestDrwNo = historyList.stream().map(LottoHistory::getDrwNo).min(Integer::compareTo).orElse(null);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("totalSaved", totalSaved);
        response.put("latestDrwNo", latestDrwNo);
        response.put("oldestDrwNo", oldestDrwNo);
        response.put("message", String.format("%d건의 로또 당첨 데이터가 성공적으로 적재되었습니다. (회차 범위: %s ~ %s)",
                totalSaved, oldestDrwNo != null ? oldestDrwNo + "회" : "-", latestDrwNo != null ? latestDrwNo + "회" : "-"));

        return response;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == (long) val) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case FORMULA -> {
                try {
                    yield String.valueOf((long) cell.getNumericCellValue());
                } catch (Exception e) {
                    yield cell.getStringCellValue().trim();
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private Integer parseInteger(String str) {
        if (str == null || str.isBlank()) return null;
        String clean = str.replaceAll("[^0-9]", "");
        if (clean.isEmpty()) return null;
        return Integer.parseInt(clean);
    }

    private Long parseLong(String str) {
        if (str == null || str.isBlank()) return null;
        String clean = str.replaceAll("[^0-9]", "");
        if (clean.isEmpty()) return null;
        return Long.parseLong(clean);
    }
}
