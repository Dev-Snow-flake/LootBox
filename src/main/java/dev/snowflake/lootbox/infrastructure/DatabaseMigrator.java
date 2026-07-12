package dev.snowflake.lootbox.infrastructure;

import dev.snowflake.lootbox.storage.ConnectionProvider;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

public final class DatabaseMigrator {
    private DatabaseMigrator() {
    }

    public static void migrate(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .table("flyway_lootbox_schema_history")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    public static void migrate(ConnectionProvider connectionProvider) {
        migrate(new ConnectionProviderDataSource(connectionProvider));
    }
}

