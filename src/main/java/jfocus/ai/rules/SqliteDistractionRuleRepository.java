package jfocus.ai.rules;

import jfocus.db.DatabaseCore;

/**
 * Backward-compatible alias for the SQLite rule repository.
 */
public class SqliteDistractionRuleRepository extends JdbcDistractionRuleRepository {

    public SqliteDistractionRuleRepository(DatabaseCore databaseCore) {
        super(databaseCore);
    }
}
