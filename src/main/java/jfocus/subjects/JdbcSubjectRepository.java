package jfocus.subjects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import jfocus.db.DatabaseCore;

public class JdbcSubjectRepository implements SubjectRepository {

    private final DatabaseCore db;

    public JdbcSubjectRepository(DatabaseCore db) {
        this.db = db;
    }

    @Override
    public List<String> findAll() {
        List<String> result = new ArrayList<>();
        String sql = "SELECT name FROM subjects ORDER BY sort_order ASC, id ASC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("name"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public void add(String name) {
        String sql = "INSERT OR IGNORE INTO subjects (name, sort_order) VALUES (?, (SELECT COALESCE(MAX(sort_order),0)+1 FROM subjects))";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void rename(String oldName, String newName) {
        String sql = "UPDATE subjects SET name = ? WHERE name = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName.trim());
            ps.setString(2, oldName);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void delete(String name) {
        String sql = "DELETE FROM subjects WHERE name = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
