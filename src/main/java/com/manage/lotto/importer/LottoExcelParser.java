package com.manage.lotto.importer;

import com.manage.lotto.domain.LottoRules;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 동행복권 당첨 번호 엑셀 파일(.xlsx / .xls) 파서
 * 첫 번째 시트 · B열 회차 · C~H열 당첨 번호 · I열 보너스 · K열 당첨 인원 · L열 당첨금
 */
@Slf4j
@Component
public class LottoExcelParser {

    private static final int COL_DRAW_NO = 1;
    private static final int COL_FIRST_NUMBER = 2;
    private static final int COL_BONUS = 8;
    private static final int COL_FIRST_PRIZE_WINNERS = 10;
    private static final int COL_FIRST_PRIZE_AMOUNT = 11;

    /**
     * @param firstWinAmt 빈 칸이면 null
     * @param firstWinCo  빈 칸이면 null
     */
    public record DrawRow(int drwNo, List<Integer> numbers, int bonusNo, Long firstWinAmt, Integer firstWinCo) {}

    /**
     * 당첨 번호가 완전한 행만 회차별로 반환 (같은 회차가 여러 행에 있으면 마지막 행 기준)
     */
    public List<DrawRow> parse(InputStream input) throws IOException {
        Map<Integer, DrawRow> rows = new LinkedHashMap<>();

        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();
            log.info("엑셀 파일 파싱 시작 (총 행 수: {})", lastRowNum + 1);

            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Integer drwNo = parseInteger(row.getCell(COL_DRAW_NO));
                if (drwNo == null || drwNo <= 0) continue;

                List<Integer> numbers = new ArrayList<>();
                for (int col = COL_FIRST_NUMBER; col < COL_FIRST_NUMBER + LottoRules.NUMBERS_PER_DRAW; col++) {
                    numbers.add(parseInteger(row.getCell(col)));
                }
                Integer bonusNo = parseInteger(row.getCell(COL_BONUS));

                if (numbers.contains(null) || bonusNo == null) {
                    log.warn("{}행: 당첨번호 데이터가 불완전하여 건너뜁니다.", r + 1);
                    continue;
                }

                rows.put(drwNo, new DrawRow(drwNo, List.copyOf(numbers), bonusNo,
                        parseLong(row.getCell(COL_FIRST_PRIZE_AMOUNT)),
                        parseInteger(row.getCell(COL_FIRST_PRIZE_WINNERS))));
            }
        }

        return new ArrayList<>(rows.values());
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

    /**
     * "16 명", "1,791,817,758 원" 같은 값에서 숫자만 추출
     */
    private String digits(Cell cell) {
        return getCellValue(cell).replaceAll("[^0-9]", "");
    }

    private Integer parseInteger(Cell cell) {
        String clean = digits(cell);
        return clean.isEmpty() ? null : Integer.parseInt(clean);
    }

    private Long parseLong(Cell cell) {
        String clean = digits(cell);
        return clean.isEmpty() ? null : Long.parseLong(clean);
    }
}
