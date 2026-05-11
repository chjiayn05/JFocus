package jfocus.dashboard.model;

import java.time.LocalTime;
import java.util.List;

public class DailyDetailData {
    private long totalFocusSeconds;
    private long distractionSeconds;
    private long longestFocusSessionSeconds;
    private LocalTime startTime;
    private LocalTime endTime;
    private List<SubjectTime> subjectTimes;
    private List<TimelineEvent> timelineEvents;
    private List<DistractionApp> topDistractions;
    private double focusScore;
    private String focusComment;

    public DailyDetailData(long totalFocusSeconds, long distractionSeconds, long longestFocusSessionSeconds, 
                           LocalTime startTime, LocalTime endTime, List<SubjectTime> subjectTimes, 
                           List<TimelineEvent> timelineEvents, List<DistractionApp> topDistractions, 
                           double focusScore, String focusComment) {
        this.totalFocusSeconds = totalFocusSeconds;
        this.distractionSeconds = distractionSeconds;
        this.longestFocusSessionSeconds = longestFocusSessionSeconds;
        this.startTime = startTime;
        this.endTime = endTime;
        this.subjectTimes = subjectTimes;
        this.timelineEvents = timelineEvents;
        this.topDistractions = topDistractions;
        this.focusScore = focusScore;
        this.focusComment = focusComment;
    }

    public long getTotalFocusSeconds() { return totalFocusSeconds; }
    public long getDistractionSeconds() { return distractionSeconds; }
    public long getLongestFocusSessionSeconds() { return longestFocusSessionSeconds; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public List<SubjectTime> getSubjectTimes() { return subjectTimes; }
    public List<TimelineEvent> getTimelineEvents() { return timelineEvents; }
    public List<DistractionApp> getTopDistractions() { return topDistractions; }
    public double getFocusScore() { return focusScore; }
    public String getFocusComment() { return focusComment; }
}
