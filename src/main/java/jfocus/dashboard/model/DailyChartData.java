package jfocus.dashboard.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class DailyChartData {
    private LocalDate date;
    private long totalFocusSeconds;
    private LocalTime startTime;
    private LocalTime endTime;
    private List<SubjectTime> subjectTimes;

    public DailyChartData(LocalDate date, long totalFocusSeconds, LocalTime startTime, LocalTime endTime, List<SubjectTime> subjectTimes) {
        this.date = date;
        this.totalFocusSeconds = totalFocusSeconds;
        this.startTime = startTime;
        this.endTime = endTime;
        this.subjectTimes = subjectTimes;
    }

    public LocalDate getDate() { return date; }
    public long getTotalFocusSeconds() { return totalFocusSeconds; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public List<SubjectTime> getSubjectTimes() { return subjectTimes; }
}
