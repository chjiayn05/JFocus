package jfocus.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 管理 SQLite 連線與資料表初始化。
 */
public class DatabaseCore {
    private static final String DB_URL_PROPERTY = "jfocus.db.url";
    private static final String DB_URL_ENV = "JFOCUS_DB_URL";

    private final String url;

    /**
     * 使用預設設定建立資料庫核心。
     */
    public DatabaseCore() {
        this(resolveDefaultUrl());
    }

    /**
     * 使用指定 JDBC URL 建立資料庫核心。
     *
     * @param url JDBC URL
     */
    public DatabaseCore(String url) {
        this.url = Objects.requireNonNull(url, "url cannot be null");
    }

    /**
     * 建立資料庫連線。
     *
     * @return SQLite 連線
     * @throws SQLException 當連線建立失敗時拋出
     */
    public Connection getConnection() throws SQLException {
        ensureDatabaseDirectory();
        return DriverManager.getConnection(url);
    }

    /**
     * 建立系統所需的資料表，並補齊既有資料庫缺少的欄位。
     */
    public static void initializeDatabase() {
        new DatabaseCore().initialize();
    }

    /**
     * 建立系統所需的資料表，並補齊既有資料庫缺少的欄位。
     */
    public void initialize() {
        String activityTableSql = """
            CREATE TABLE IF NOT EXISTS activities (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                app_name TEXT,
                window_title TEXT,
                start_time TEXT,
                end_time TEXT,
                duration INTEGER,
                is_focus INTEGER,
                session_id TEXT
            );
            """;

       String playerStatsTableSql = """
            CREATE TABLE IF NOT EXISTS player_stats (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                coins INTEGER NOT NULL DEFAULT 0,
                stones INTEGER NOT NULL DEFAULT 0,
                xp INTEGER NOT NULL DEFAULT 0,
                partner_id TEXT NOT NULL DEFAULT '004'
            );
            """;

        String unlockedStagesTableSql = """
            CREATE TABLE IF NOT EXISTS unlocked_stages (
                stage_key TEXT PRIMARY KEY
            );
            """;

        String distractionRulesTableSql = """
            CREATE TABLE IF NOT EXISTS distraction_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                list_type TEXT NOT NULL,
                keyword TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                UNIQUE(list_type, keyword)
            );
            """;

        String appSettingsTableSql = """
            CREATE TABLE IF NOT EXISTS app_settings (
                setting_key TEXT PRIMARY KEY,
                setting_value TEXT NOT NULL,
                updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            );
            """;

        String todosTableSql = """
            CREATE TABLE IF NOT EXISTS todos (
                id       INTEGER PRIMARY KEY AUTOINCREMENT,
                task     TEXT    NOT NULL,
                deadline TEXT,
                is_done  INTEGER NOT NULL DEFAULT 0,
                notes    TEXT
            );
            """;

        String focusSessionsTableSql = """
            CREATE TABLE IF NOT EXISTS focus_sessions (
                session_id TEXT PRIMARY KEY,
                subject TEXT NOT NULL,
                start_time TEXT NOT NULL,
                end_time TEXT,
                expected_duration_seconds INTEGER,
                actual_duration_seconds INTEGER,
                total_idle_seconds_deducted INTEGER NOT NULL DEFAULT 0
            );
            """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute(activityTableSql);
            stmt.execute(playerStatsTableSql);
            stmt.execute(unlockedStagesTableSql);
            stmt.execute(distractionRulesTableSql);
            stmt.execute(appSettingsTableSql);
            stmt.execute(todosTableSql);
            stmt.execute(focusSessionsTableSql);

            // 若舊資料庫缺少 partner_id 欄位，初始化時補齊。
            ensureColumnExists(conn, "player_stats", "partner_id", "TEXT NOT NULL DEFAULT '004'");
            stmt.execute("INSERT OR IGNORE INTO player_stats(id, coins, stones, xp, partner_id) VALUES (1, 0, 0, 0, '004')");
            ensureColumnExists(conn, "activities", "end_time", "TEXT");
            ensureColumnExists(conn, "activities", "duration", "INTEGER");
            ensureColumnExists(conn, "activities", "session_id", "TEXT");
            ensureKeywordOnlyDistractionRulesTable(conn);
            ensureIndexes(stmt);
            System.out.println("DatabaseCore: SQLite 資料庫與資料表已就緒！");
        } catch (SQLException e) {
            throw new StorageException("初始化資料庫失敗", e);
        }
    }

    /**
     * 取得目前使用的 JDBC URL。
     *
     * @return JDBC URL
     */
    public String getUrl() {
        return url;
    }

    private static String resolveDefaultUrl() {
        String configuredUrl = System.getProperty(DB_URL_PROPERTY);
        if (configuredUrl == null || configuredUrl.isBlank()) {
            configuredUrl = System.getenv(DB_URL_ENV);
        }

        if (configuredUrl != null && !configuredUrl.isBlank()) {
            return configuredUrl;
        }

        return "jdbc:sqlite:" + AppPaths.getDatabasePath();
    }
    
    private void ensureDatabaseDirectory() {
        if (!url.startsWith("jdbc:sqlite:")) {
            return;
        }

        String dbPath = url.substring("jdbc:sqlite:".length());
        if (dbPath.isBlank() || dbPath.equals(":memory:") || dbPath.startsWith("file:")) {
            return;
        }

        Path parent = Path.of(dbPath).toAbsolutePath().normalize().getParent();
        if (parent == null) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new StorageException("建立資料庫目錄失敗", e);
        }
    }

    private void ensureColumnExists(Connection conn, String tableName, String columnName, String columnType) throws SQLException {
        Set<String> columns = new HashSet<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }

        if (columns.contains(columnName)) {
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType);
        }
    }

    private void ensureIndexes(Statement stmt) throws SQLException {
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_activities_start_time ON activities(start_time)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_activities_end_time ON activities(end_time)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_activities_session_id ON activities(session_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_distraction_rules_type ON distraction_rules(list_type)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_distraction_rules_type_keyword ON distraction_rules(list_type, keyword)");
    }

    private void ensureKeywordOnlyDistractionRulesTable(Connection conn) throws SQLException {
        Set<String> columns = getColumns(conn, "distraction_rules");
        if (columns.contains("keyword")
                && !columns.contains("app_name")
                && !columns.contains("window_title")) {
            return;
        }

        String migrationSql = """
            CREATE TABLE IF NOT EXISTS distraction_rules_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                list_type TEXT NOT NULL,
                keyword TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                UNIQUE(list_type, keyword)
            )
            """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(migrationSql);
            if (columns.contains("keyword")) {
                stmt.executeUpdate("""
                    INSERT OR IGNORE INTO distraction_rules_new(list_type, keyword, created_at)
                    SELECT list_type, trim(keyword), created_at
                    FROM distraction_rules
                    WHERE trim(keyword) <> ''
                    """);
            } else if (columns.contains("app_name") || columns.contains("window_title")) {
                stmt.executeUpdate("""
                    INSERT OR IGNORE INTO distraction_rules_new(list_type, keyword, created_at)
                    SELECT list_type,
                           trim(
                               CASE
                                   WHEN app_name <> '' AND window_title <> '' THEN app_name || ' ' || window_title
                                   WHEN app_name <> '' THEN app_name
                                   ELSE window_title
                               END
                           ),
                           created_at
                    FROM distraction_rules
                    WHERE trim(app_name) <> '' OR trim(window_title) <> ''
                    """);
            }
            stmt.execute("DROP TABLE distraction_rules");
            stmt.execute("ALTER TABLE distraction_rules_new RENAME TO distraction_rules");
        }
    }

    private Set<String> getColumns(Connection conn, String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }

        return columns;
    }
}
