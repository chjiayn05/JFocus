package jfocus.io;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jfocus.db.DatabaseCore;
import jfocus.db.StorageException;

/**
 * 玩家資料讀寫工具。
 */
public final class UserData {
    private static final Set<String> DEFAULT_UNLOCKED_STAGES = Set.of(
            "001_bulbasaur_1", "004_charmander_1", "007_squirtle_1");

    private UserData() {
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
     * 儲存玩家數值資料（包含當前夥伴 ID）。
     */
    public static void savePlayerStats(int coins, int stones, int xp, String partnerId) {
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
                pstmt.setString(4, partnerId);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
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
        return "004_charmander";
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
     * 載入玩家已解鎖的關卡，返回 pokemonId → 已解鎖 stage 集合（1/2/3）。
     */
    public static Map<String, Set<Integer>> loadUnlockedStages() {
        String sql = "SELECT pokemon_id, stage1_unlocked, stage2_unlocked, stage3_unlocked "
                   + "FROM pokemon_progress";

        try (Connection conn = new DatabaseCore().getConnection()) {
            ensureSchema(conn);

            Map<String, Set<Integer>> result = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("pokemon_id");
                    if (id == null || id.isBlank()) continue;
                    Set<Integer> stages = new HashSet<>();
                    if (rs.getInt("stage1_unlocked") == 1) stages.add(1);
                    if (rs.getInt("stage2_unlocked") == 1) stages.add(2);
                    if (rs.getInt("stage3_unlocked") == 1) stages.add(3);
                    if (!stages.isEmpty()) result.put(id, stages);
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
        String sql = "SELECT pokemon_id, xp FROM pokemon_progress ORDER BY pokemon_id";

        try (Connection conn = new DatabaseCore().getConnection()) {
            ensureSchema(conn);

            Map<String, Integer> result = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("pokemon_id");
                    if (id != null && !id.isBlank()) {
                        result.put(id, Math.max(0, rs.getInt("xp")));
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            throw new StorageException("讀取寶可夢 XP 失敗", e);
        }
    }

    /**
     * 儲存所有寶可夢 XP（只更新 xp 欄位，保留其他欄位）。
     */
    public static void savePokemonXp(Map<String, Integer> pokemonXpMap) {
        String sql = "INSERT INTO pokemon_progress(pokemon_id, xp) VALUES (?, ?) "
                   + "ON CONFLICT(pokemon_id) DO UPDATE SET xp = excluded.xp";

        try (Connection conn = new DatabaseCore().getConnection()) {
            ensureSchema(conn);

            if (pokemonXpMap == null || pokemonXpMap.isEmpty()) {
                return;
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map.Entry<String, Integer> entry : pokemonXpMap.entrySet()) {
                    String id = entry.getKey();
                    if (id == null || id.isBlank()) continue;
                    int xp = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
                    ps.setString(1, id.trim());
                    ps.setInt(2, xp);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            throw new StorageException("儲存寶可夢 XP 失敗", e);
        }
    }

    /**
     * 儲存單一解鎖關卡（重複會自動忽略）。
     */
    public static void saveUnlockedStage(String pokemonId, int stage) {
        if (pokemonId == null || pokemonId.isBlank()) {
            throw new IllegalArgumentException("pokemonId cannot be blank");
        }
        // col is derived from a switch on a 1-3 int — safe to interpolate
        String col = switch (stage) {
            case 1 -> "stage1_unlocked";
            case 2 -> "stage2_unlocked";
            case 3 -> "stage3_unlocked";
            default -> throw new IllegalArgumentException("Stage must be 1-3: " + stage);
        };

        String sql = "INSERT INTO pokemon_progress(pokemon_id, " + col + ") VALUES(?, 1) "
                   + "ON CONFLICT(pokemon_id) DO UPDATE SET " + col + " = 1";

        try (Connection conn = new DatabaseCore().getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, pokemonId.trim());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StorageException("儲存解鎖關卡失敗", e);
        }
    }

    /**
     * 載入每隻寶可夢目前選擇的顯示 stage。
     */
    public static Map<String, Integer> loadSelectedStages() {
        String sql = "SELECT pokemon_id, selected_stage FROM pokemon_progress";
        Map<String, Integer> map = new HashMap<>();
        try (Connection conn = new DatabaseCore().getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("pokemon_id"), rs.getInt("selected_stage"));
                }
            }
        } catch (SQLException e) {
            System.err.println("讀取選擇 stage 失敗: " + e.getMessage());
        }
        return map;
    }

    /**
     * 儲存寶可夢目前選擇的顯示 stage。
     */
    public static void saveSelectedStage(String pokemonId, int stage) {
        String sql = "INSERT INTO pokemon_progress(pokemon_id, selected_stage) VALUES (?, ?) "
                   + "ON CONFLICT(pokemon_id) DO UPDATE SET selected_stage = excluded.selected_stage";
        try (Connection conn = new DatabaseCore().getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, pokemonId);
                ps.setInt(2, stage);
                ps.executeUpdate();
            }
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

    private static void ensureSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        coins INTEGER NOT NULL DEFAULT 0,
                        stones INTEGER NOT NULL DEFAULT 0,
                        xp INTEGER NOT NULL DEFAULT 0,
                        partner_id TEXT NOT NULL DEFAULT '004'
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS pokemon_progress (
                        pokemon_id      TEXT    PRIMARY KEY,
                        xp              INTEGER NOT NULL DEFAULT 0,
                        stage1_unlocked INTEGER NOT NULL DEFAULT 0,
                        stage2_unlocked INTEGER NOT NULL DEFAULT 0,
                        stage3_unlocked INTEGER NOT NULL DEFAULT 0,
                        selected_stage  INTEGER NOT NULL DEFAULT 1
                    )
                    """);
            stmt.execute(
                    "INSERT OR IGNORE INTO player_stats(id, coins, stones, xp, partner_id) VALUES (1, 0, 0, 0, '004')");
        }
        ensureDefaultProgress(conn);
    }

    private static void ensureDefaultProgress(Connection conn) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM pokemon_progress WHERE stage1_unlocked = 1";
        try (PreparedStatement ps = conn.prepareStatement(countSql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getInt(1) > 0) return;
        }

        String insertSql = "INSERT OR IGNORE INTO pokemon_progress(pokemon_id, stage1_unlocked) VALUES (?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            for (String stageKey : DEFAULT_UNLOCKED_STAGES) {
                int sep = stageKey.lastIndexOf('_');
                ps.setString(1, stageKey.substring(0, sep));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
