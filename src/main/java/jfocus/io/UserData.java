package jfocus.io;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jfocus.db.DatabaseCore;
import jfocus.db.StorageException;

/**
 * 玩家資料讀寫工具。
 */
public final class UserData {
    private static final Pattern STAGE_KEY_PATTERN = Pattern.compile("\\d{3}_[1-3]");
    private static final Pattern POKEMON_ID_PATTERN = Pattern.compile("\\d{3}");
    private static final Set<String> DEFAULT_UNLOCKED_STAGES = Set.of("001_bulbasaur", "004_charmander", "007_squirtle");

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
    /**
     * 儲存玩家數值資料（包含當前夥伴 ID）。
     */
    public static void savePlayerStats(int coins, int stones, int xp, String partnerId) {
        // 這裡的 SQL 必須包含 partner_id，否則會報錯
        String sql = """
                INSERT INTO player_stats(id, coins, stones, xp, partner_id)
                VALUES (1, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    coins = excluded.coins,
                    stones = excluded.stones,
                    xp = excluded.xp,
                    partner_id = excluded.partner_id
                """;

        try (Connection conn = new DatabaseCore().getConnection()) {
            ensureSchema(conn);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, Math.max(0, coins));
                pstmt.setInt(2, Math.max(0, stones));
                pstmt.setInt(3, Math.max(0, xp));
                pstmt.setString(4, partnerId); // 確保這行有寫入
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            // 這就是你看到的報錯來源
            throw new StorageException("儲存玩家數值失敗", e);
        }
    }
    /**
     * 從資料庫讀取當前夥伴的 ID。
     */
    public static String loadCurrentPartner() {
        String sql = "SELECT partner_id FROM player_stats WHERE id = 1";
        try (Connection conn = new DatabaseCore().getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("partner_id");
                }
            }
        } catch (SQLException e) {
            System.err.println("讀取夥伴 ID 失敗，使用預設值。");
        }
        return "004_charmander"; // 找不到就給預設值小火龍
    }

    /**
     * 將當前夥伴的 ID 存入資料庫。
     */
    public static void saveCurrentPartner(String partnerId) {
        String sql = "UPDATE player_stats SET partner_id = ? WHERE id = 1";
        try (Connection conn = new DatabaseCore().getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, partnerId);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StorageException("儲存夥伴 ID 失敗", e);
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
                    
                    // 🌟 【關鍵修改】：不要再用那個嚴格的 isValidStageKey 了！
                    // 只要資料庫裡抓出來的東西不是 null 也不是空白，就直接加進去！
                    if (stageKey != null && !stageKey.trim().isEmpty()) {
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
     * 載入每隻寶可夢的 XP。
     */
    public static Map<String, Integer> loadPokemonXp() {
        String sql = "SELECT pokemon_id, xp FROM pokemon_xp ORDER BY pokemon_id";

        try (Connection conn = new DatabaseCore().getConnection()) {
            ensureSchema(conn);

            Map<String, Integer> result = new LinkedHashMap<>();
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String pokemonId = rs.getString("pokemon_id");
                    if (isValidPokemonId(pokemonId)) {
                        result.put(pokemonId, Math.max(0, rs.getInt("xp")));
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            throw new StorageException("讀取寶可夢 XP 失敗", e);
        }
    }

    /**
     * 以覆蓋方式儲存所有寶可夢 XP。
     */
    public static void savePokemonXp(Map<String, Integer> pokemonXpMap) {
        String deleteSql = "DELETE FROM pokemon_xp";
        String upsertSql = "INSERT INTO pokemon_xp(pokemon_id, xp) VALUES (?, ?) "
                + "ON CONFLICT(pokemon_id) DO UPDATE SET xp = excluded.xp";

        try (Connection conn = new DatabaseCore().getConnection()) {
            ensureSchema(conn);

            try (Statement deleteStmt = conn.createStatement()) {
                deleteStmt.executeUpdate(deleteSql);
            }

            if (pokemonXpMap == null || pokemonXpMap.isEmpty()) {
                return;
            }

            try (PreparedStatement upsertStmt = conn.prepareStatement(upsertSql)) {
                for (Map.Entry<String, Integer> entry : pokemonXpMap.entrySet()) {
                    String pokemonId = entry.getKey();
                    if (!isValidPokemonId(pokemonId)) {
                        continue;
                    }

                    int xp = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
                    upsertStmt.setString(1, pokemonId.trim());
                    upsertStmt.setInt(2, xp);
                    upsertStmt.addBatch();
                }
                upsertStmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new StorageException("儲存寶可夢 XP 失敗", e);
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
                    xp INTEGER NOT NULL DEFAULT 0,
                    partner_id TEXT NOT NULL DEFAULT '004'
                )
                """;

        String createUnlockedSql = """
                CREATE TABLE IF NOT EXISTS unlocked_stages (
                    stage_key TEXT PRIMARY KEY
                )
                """;

        String createPokemonXpSql = """
                CREATE TABLE IF NOT EXISTS pokemon_xp (
                    pokemon_id TEXT PRIMARY KEY,
                    xp INTEGER NOT NULL DEFAULT 0
                )
                """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createStatsSql);
            stmt.execute(createUnlockedSql);
            stmt.execute(createPokemonXpSql);
            stmt.execute("INSERT OR IGNORE INTO player_stats(id, coins, stones, xp) VALUES (1, 0, 0, 0)");
            stmt.execute("INSERT OR IGNORE INTO player_stats(id, coins, stones, xp, partner_id) VALUES (1, 0, 0, 0, '004')");
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

private static boolean isValidStageKey(String key) {
    return key != null && !key.trim().isEmpty(); // ✅ 變成超級寬鬆模式
}

    private static boolean isValidPokemonId(String pokemonId) {
        return pokemonId != null
                && !pokemonId.isBlank()
                && POKEMON_ID_PATTERN.matcher(pokemonId.trim()).matches();
    }

    public static java.util.Map<String, Integer> loadSelectedStages() {
        String sql = "SELECT pokemon_id, stage FROM pokemon_selected_stage";
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        try (Connection conn = new DatabaseCore().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("pokemon_id"), rs.getInt("stage"));
            }
        } catch (SQLException e) {
            System.err.println("讀取選擇 stage 失敗: " + e.getMessage());
        }
        return map;
    }

    public static void saveSelectedStage(String pokemonId, int stage) {
        String sql = "INSERT INTO pokemon_selected_stage(pokemon_id, stage) VALUES (?, ?) "
                   + "ON CONFLICT(pokemon_id) DO UPDATE SET stage = excluded.stage";
        try (Connection conn = new DatabaseCore().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, pokemonId);
            pstmt.setInt(2, stage);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("儲存選擇 stage 失敗: " + e.getMessage());
        }
    }

    public static void saveAppSetting(String key, String value) {
        String sql = "INSERT INTO app_settings(setting_key, setting_value) VALUES (?, ?) "
                   + "ON CONFLICT(setting_key) DO UPDATE SET setting_value = excluded.setting_value, "
                   + "updated_at = datetime('now')";
        try (Connection conn = new DatabaseCore().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("儲存設定失敗: " + e.getMessage());
        }
    }

    public static String loadAppSetting(String key, String defaultValue) {
        String sql = "SELECT setting_value FROM app_settings WHERE setting_key = ?";
        try (Connection conn = new DatabaseCore().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String val = rs.getString("setting_value");
                    return val != null ? val : defaultValue;
                }
            }
        } catch (SQLException e) {
            System.err.println("讀取設定失敗: " + e.getMessage());
        }
        return defaultValue;
    }
}
