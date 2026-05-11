package jfocus.todo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jfocus.db.DatabaseCore;
import jfocus.db.StorageException;

/**
 * 使用 JDBC（SQLite）實作的 {@link TodoRepository}。
 */
public class JdbcTodoRepository implements TodoRepository {

    private final DatabaseCore db;

    /**
     * 使用指定的 {@link DatabaseCore} 建立 repository，並在需要時自動初始化資料庫。
     *
     * @param db 資料庫核心
     */
    public JdbcTodoRepository(DatabaseCore db) {
        this.db = Objects.requireNonNull(db, "db cannot be null");
        if (DatabaseCore.isAutoInitializeEnabled()) {
            db.initialize();
        }
    }

    @Override
    public void saveTodo(TodoRecord todo) {
        Objects.requireNonNull(todo, "todo cannot be null");
        String sql = "INSERT INTO todos (task, deadline, is_done, notes) VALUES (?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, todo.task());
            ps.setString(2, todo.deadline() != null ? todo.deadline().toString() : null);
            ps.setInt(3, todo.isDone() ? 1 : 0);
            ps.setString(4, todo.notes());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("新增待辦事項失敗", e);
        }
    }

    @Override
    public void updateTodo(TodoRecord todo) {
        Objects.requireNonNull(todo, "todo cannot be null");
        String sql = "UPDATE todos SET task = ?, deadline = ?, is_done = ?, notes = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, todo.task());
            ps.setString(2, todo.deadline() != null ? todo.deadline().toString() : null);
            ps.setInt(3, todo.isDone() ? 1 : 0);
            ps.setString(4, todo.notes());
            ps.setInt(5, todo.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("更新待辦事項失敗", e);
        }
    }

    @Override
    public void deleteTodo(int id) {
        String sql = "DELETE FROM todos WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("刪除待辦事項失敗", e);
        }
    }

    @Override
    public List<TodoRecord> getAllTodos() {
        String sql = "SELECT id, task, deadline, is_done, notes FROM todos ORDER BY deadline ASC NULLS LAST";
        List<TodoRecord> results = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapTodo(rs));
            }
        } catch (SQLException e) {
            throw new StorageException("查詢所有待辦事項失敗", e);
        }
        return results;
    }

    @Override
    public Optional<TodoRecord> getTodoById(int id) {
        String sql = "SELECT id, task, deadline, is_done, notes FROM todos WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapTodo(rs));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("查詢待辦事項失敗，id=" + id, e);
        }
        return Optional.empty();
    }

    private TodoRecord mapTodo(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String task = rs.getString("task");
        String deadlineStr = rs.getString("deadline");
        LocalDateTime deadline = deadlineStr != null ? LocalDateTime.parse(deadlineStr) : null;
        boolean isDone = rs.getInt("is_done") == 1;
        String notes = rs.getString("notes");
        return new TodoRecord(id, task, deadline, isDone, notes);
    }
}
