package jfocus.dashboard.model;

import java.time.LocalDateTime;

public class TimelineEvent {
    private String subject;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public TimelineEvent(String subject, LocalDateTime startTime, LocalDateTime endTime) {
        this.subject = subject;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getSubject() {
        return subject;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }
}
