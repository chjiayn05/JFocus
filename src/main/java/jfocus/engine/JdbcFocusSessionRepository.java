package jfocus.engine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;

import jfocus.db.DatabaseCore;
import jfocus.db.StorageException;

public class JdbcFocusSessionRepository implements FocusSessionRepository {

    private final DatabaseCore databaseCore;

    public JdbcFocusSessionRepository(DatabaseCore databaseCore) {
        this.databaseCore = Objects.requireNonNull(databaseCore, "databaseCore cannot be null");
    }

    @Override
    public void saveFocusSession(FocusSessionRecord record) {
        Objects.requireNonNull(record, "record cannot be null");
        String sql = """
            INSERT INTO focus_sessions (
                session_id, subject, start_time, end_time,
                expected_duration_seconds, actual_duration_seconds,
                total_idle_seconds_deducted
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, record.sessionId);
            pstmt.setString(2, record.subject != null ? record.subject : "未分類");
            pstmt.setString(3, record.startTime != null ? record.startTime.toString() : LocalDateTime.now().toString());
            pstmt.setString(4, record.endTime != null ? record.endTime.toString() : null);
            pstmt.setInt(5, record.expectedDurationSeconds);
            pstmt.setInt(6, record.actualDurationSeconds);
            pstmt.setInt(7, record.totalIdleSecondsDeducted);
            pstmt.executeUpdate();
            System.out.println("成功存入專注 Session: " + record.sessionId);
        } catch (SQLException e) {
            throw new StorageException("寫入專注 Session 失敗", e);
        }
    }
}
