package dev.snowflake.lootbox.placeholder;

import dev.snowflake.lootbox.history.StatsSnapshot;
import dev.snowflake.lootbox.storage.LootBoxRepository;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.Plugin;

public final class StatsCache {
    private final Plugin plugin;
    private final LootBoxRepository repository;
    private final Executor databaseExecutor;
    private final Consumer<String> warnings;
    private final AtomicReference<StatsSnapshot> snapshot = new AtomicReference<>(StatsSnapshot.EMPTY);
    private BukkitTask task;

    public StatsCache(
            Plugin plugin,
            LootBoxRepository repository,
            Executor databaseExecutor,
            Consumer<String> warnings
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    public void start() {
        refresh();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 20L * 30L, 20L * 60L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    public StatsSnapshot snapshot() {
        return snapshot.get();
    }

    public void refresh() {
        databaseExecutor.execute(() -> {
            try {
                snapshot.set(repository.loadStatsSnapshot());
            } catch (SQLException exception) {
                warnings.accept("Failed to refresh LootBox placeholders: " + exception.getMessage());
            }
        });
    }
}

