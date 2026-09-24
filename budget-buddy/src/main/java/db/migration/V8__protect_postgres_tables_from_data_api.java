package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Supabase's public Data API must not bypass Spring's user-ownership checks. */
public class V8__protect_postgres_tables_from_data_api extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        if (!context.getConnection().getMetaData().getDatabaseProductName().equals("PostgreSQL")) return;
        try (var statement = context.getConnection().createStatement()) {
            for (String table : new String[]{"users", "categories", "transactions", "ai_reports", "ai_usage"}) {
                statement.execute("ALTER TABLE " + table + " ENABLE ROW LEVEL SECURITY");
            }
        }
        // No public policies: only the table owner/backend role may use these tables.
    }
}
