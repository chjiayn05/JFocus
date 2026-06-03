package jfocus.dashboard;

import jfocus.dashboard.model.*;
import jfocus.db.DatabaseCore;
import jfocus.db.StorageException;
import jfocus.ui.FocusCommentGenerator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class JdbcDashboardDataManager implements DashboardDataManager {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DatabaseCore databaseCore;

    public JdbcDashboardDataManager() {
        this(new DatabaseCore());
    }

    public JdbcDashboardDataManager(DatabaseCore databaseCore) {
        this.databaseCore = Objects.requireNonNull(databaseCore, "databaseCore cannot be null");
    }

    private LocalDateTime parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return value.contains("T")
                    ? LocalDateTime.parse(value)
                    : LocalDateTime.parse(value, TIMESTAMP_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private String toStartOfDayStr(LocalDate date) {
        return TIMESTAMP_FORMATTER.format(date.atStartOfDay());
    }

    @Override
    public Map<LocalDate, Long> getMonthlyCalendarData(YearMonth month) {
        Map<LocalDate, Long> data = new LinkedHashMap<>();
        int lengthOfMonth = month.lengthOfMonth();
        for (int i = 1; i <= lengthOfMonth; i++) {
            data.put(month.atDay(i), 0L);
        }

        String sql = "SELECT start_time, duration FROM activities " +
                "WHERE is_focus = 1 AND start_time >= ? AND start_time < ?";
        
        String startStr = toStartOfDayStr(month.atDay(1));
        String endStr = toStartOfDayStr(month.plusMonths(1).atDay(1));

        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    LocalDateTime startTime = parseTimestamp(rs.getString("start_time"));
                    if (startTime != null) {
                        LocalDate date = startTime.toLocalDate();
                        long duration = rs.getLong("duration");
                        data.put(date, data.getOrDefault(date, 0L) + duration);
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load monthly calendar data", e);
        }
        return data;
    }

    @Override
    public DailyDetailData getDailyDetail(LocalDate date) {
        String startStr = toStartOfDayStr(date);
        String endStr = toStartOfDayStr(date.plusDays(1));

        long totalFocusSeconds = 0;
        long distractionSeconds = 0;
        long longestFocusSessionSeconds = 0;
        LocalTime startTime = null;
        LocalTime endTime = null;

        // 1. Get total focus seconds and start/end times
        String focusSql = "SELECT start_time, end_time, duration FROM activities " +
                "WHERE is_focus = 1 AND start_time >= ? AND start_time < ?";
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(focusSql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    totalFocusSeconds += rs.getLong("duration");
                    LocalDateTime st = parseTimestamp(rs.getString("start_time"));
                    LocalDateTime et = parseTimestamp(rs.getString("end_time"));
                    if (st != null) {
                        LocalTime lt = st.toLocalTime();
                        if (startTime == null || lt.isBefore(startTime)) {
                            startTime = lt;
                        }
                    }
                    if (et != null) {
                        LocalTime lt = et.toLocalTime();
                        if (endTime == null || lt.isAfter(endTime)) {
                            endTime = lt;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query focus details", e);
        }

        // 2. Get distraction seconds
        String distractionSql = "SELECT SUM(duration) AS total FROM activities " +
                "WHERE is_focus = 0 AND start_time >= ? AND start_time < ?";
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(distractionSql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    distractionSeconds = rs.getLong("total");
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query distraction details", e);
        }

        // 3. Get longest focus session duration from focus_sessions
        String sessionSql = "SELECT MAX(actual_duration_seconds) AS max_duration FROM focus_sessions " +
                "WHERE start_time >= ? AND start_time < ?";
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sessionSql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    longestFocusSessionSeconds = rs.getLong("max_duration");
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query longest focus session", e);
        }

        // 4. Get subject times
        List<SubjectTime> subjectTimes = new ArrayList<>();
        String subjectSql = "SELECT COALESCE(f.subject, '未分類') AS subject_name, SUM(a.duration) AS total_duration " +
                "FROM activities a " +
                "LEFT JOIN focus_sessions f ON a.session_id = f.session_id " +
                "WHERE a.is_focus = 1 AND a.start_time >= ? AND a.start_time < ? " +
                "GROUP BY subject_name ORDER BY total_duration DESC";
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(subjectSql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    subjectTimes.add(new SubjectTime(rs.getString("subject_name"), rs.getLong("total_duration")));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query subject times", e);
        }

        // 5. Get timeline events from focus_sessions
        List<TimelineEvent> timelineEvents = new ArrayList<>();
        String timelineSql = "SELECT subject, start_time, end_time FROM focus_sessions " +
                "WHERE start_time >= ? AND start_time < ? ORDER BY start_time ASC";
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(timelineSql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    LocalDateTime st = parseTimestamp(rs.getString("start_time"));
                    LocalDateTime et = parseTimestamp(rs.getString("end_time"));
                    if (st != null && et != null) {
                        timelineEvents.add(new TimelineEvent(rs.getString("subject"), st, et));
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query timeline events", e);
        }

        // 6. Get top distractions
        List<DistractionApp> topDistractions = new ArrayList<>();
        String topDistractionSql = "SELECT app_name, SUM(duration) AS total_duration " +
                "FROM activities " +
                "WHERE is_focus = 0 AND start_time >= ? AND start_time < ? " +
                "GROUP BY app_name ORDER BY total_duration DESC LIMIT 10";
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(topDistractionSql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    topDistractions.add(new DistractionApp(rs.getString("app_name"), rs.getLong("total_duration")));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query top distractions", e);
        }

        if (startTime == null) startTime = LocalTime.MIDNIGHT;
        if (endTime == null) endTime = LocalTime.MIDNIGHT;

        // 計算基本分 (2小時 -> 60分, 4小時 -> 80分, 6小時 -> 100分)
        double totalFocusHours = totalFocusSeconds / 3600.0;
        double baseScore;
        if (totalFocusHours <= 2.0) {
            baseScore = totalFocusHours * 30.0; // 0~2小時線性從 0 到 60
        } else if (totalFocusHours <= 4.0) {
            baseScore = 60.0 + (totalFocusHours - 2.0) * 10.0; // 2~4小時線性從 60 到 80
        } else if (totalFocusHours <= 6.0) {
            baseScore = 80.0 + (totalFocusHours - 4.0) * 10.0; // 4~6小時線性從 80 到 100
        } else {
            baseScore = 100.0; // 超過6小時為滿分 100
        }

        // 計算專注時間佔總時間的比例
        double focusRatio = 0.0;
        long totalSecs = totalFocusSeconds + distractionSeconds;
        if (totalSecs > 0) {
            focusRatio = totalFocusSeconds / (double) totalSecs;
        }

        // 最終分數 = 基本分 * 專注時間比例
        double focusScore = (baseScore / 100.0) * focusRatio;

        String focusComment = FocusCommentGenerator.getComment(focusScore, totalFocusSeconds, distractionSeconds, date);

        return new DailyDetailData(
                totalFocusSeconds, distractionSeconds, longestFocusSessionSeconds,
                startTime, endTime, subjectTimes, timelineEvents, topDistractions,
                focusScore, focusComment
        );
    }

    @Override
    public WeeklyDetailData getWeeklyDetail(LocalDate dateInWeek) {
        LocalDate startOfWeek = dateInWeek.minusDays(dateInWeek.getDayOfWeek().getValue() - 1);
        LocalDate endOfWeek = startOfWeek.plusDays(6);

        String startStr = toStartOfDayStr(startOfWeek);
        String endStr = toStartOfDayStr(endOfWeek.plusDays(1));

        long totalFocusSeconds = 0;
        long distractionSeconds = 0;

        // 1. Total focus and distraction
        String sumSql = "SELECT is_focus, SUM(duration) AS total FROM activities " +
                "WHERE start_time >= ? AND start_time < ? GROUP BY is_focus";
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sumSql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int isFocus = rs.getInt("is_focus");
                    if (isFocus == 1) {
                        totalFocusSeconds = rs.getLong("total");
                    } else {
                        distractionSeconds = rs.getLong("total");
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query weekly totals", e);
        }

        long dailyAverageSeconds = totalFocusSeconds / 7;

        // 2. Overall subject times
        List<SubjectTime> overallSubjectTimes = new ArrayList<>();
        String subjectSql = "SELECT COALESCE(f.subject, '未分類') AS subject_name, SUM(a.duration) AS total_duration " +
                "FROM activities a " +
                "LEFT JOIN focus_sessions f ON a.session_id = f.session_id " +
                "WHERE a.is_focus = 1 AND a.start_time >= ? AND a.start_time < ? " +
                "GROUP BY subject_name ORDER BY total_duration DESC";
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(subjectSql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    overallSubjectTimes.add(new SubjectTime(rs.getString("subject_name"), rs.getLong("total_duration")));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query weekly subject times", e);
        }

        // 3. Daily charts
        List<DailyChartData> dailyCharts = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = startOfWeek.plusDays(i);
            dailyCharts.add(getDailyChartData(day));
        }

        // 4. Top distractions
        List<DistractionApp> topDistractions = new ArrayList<>();
        String topDistractionSql = "SELECT app_name, SUM(duration) AS total_duration " +
                "FROM activities " +
                "WHERE is_focus = 0 AND start_time >= ? AND start_time < ? " +
                "GROUP BY app_name ORDER BY total_duration DESC LIMIT 10";
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(topDistractionSql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    topDistractions.add(new DistractionApp(rs.getString("app_name"), rs.getLong("total_duration")));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query weekly top distractions", e);
        }

        return new WeeklyDetailData(
                totalFocusSeconds, distractionSeconds, dailyAverageSeconds,
                overallSubjectTimes, dailyCharts, topDistractions
        );
    }

    @Override
    public Map<YearMonth, Long> getYearlyCalendarData(int year) {
        Map<YearMonth, Long> data = new LinkedHashMap<>();
        for (int i = 1; i <= 12; i++) {
            data.put(YearMonth.of(year, i), 0L);
        }

        String sql = "SELECT start_time, duration FROM activities " +
                "WHERE is_focus = 1 AND start_time >= ? AND start_time < ?";
        
        String startStr = toStartOfDayStr(LocalDate.of(year, 1, 1));
        String endStr = toStartOfDayStr(LocalDate.of(year + 1, 1, 1));

        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    LocalDateTime startTime = parseTimestamp(rs.getString("start_time"));
                    if (startTime != null) {
                        YearMonth ym = YearMonth.from(startTime);
                        long duration = rs.getLong("duration");
                        data.put(ym, data.getOrDefault(ym, 0L) + duration);
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load yearly calendar data", e);
        }
        return data;
    }

    @Override
    public MonthlyDetailData getMonthlyDetail(YearMonth month) {
        LocalDate startOfMonth = month.atDay(1);
        LocalDate endOfMonth = month.atEndOfMonth();

        String startStr = toStartOfDayStr(startOfMonth);
        String endStr = toStartOfDayStr(endOfMonth.plusDays(1));

        long totalFocusSeconds = 0;
        long distractionSeconds = 0;

        // 1. Total focus and distraction
        String sumSql = "SELECT is_focus, SUM(duration) AS total FROM activities " +
                "WHERE start_time >= ? AND start_time < ? GROUP BY is_focus";
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sumSql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int isFocus = rs.getInt("is_focus");
                    if (isFocus == 1) {
                        totalFocusSeconds = rs.getLong("total");
                    } else {
                        distractionSeconds = rs.getLong("total");
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query monthly totals", e);
        }

        long dailyAverageSeconds = totalFocusSeconds / month.lengthOfMonth();

        // 2. Overall subject times
        List<SubjectTime> overallSubjectTimes = new ArrayList<>();
        String subjectSql = "SELECT COALESCE(f.subject, '未分類') AS subject_name, SUM(a.duration) AS total_duration " +
                "FROM activities a " +
                "LEFT JOIN focus_sessions f ON a.session_id = f.session_id " +
                "WHERE a.is_focus = 1 AND a.start_time >= ? AND a.start_time < ? " +
                "GROUP BY subject_name ORDER BY total_duration DESC";
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(subjectSql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    overallSubjectTimes.add(new SubjectTime(rs.getString("subject_name"), rs.getLong("total_duration")));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query monthly subject times", e);
        }

        // 3. Daily charts
        List<DailyChartData> dailyCharts = new ArrayList<>();
        for (int i = 1; i <= month.lengthOfMonth(); i++) {
            dailyCharts.add(getDailyChartData(month.atDay(i)));
        }

        // 4. Top distractions
        List<DistractionApp> topDistractions = new ArrayList<>();
        String topDistractionSql = "SELECT app_name, SUM(duration) AS total_duration " +
                "FROM activities " +
                "WHERE is_focus = 0 AND start_time >= ? AND start_time < ? " +
                "GROUP BY app_name ORDER BY total_duration DESC LIMIT 10";
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(topDistractionSql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    topDistractions.add(new DistractionApp(rs.getString("app_name"), rs.getLong("total_duration")));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query monthly top distractions", e);
        }

        return new MonthlyDetailData(
                totalFocusSeconds, distractionSeconds, dailyAverageSeconds,
                overallSubjectTimes, dailyCharts, topDistractions
        );
    }

    private DailyChartData getDailyChartData(LocalDate date) {
        String startStr = toStartOfDayStr(date);
        String endStr = toStartOfDayStr(date.plusDays(1));

        long totalFocusSeconds = 0;
        LocalTime startTime = null;
        LocalTime endTime = null;

        // 1. Get total focus and start/end times
        String focusSql = "SELECT start_time, end_time, duration FROM activities " +
                "WHERE is_focus = 1 AND start_time >= ? AND start_time < ?";
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(focusSql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    totalFocusSeconds += rs.getLong("duration");
                    LocalDateTime st = parseTimestamp(rs.getString("start_time"));
                    LocalDateTime et = parseTimestamp(rs.getString("end_time"));
                    if (st != null) {
                        LocalTime lt = st.toLocalTime();
                        if (startTime == null || lt.isBefore(startTime)) {
                            startTime = lt;
                        }
                    }
                    if (et != null) {
                        LocalTime lt = et.toLocalTime();
                        if (endTime == null || lt.isAfter(endTime)) {
                            endTime = lt;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query daily chart focus data", e);
        }

        // 2. Get subject times
        List<SubjectTime> subjectTimes = new ArrayList<>();
        String subjectSql = "SELECT COALESCE(f.subject, '未分類') AS subject_name, SUM(a.duration) AS total_duration " +
                "FROM activities a " +
                "LEFT JOIN focus_sessions f ON a.session_id = f.session_id " +
                "WHERE a.is_focus = 1 AND a.start_time >= ? AND a.start_time < ? " +
                "GROUP BY subject_name ORDER BY total_duration DESC";
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(subjectSql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    subjectTimes.add(new SubjectTime(rs.getString("subject_name"), rs.getLong("total_duration")));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query daily chart subject times", e);
        }

        if (startTime == null) startTime = LocalTime.MIDNIGHT;
        if (endTime == null) endTime = LocalTime.MIDNIGHT;

        return new DailyChartData(date, totalFocusSeconds, startTime, endTime, subjectTimes);
    }
}
