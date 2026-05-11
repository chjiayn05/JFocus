package jfocus.dashboard.model;

public class SubjectTime {
    private String subject;
    private long durationSeconds;

    public SubjectTime(String subject, long durationSeconds) {
        this.subject = subject;
        this.durationSeconds = durationSeconds;
    }

    public String getSubject() {
        return subject;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }
}
