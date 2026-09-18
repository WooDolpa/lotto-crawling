package com.manage.lotto.dto;

public record ExcelUploadResponse(String status, int totalSaved, int insertedCount, int updatedCount,
                                  Integer latestDrwNo, Integer oldestDrwNo, String message) {}
