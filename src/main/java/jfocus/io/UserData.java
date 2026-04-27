package jfocus.io;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import jfocus.db.DatabaseCore;
import jfocus.db.StorageException;

/**
 * 玩家資料讀寫工具。
 */
public final class UserData {
    private static final Pattern STAGE_KEY_PATTERN = Pattern.compile("\\d{3}_[1-3]");
    private static final Set<String> DEFAULT_UNLOCKED_STAGES = Set.of("001_1", "004_1", "007_1");

    private UserData() {
        // Utility class
    }

    /**
     * 遊戲啟動時載入玩家數值資料。
     *
     * @return [專注幣, 大師石, 總經驗值]
     */
    public static int[] loadPlayerStats() {
        String sql = "SELECT coins, stones, xp FROM player_stats WHERE id = 1";

        try (Connection conn = new DatabaseCore().getConnection()) {
            ensureSchema(conn);

            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new int[] {
                        Math.max(0, rs.getInt("coins")),
                        Math.max(0, rs.getInt("stones")),
                        Math.max(0, rs.getInt("xp"))
                    };
                }
            }

            return new int[] {0, 0, 0};
        } catch (SQLException e) {
            throw new StorageException("讀取玩家數值失敗", e);
        }
    }

    /**
     * 儲存玩家數值資料。
     */
    public static void savePlayerStats(int coins, int stones, int xp) {
        String sql = """
                INSERT INTO player_stats(id, coins, stones, xp)
                VALUES (1, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    coins = excluded.coins,
                    stones = excluded.stones,
                    xp = excluded.xp
                """;

        int safeCoins = Math.max(0, coins);
        int safeStones = Math.max(0, stones);
        int safeXp = Math.max(0, xp);

        try (Connection conn = new DatabaseCore().getConnection()) {
            ensureSchema(conn);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, safeCoins);
                pstmt.setInt(2, safeStones);
                pstmt.setInt(3, safeXp);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StorageException("儲存玩家數值失敗", e);
        }
    }

    /**
     * 載入玩家已解鎖的關卡。
     */
    public static Set<String> loadUnlockedStages() {
        String sql = "SELECT stage_key FROM unlocked_stages ORDER BY stage_key";

        try (Connection conn = new DatabaseCore().getConnection()) {
            ensureSchema(conn);

            Set<String> result = new LinkedHashSet<>();
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String stageKey = rs.getString("stage_key");
                    if (isValidStageKey(stageKey)) {
                        result.add(stageKey);
                    }
                }
            }

            return result;
        } catch (SQLException e) {
            throw new StorageException("讀取解鎖關卡失敗", e);
        }
    }

    /**
     * 儲存單一解鎖關卡（重複會自動忽略）。
     */
    public static void saveUnlockedStage(String stageKey) {
        if (!isValidStageKey(stageKey)) {
            throw new IllegalArgumentException("stageKey must match pattern ddd_s (e.g. 004_2)");
        }

        String sql = "INSERT OR IGNORE INTO unlocked_stages(stage_key) VALUES (?)";

        try (Connection conn = new DatabaseCore().getConnection()) {
            ensureSchema(conn);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, stageKey);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StorageException("儲存解鎖關卡失敗", e);
        }
    }

    private static void ensureSchema(Connection conn) throws SQLException {
        String createStatsSql = """
                CREATE TABLE IF NOT EXISTS player_stats (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    coins INTEGER NOT NULL DEFAULT 0,
                    stones INTEGER NOT NULL DEFAULT 0,
                    xp INTEGER NOT NULL DEFAULT 0
                )
                """;

        String createUnlockedSql = """
                CREATE TABLE IF NOT EXISTS unlocked_stages (
                    stage_key TEXT PRIMARY KEY
                )
                """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createStatsSql);
            stmt.execute(createUnlockedSql);
            stmt.execute("INSERT OR IGNORE INTO player_stats(id, coins, stones, xp) VALUES (1, 0, 0, 0)");
        }

        ensureDefaultUnlockedStages(conn);
    }

    private static void ensureDefaultUnlockedStages(Connection conn) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM unlocked_stages";
        try (PreparedStatement countStmt = conn.prepareStatement(countSql);
             ResultSet rs = countStmt.executeQuery()) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }

        String insertSql = "INSERT OR IGNORE INTO unlocked_stages(stage_key) VALUES (?)";
        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            for (String stageKey : DEFAULT_UNLOCKED_STAGES) {
                insertStmt.setString(1, stageKey);
                insertStmt.addBatch();
            }
            insertStmt.executeBatch();
        }
    }

    private static boolean isValidStageKey(String stageKey) {
        return stageKey != null
                && !stageKey.isBlank()
                && STAGE_KEY_PATTERN.matcher(stageKey.trim()).matches();
    }
}
