package dev.snowflake.lootbox.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class DatabaseSettingsTest {
    @Test
    void acceptsStandaloneSettings() {
        DatabaseSettings settings = new DatabaseSettings(
                "standalone",
                "jdbc:postgresql://localhost/lootbox",
                "lootbox",
                "secret",
                4,
                5000L,
                true);

        assertThat(settings.mode()).isEqualTo("standalone");
        assertThat(settings.maximumPoolSize()).isEqualTo(4);
    }

    @Test
    void rejectsUnknownMode() {
        assertThatThrownBy(() -> new DatabaseSettings("sqlite", "", "", "", 1, 1000L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("standalone");
    }

    @Test
    void rejectsStandaloneWithoutJdbcUrl() {
        assertThatThrownBy(() -> new DatabaseSettings("standalone", "", "", "", 1, 1000L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbc_url");
    }
}

