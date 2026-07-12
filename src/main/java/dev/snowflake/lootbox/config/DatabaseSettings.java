package dev.snowflake.lootbox.config;

public record DatabaseSettings(
        String mode,
        String jdbcUrl,
        String username,
        String password,
        int maximumPoolSize,
        long connectionTimeoutMs,
        boolean migrate
) {
    public DatabaseSettings {
        mode = mode == null || mode.isBlank() ? "standalone" : mode.trim().toLowerCase();
        if (!mode.equals("standalone")) {
            throw new IllegalArgumentException("database.mode must be standalone");
        }
        jdbcUrl = jdbcUrl == null ? "" : jdbcUrl.trim();
        username = username == null ? "" : username;
        password = password == null ? "" : password;
        if (maximumPoolSize < 1 || maximumPoolSize > 32) {
            throw new IllegalArgumentException("database.maximum_pool_size must be between 1 and 32");
        }
        if (connectionTimeoutMs < 1000L) {
            throw new IllegalArgumentException("database.connection_timeout_ms must be at least 1000");
        }
        if (jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("database.jdbc_url is required in standalone mode");
        }
    }
}

