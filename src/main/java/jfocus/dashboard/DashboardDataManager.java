package jfocus.dashboard;

import jfocus.dashboard.model.DailyDetailData;
import jfocus.dashboard.model.WeeklyDetailData;
import jfocus.dashboard.model.MonthlyDetailData;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

public interface DashboardDataManager {
    // 每日分頁
    Map<LocalDate, Long> getMonthlyCalendarData(YearMonth month);
    DailyDetailData getDailyDetail(LocalDate date);

    // 每週分頁
    WeeklyDetailData getWeeklyDetail(LocalDate dateInWeek);

    // 每月分頁
    Map<YearMonth, Long> getYearlyCalendarData(int year);
    MonthlyDetailData getMonthlyDetail(YearMonth month);
}
