package dev.snowflake.lootbox;

import dev.snowflake.lootbox.api.LootBoxAPI;
import dev.snowflake.lootbox.command.LootBoxCommand;
import dev.snowflake.lootbox.config.DatabaseSettings;
import dev.snowflake.lootbox.config.LootBoxConfigLoader;
import dev.snowflake.lootbox.config.LootBoxSettings;
import dev.snowflake.lootbox.definition.LootBoxDefinition;
import dev.snowflake.lootbox.definition.LootBoxRegistry;
import dev.snowflake.lootbox.gui.PoolEditorListener;
import dev.snowflake.lootbox.gui.ProbabilityGui;
import dev.snowflake.lootbox.infrastructure.DatabaseMigrator;
import dev.snowflake.lootbox.infrastructure.HikariConnectionProvider;
import dev.snowflake.lootbox.item.LootBoxItemFactory;
import dev.snowflake.lootbox.listener.LootBoxListener;
import dev.snowflake.lootbox.open.MailBridge;
import dev.snowflake.lootbox.open.OpenService;
import dev.snowflake.lootbox.open.RewardDispatcher;
import dev.snowflake.lootbox.placeholder.LootBoxPlaceholderExpansion;
import dev.snowflake.lootbox.placeholder.StatsCache;
import dev.snowflake.lootbox.pool.ProbabilityCalculator;
import dev.snowflake.lootbox.storage.ConnectionProvider;
import dev.snowflake.lootbox.storage.JdbcLootBoxRepository;
import dev.snowflake.lootbox.storage.LootBoxRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class LootBoxPlugin extends JavaPlugin implements LootBoxAPI {
    private final LootBoxRegistry registry = new LootBoxRegistry();
    private final AtomicReference<LootBoxSettings> settings = new AtomicReference<>();
    private ExecutorService databaseExecutor;
    private AutoCloseable ownedConnectionResource;
    private LootBoxRepository repository;
    private LootBoxItemFactory itemFactory;
    private MailBridge mailBridge;
    private StatsCache statsCache;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            LootBoxSettings loaded = LootBoxConfigLoader.load(getConfig());
            settings.set(loaded);

            ConnectionProvider connectionProvider = createConnectionProvider(loaded.database());
            if (loaded.database().migrate()) {
                if (connectionProvider instanceof HikariConnectionProvider hikari) {
                    DatabaseMigrator.migrate(hikari.dataSource());
                } else {
                    DatabaseMigrator.migrate(connectionProvider);
                }
            }

            repository = new JdbcLootBoxRepository(connectionProvider);
            reloadRegistryFromStorage(loaded);

            databaseExecutor = createDatabaseExecutor(loaded.database().maximumPoolSize());
            itemFactory = new LootBoxItemFactory(this, loaded.hmacSecret());
            mailBridge = new MailBridge(getServer(), loaded.fallbackToMail());
            ProbabilityCalculator probability = new ProbabilityCalculator();
            RewardDispatcher dispatcher = new RewardDispatcher(mailBridge, loaded.mailExpireDays());
            OpenService openService = new OpenService(
                    this,
                    registry,
                    itemFactory,
                    repository,
                    probability,
                    dispatcher,
                    getLogger(),
                    loaded.announceMythicToServer(),
                    loaded.titleEnabled(),
                    loaded.particleEnabled(),
                    loaded.soundEnabled());
            ProbabilityGui gui = new ProbabilityGui(registry, probability);

            LootBoxCommand commandHandler = new LootBoxCommand(
                    this,
                    registry,
                    repository,
                    itemFactory,
                    gui,
                    mailBridge,
                    databaseExecutor,
                    this::reloadLootBox,
                    message -> getLogger().warning(message));
            PluginCommand command = Objects.requireNonNull(getCommand("lootbox"), "lootbox command missing");
            command.setExecutor(commandHandler);
            command.setTabCompleter(commandHandler);

            getServer().getPluginManager().registerEvents(
                    new LootBoxListener(
                            this,
                            itemFactory,
                            openService,
                            databaseExecutor,
                            loaded.clickCooldown(),
                            Clock.systemUTC(),
                            message -> getLogger().warning(message)),
                    this);
            getServer().getPluginManager().registerEvents(
                    new PoolEditorListener(
                            this,
                            repository,
                            registry,
                            databaseExecutor,
                            () -> reloadRegistryFromStorage(settings.get()),
                            message -> getLogger().warning(message)),
                    this);

            statsCache = new StatsCache(this, repository, databaseExecutor, message -> getLogger().warning(message));
            statsCache.start();
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                if (!new LootBoxPlaceholderExpansion(this, statsCache).register()) {
                    getLogger().warning("PlaceholderAPI expansion registration returned false");
                }
            }
            getServer().getServicesManager().register(LootBoxAPI.class, this, this, ServicePriority.Normal);
            getLogger().info("LootBox enabled with " + registry.definitions().size() + " box definitions");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE,
                    "LootBox failed closed during startup: " + rootMessage(exception), exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (statsCache != null) {
            statsCache.stop();
        }
        if (databaseExecutor != null) {
            databaseExecutor.shutdown();
            try {
                if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    databaseExecutor.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                databaseExecutor.shutdownNow();
            }
        }
        if (ownedConnectionResource != null) {
            try {
                ownedConnectionResource.close();
            } catch (Exception exception) {
                getLogger().warning("Failed to close LootBox database pool: " + exception.getMessage());
            }
        }
    }

    @Override
    public List<LootBoxDefinition> definitions() {
        return registry.definitions();
    }

    @Override
    public boolean issue(Player player, String boxId, int amount) {
        LootBoxDefinition definition = registry.definition(boxId).orElse(null);
        if (definition == null || repository == null || itemFactory == null) {
            return false;
        }
        int count = Math.max(1, amount);
        for (int i = 0; i < count; i++) {
            UUID serial = UUID.randomUUID();
            try {
                repository.issueBox(serial, definition.id(), player.getUniqueId());
                ItemStack item = itemFactory.create(definition, serial);
                player.getInventory().addItem(item);
            } catch (SQLException exception) {
                getLogger().warning("Failed to issue LootBox through API: " + exception.getMessage());
                return false;
            }
        }
        return true;
    }

    private ConnectionProvider createConnectionProvider(DatabaseSettings database) {
        HikariConnectionProvider hikari = new HikariConnectionProvider(
                database.jdbcUrl(),
                database.username(),
                database.password(),
                database.maximumPoolSize(),
                database.connectionTimeoutMs());
        ownedConnectionResource = hikari;
        return hikari;
    }

    private ExecutorService createDatabaseExecutor(int maximumPoolSize) {
        int threads = Math.max(1, Math.min(4, maximumPoolSize));
        AtomicInteger threadNumber = new AtomicInteger();
        return Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "lootbox-db-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignored, error) ->
                    getLogger().severe("Uncaught LootBox DB task failure: " + error.getMessage()));
            return thread;
        });
    }

    private boolean reloadLootBox() {
        try {
            reloadConfig();
            LootBoxSettings loaded = LootBoxConfigLoader.load(getConfig());
            LootBoxSettings current = settings.get();
            if (!loaded.database().equals(current.database())) {
                getLogger().warning("database settings changed; restart required");
                return false;
            }
            settings.set(loaded);
            reloadRegistryFromStorage(loaded);
            return true;
        } catch (RuntimeException exception) {
            getLogger().log(Level.WARNING, "LootBox reload failed: " + rootMessage(exception), exception);
            return false;
        }
    }

    private void reloadRegistryFromStorage(LootBoxSettings loaded) {
        try {
            registry.reload(loaded.definitions(), loaded.configPools(), repository.loadAdminPoolEntries());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load LootBox admin pools", exception);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}

