package jfocus.io;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;

import jfocus.activity.ActivityRepository;
import jfocus.activity.JdbcActivityRepository;
import jfocus.db.DatabaseCore;

/**
 * 提供活動資料的查詢。
 */
public class PullData {
    private final ActivityRepository repository;

    /**
     * 使用預設 JDBC repository 建立查詢服務。
     */
    public PullData() {
        this(new JdbcActivityRepository(new DatabaseCore()));
    }

    /**
     * 使用指定 repository 建立查詢服務。
     *
     * @param repository 活動資料 repository
     */
    public PullData(ActivityRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    /**
     * 依日期統計各應用程式的使用總時長。
     *
     * @param date 目標日期，格式為 yyyy-MM-dd
     * @return 以應用程式名稱為鍵、總秒數為值的統計結果
     */
    public Map<String, Integer> getAppUsageByDate(String date) {
        return repository.getAppUsageByDate(parseDate(date, "date"));
    }

    /**
     * 依 session ID 統計各應用程式的使用總時長。
     *
     * @param sessionId 目標 session ID
     * @return 以應用程式名稱為鍵、總秒數為值的統計結果
     */
    public Map<String, Integer> getAppUsageBySession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be null or blank");
        }
        return repository.getAppUsageBySession(sessionId);
    }

    /**
     * 依日期區間統計各應用程式的使用總時長。
     *
     * @param dateStart 起始日期，格式為 yyyy-MM-dd
     * @param dateEnd 結束日期，格式為 yyyy-MM-dd
     * @return 以應用程式名稱為鍵、總秒數為值的統計結果
     */
    public Map<String, Integer> getAppUsageByRange(String dateStart, String dateEnd) {
        LocalDate startDate = parseDate(dateStart, "dateStart");
        LocalDate endDate = parseDate(dateEnd, "dateEnd");
        return repository.getAppUsageByRange(startDate, endDate);
    }

    private LocalDate parseDate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + " must use yyyy-MM-dd format", e);
        }
    }
}
