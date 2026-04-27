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
    private static final String DB_AUTO_INIT_PROPERTY = "jfocus.db.auto-init";
    private static final String DB_AUTO_INIT_ENV = "JFOCUS_DB_AUTO_INIT";

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
     * 取得是否啟用 repository 建構時自動初始化資料庫。
     *
     * 預設為 true，可透過 jfocus.db.auto-init 或 JFOCUS_DB_AUTO_INIT 覆蓋。
     */
    public static boolean isAutoInitializeEnabled() {
        String configured = System.getProperty(DB_AUTO_INIT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(DB_AUTO_INIT_ENV);
        }

        if (configured == null || configured.isBlank()) {
            return true;
        }

        return parseBoolean(configured.trim(), true);
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
                xp INTEGER NOT NULL DEFAULT 0
            );
            """;

        String unlockedStagesTableSql = """
            CREATE TABLE IF NOT EXISTS unlocked_stages (
                stage_key TEXT PRIMARY KEY
            );
            """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute(activityTableSql);
            stmt.execute(playerStatsTableSql);
            stmt.execute(unlockedStagesTableSql);
            stmt.execute("INSERT OR IGNORE INTO player_stats(id, coins, stones, xp) VALUES (1, 0, 0, 0)");
            ensureColumnExists(conn, "activities", "end_time", "TEXT");
            ensureColumnExists(conn, "activities", "duration", "INTEGER");
            ensureColumnExists(conn, "activities", "session_id", "TEXT");
            ensureIndexes(stmt);
            System.out.println("✅ DatabaseCore: SQLite 資料庫與資料表已就緒！");
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

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("1")
                || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("on")) {
            return true;
        }
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("0")
                || value.equalsIgnoreCase("no") || value.equalsIgnoreCase("off")) {
            return false;
        }
        return defaultValue;
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
    }
}
