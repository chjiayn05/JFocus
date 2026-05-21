package jfocus.dashboard.model;

import java.util.List;

public class MonthlyDetailData {
    private long totalFocusSeconds;
    private long distractionSeconds;
    private long dailyAverageSeconds;
    private List<SubjectTime> overallSubjectTimes;
    private List<DailyChartData> dailyCharts;
    private List<DistractionApp> topDistractions;

    public MonthlyDetailData(long totalFocusSeconds, long distractionSeconds, long dailyAverageSeconds, 
                             List<SubjectTime> overallSubjectTimes, List<DailyChartData> dailyCharts,
                             List<DistractionApp> topDistractions) {
        this.totalFocusSeconds = totalFocusSeconds;
        this.distractionSeconds = distractionSeconds;
        this.dailyAverageSeconds = dailyAverageSeconds;
        this.overallSubjectTimes = overallSubjectTimes;
        this.dailyCharts = dailyCharts;
        this.topDistractions = topDistractions;
    }

    public long getTotalFocusSeconds() { return totalFocusSeconds; }
    public long getDistractionSeconds() { return distractionSeconds; }
    public long getDailyAverageSeconds() { return dailyAverageSeconds; }
    public List<SubjectTime> getOverallSubjectTimes() { return overallSubjectTimes; }
    public List<DailyChartData> getDailyCharts() { return dailyCharts; }
    public List<DistractionApp> getTopDistractions() { return topDistractions; }
}
