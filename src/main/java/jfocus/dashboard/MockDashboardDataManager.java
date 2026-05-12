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
            // 隨機產生 1~8 小時的專注時間
            data.put(month.atDay(i), (long) (Math.random() * 28800) + 3600);
        }
        return data;
    }

    @Override
    public DailyDetailData getDailyDetail(LocalDate date) {
        long totalFocusSeconds = 14400; // 4 hours
        long distractionSeconds = 1800; // 30 minutes
        long longestFocusSessionSeconds = 5400; // 1.5 hours
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(15, 30);

        List<SubjectTime> subjectTimes = Arrays.asList(
            new SubjectTime("數學", 7200),
            new SubjectTime("英文", 3600),
            new SubjectTime("程式設計", 3600)
        );

        List<TimelineEvent> timelineEvents = Arrays.asList(
            new TimelineEvent("數學", date.atTime(9, 0), date.atTime(11, 0)),
            new TimelineEvent("英文", date.atTime(13, 0), date.atTime(14, 0)),
            new TimelineEvent("程式設計", date.atTime(14, 30), date.atTime(15, 30))
        );

        List<DistractionApp> topDistractions = Arrays.asList(
            new DistractionApp("YouTube", 1200),
            new DistractionApp("Instagram", 400),
            new DistractionApp("Line", 200)
        );

        double focusScore = 0.88;
        String focusComment = "太棒了！你的專注力驚人，完全進入了心流狀態，請繼續保持這股氣勢！";

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
