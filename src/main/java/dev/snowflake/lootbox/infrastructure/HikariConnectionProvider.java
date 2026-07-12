package dev.snowflake.lootbox.infrastructure;

import dev.snowflake.lootbox.storage.ConnectionProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class HikariConnectionProvider implements ConnectionProvider, AutoCloseable {
    private final HikariDataSource dataSource;

    public HikariConnectionProvider(
            String jdbcUrl,
            String username,
            String password,
            int maximumPoolSize,
            long connectionTimeoutMs
    ) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("LootBox");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(connectionTimeoutMs);
        config.setValidationTimeout(Math.min(connectionTimeoutMs, 3000L));
        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public HikariDataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}

