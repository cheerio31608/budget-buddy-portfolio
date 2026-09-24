package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Explicit IDs in the original demo seed did not advance PostgreSQL identity sequences. */
public class V7__align_identity_sequences extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        for (String table : new String[]{"users", "categories"}) {
            String id = table.equals("users") ? "user_id" : "category_id";
            try (var statement = context.getConnection().createStatement();
                 var result = statement.executeQuery("SELECT COALESCE(MAX(" + id + "), 0) + 1 FROM " + table)) {
                result.next();
                long next = result.getLong(1);
                try (var alter = context.getConnection().createStatement()) {
                    alter.execute("ALTER TABLE " + table + " ALTER COLUMN " + id + " RESTART WITH " + next);
                }
            }
        }
    }
}
