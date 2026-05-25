package jfocus.dashboard;

import jfocus.dashboard.model.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;

public class MockDashboardDataManager implements DashboardDataManager {

    @Override
    public Map<LocalDate, Long> getMonthlyCalendarData(YearMonth month) {
        Map<LocalDate, Long> data = new LinkedHashMap<>();
        int lengthOfMonth = month.lengthOfMonth();
        for (int i = 1; i <= lengthOfMonth; i++) {
            LocalDate date = month.atDay(i);
            long seed = date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
            Random rand = new Random(seed);
            long totalFocusSeconds = rand.nextInt(28800) + 120; // 隨機產生 2分鐘 到 8小時
            data.put(date, totalFocusSeconds);
        }
        return data;
    }

    @Override
    public DailyDetailData getDailyDetail(LocalDate date) {
        long seed = date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
        Random rand = new Random(seed);

        // 隨機產生 2分鐘 ~ 8小時 的專注秒數
        long totalFocusSeconds = rand.nextInt(28800) + 120;
        // 隨機產生 0 ~ 2小時 的分心秒數
        long distractionSeconds = rand.nextInt(7200);
        long longestFocusSessionSeconds = totalFocusSeconds > 1800 ? totalFocusSeconds - rand.nextInt(1800) : totalFocusSeconds;
        
        LocalTime startTime = LocalTime.of(rand.nextInt(4) + 8, rand.nextInt(60)); // 8~12點間開始
        LocalTime endTime = startTime.plusSeconds(totalFocusSeconds + distractionSeconds);

        List<SubjectTime> subjectTimes = Arrays.asList(
            new SubjectTime("數學", (long)(totalFocusSeconds * 0.4)),
            new SubjectTime("英文", (long)(totalFocusSeconds * 0.3)),
            new SubjectTime("程式設計", (long)(totalFocusSeconds * 0.3))
        );

        List<TimelineEvent> timelineEvents = Arrays.asList(
            new TimelineEvent("數學", date.atTime(startTime), date.atTime(startTime.plusSeconds((long)(totalFocusSeconds * 0.4)))),
            new TimelineEvent("英文", date.atTime(startTime.plusSeconds((long)(totalFocusSeconds * 0.5))), date.atTime(startTime.plusSeconds((long)(totalFocusSeconds * 0.8)))),
            new TimelineEvent("程式設計", date.atTime(startTime.plusSeconds((long)(totalFocusSeconds * 0.9))), date.atTime(endTime))
        );

        List<DistractionApp> topDistractions = Arrays.asList(
            new DistractionApp("YouTube", (long)(distractionSeconds * 0.6)),
            new DistractionApp("Instagram", (long)(distractionSeconds * 0.3)),
            new DistractionApp("Line", (long)(distractionSeconds * 0.1))
        );

        // 採用相同的專注分數計算公式
        double totalFocusHours = totalFocusSeconds / 3600.0;
        double baseScore;
        if (totalFocusHours <= 2.0) {
            baseScore = totalFocusHours * 30.0;
        } else if (totalFocusHours <= 4.0) {
            baseScore = 60.0 + (totalFocusHours - 2.0) * 10.0;
        } else if (totalFocusHours <= 6.0) {
            baseScore = 80.0 + (totalFocusHours - 4.0) * 10.0;
        } else {
            baseScore = 100.0;
        }

        double focusRatio = 0.0;
        long totalSecs = totalFocusSeconds + distractionSeconds;
        if (totalSecs > 0) {
            focusRatio = totalFocusSeconds / (double) totalSecs;
        }

        double focusScore = (baseScore / 100.0) * focusRatio;
        String focusComment = jfocus.ui.FocusCommentGenerator.getComment(focusScore, totalFocusSeconds, distractionSeconds, date);

        return new DailyDetailData(
            totalFocusSeconds, distractionSeconds, longestFocusSessionSeconds,
            startTime, endTime, subjectTimes, timelineEvents, topDistractions,
            focusScore, focusComment
        );
    }

    @Override
    public WeeklyDetailData getWeeklyDetail(LocalDate dateInWeek) {
        long totalFocusSeconds = 100800; // 28 hours
        long distractionSeconds = 12600; // 3.5 hours
        long dailyAverageSeconds = 14400; // 4 hours

        List<SubjectTime> overallSubjectTimes = Arrays.asList(
            new SubjectTime("數學", 40000),
            new SubjectTime("英文", 30000),
            new SubjectTime("程式設計", 30800)
        );

        List<DailyChartData> dailyCharts = new ArrayList<>();
        LocalDate startOfWeek = dateInWeek.minusDays(dateInWeek.getDayOfWeek().getValue() - 1);
        for (int i = 0; i < 7; i++) {
            LocalDate day = startOfWeek.plusDays(i);
            dailyCharts.add(generateMockDailyChartData(day));
        }

        List<DistractionApp> topDistractions = Arrays.asList(
            new DistractionApp("YouTube", 8000),
            new DistractionApp("Instagram", 3000),
            new DistractionApp("Line", 1600)
        );

        return new WeeklyDetailData(
            totalFocusSeconds, distractionSeconds, dailyAverageSeconds,
            overallSubjectTimes, dailyCharts, topDistractions
        );
    }

    @Override
    public Map<YearMonth, Long> getYearlyCalendarData(int year) {
        Map<YearMonth, Long> data = new LinkedHashMap<>();
        for (int i = 1; i <= 12; i++) {
            // 每個月大約 100 小時 ~ 150 小時
            data.put(YearMonth.of(year, i), (long) (Math.random() * 180000) + 360000);
        }
        return data;
    }

    @Override
    public MonthlyDetailData getMonthlyDetail(YearMonth month) {
        long totalFocusSeconds = 432000; // 120 hours
        long distractionSeconds = 54000; // 15 hours
        long dailyAverageSeconds = totalFocusSeconds / month.lengthOfMonth();

        List<SubjectTime> overallSubjectTimes = Arrays.asList(
            new SubjectTime("數學", 180000),
            new SubjectTime("英文", 120000),
            new SubjectTime("程式設計", 132000)
        );

        List<DailyChartData> dailyCharts = new ArrayList<>();
        for (int i = 1; i <= month.lengthOfMonth(); i++) {
            dailyCharts.add(generateMockDailyChartData(month.atDay(i)));
        }

        List<DistractionApp> topDistractions = Arrays.asList(
            new DistractionApp("YouTube", 30000),
            new DistractionApp("Instagram", 15000),
            new DistractionApp("Line", 9000)
        );

        return new MonthlyDetailData(
            totalFocusSeconds, distractionSeconds, dailyAverageSeconds,
            overallSubjectTimes, dailyCharts, topDistractions
        );
    }

    private DailyChartData generateMockDailyChartData(LocalDate date) {
        long totalFocus = (long) (Math.random() * 14400) + 7200; // 2~6 hours
        LocalTime startTime = LocalTime.of((int)(Math.random() * 3) + 8, 0); // 8~10 AM
        LocalTime endTime = startTime.plusSeconds(totalFocus + 3600); // end time
        List<SubjectTime> subjectTimes = Arrays.asList(
            new SubjectTime("數學", totalFocus / 2),
            new SubjectTime("英文", totalFocus / 4),
            new SubjectTime("程式設計", totalFocus / 4)
        );
        return new DailyChartData(date, totalFocus, startTime, endTime, subjectTimes);
    }
}
