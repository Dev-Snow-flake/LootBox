package dev.snowflake.lootbox.command;

import dev.snowflake.lootbox.definition.LootBoxDefinition;
import dev.snowflake.lootbox.definition.LootBoxRegistry;
import dev.snowflake.lootbox.gui.ProbabilityGui;
import dev.snowflake.lootbox.history.HistoryRecord;
import dev.snowflake.lootbox.item.LootBoxItemFactory;
import dev.snowflake.lootbox.open.MailBridge;
import dev.snowflake.lootbox.pool.ProbabilityCalculator;
import dev.snowflake.lootbox.storage.LootBoxRepository;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class LootBoxCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter HISTORY_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final Plugin plugin;
    private final LootBoxRegistry registry;
    private final LootBoxRepository repository;
    private final LootBoxItemFactory itemFactory;
    private final ProbabilityGui gui;
    private final MailBridge mailBridge;
    private final Executor databaseExecutor;
    private final Supplier<Boolean> reload;
    private final Consumer<String> warnings;

    public LootBoxCommand(
            Plugin plugin,
            LootBoxRegistry registry,
            LootBoxRepository repository,
            LootBoxItemFactory itemFactory,
            ProbabilityGui gui,
            MailBridge mailBridge,
            Executor databaseExecutor,
            Supplier<Boolean> reload,
            Consumer<String> warnings
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
        this.gui = Objects.requireNonNull(gui, "gui");
        this.mailBridge = Objects.requireNonNull(mailBridge, "mailBridge");
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
        this.reload = Objects.requireNonNull(reload, "reload");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("/lootbox list");
                return true;
            }
            gui.openGallery(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "chance", "probability", "rates" -> chance(sender, args);
            case "list" -> list(sender);
            case "give" -> give(sender, args);
            case "createpool", "editpool" -> createPool(sender, args);
            case "viewpool" -> viewPool(sender, args);
            case "history" -> history(sender, args);
            case "reload" -> reload(sender);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /lootbox list.");
                yield true;
            }
        };
    }

    private boolean chance(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open the chance GUI.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /lootbox chance <box_id>");
            return true;
        }
        gui.openChance(player, args[1]);
        return true;
    }

    private boolean list(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "LootBoxes:");
        for (LootBoxDefinition definition : registry.definitions()) {
            int rewardCount = registry.pool(definition.poolId()).size();
            sender.sendMessage(ChatColor.YELLOW + "- " + definition.id()
                    + ChatColor.GRAY + " pool=" + definition.poolId()
                    + " rewards=" + rewardCount);
        }
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lootbox.admin")) {
            sender.sendMessage(ChatColor.RED + "Missing permission: lootbox.admin");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /lootbox give <player> <box_id> [amount]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player must be online to receive LootBox items.");
            return true;
        }
        LootBoxDefinition definition = registry.definition(args[2]).orElse(null);
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "Unknown LootBox: " + args[2]);
            return true;
        }
        int amount = args.length >= 4 ? parseAmount(args[3]) : 1;
        if (amount < 1 || amount > 64) {
            sender.sendMessage(ChatColor.RED + "Amount must be between 1 and 64.");
            return true;
        }
        databaseExecutor.execute(() -> issueBoxes(sender, target, definition, amount));
        sender.sendMessage(ChatColor.GRAY + "Issuing " + amount + " LootBox item(s)...");
        return true;
    }

    private void issueBoxes(CommandSender sender, Player target, LootBoxDefinition definition, int amount) {
        List<UUID> issued = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            UUID serial = UUID.randomUUID();
            try {
                repository.issueBox(serial, definition.id(), target.getUniqueId());
                issued.add(serial);
            } catch (SQLException exception) {
                warnings.accept("Failed to issue lootbox " + definition.id() + ": " + exception.getMessage());
                break;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> deliverIssuedBoxes(sender, target, definition, issued, amount));
    }

    private void deliverIssuedBoxes(
            CommandSender sender,
            Player target,
            LootBoxDefinition definition,
            List<UUID> serials,
            int requested
    ) {
        int delivered = 0;
        for (UUID serial : serials) {
            ItemStack item = itemFactory.create(definition, serial);
            boolean mailed = false;
            if (target.getInventory().firstEmpty() == -1) {
                mailed = mailBridge.send(
                        target.getUniqueId(),
                        item,
                        "LootBox item: " + definition.displayName(),
                        7);
            }
            if (mailed) {
                target.sendMessage(ChatColor.YELLOW + "A LootBox was sent to your mail.");
                delivered++;
                continue;
            }
            var leftovers = target.getInventory().addItem(item);
            if (leftovers.isEmpty()) {
                delivered++;
            } else {
                leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
                delivered++;
            }
        }
        sender.sendMessage(ChatColor.GREEN
                + "Issued " + delivered + "/" + requested + " LootBox item(s) to " + target.getName() + ".");
    }

    private boolean createPool(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lootbox.admin")) {
            sender.sendMessage(ChatColor.RED + "Missing permission: lootbox.admin");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can edit pools.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /lootbox createpool <pool_id>");
            return true;
        }
        gui.openAdminPool(player, args[1]);
        return true;
    }

    private boolean viewPool(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lootbox.admin")) {
            sender.sendMessage(ChatColor.RED + "Missing permission: lootbox.admin");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /lootbox viewpool <pool_id>");
            return true;
        }
        String poolId = args[1];
        var views = new ProbabilityCalculator().views(registry.pool(poolId));
        sender.sendMessage(ChatColor.GOLD + "Pool " + poolId + ":");
        for (var view : views) {
            sender.sendMessage(ChatColor.YELLOW + "- " + view.entry().tier().name()
                    + ChatColor.GRAY + " weight=" + view.entry().weight()
                    + " amount=" + view.entry().amountMin() + "~" + view.entry().amountMax()
                    + " chance=" + String.format(Locale.ROOT, "%.3f%%", view.percent()));
        }
        if (views.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No enabled entries.");
        }
        return true;
    }

    private boolean history(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lootbox.admin")) {
            sender.sendMessage(ChatColor.RED + "Missing permission: lootbox.admin");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /lootbox history <player> [page]");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        int page = args.length >= 3 ? Math.max(1, parseAmount(args[2])) : 1;
        int offset = (page - 1) * 10;
        databaseExecutor.execute(() -> {
            try {
                List<HistoryRecord> records = repository.findHistory(target.getUniqueId(), 10, offset);
                Bukkit.getScheduler().runTask(plugin, () -> showHistory(sender, target, page, records));
            } catch (SQLException exception) {
                warnings.accept("Failed to load lootbox history: " + exception.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(ChatColor.RED
                        + "Failed to load history. Check console."));
            }
        });
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("lootbox.admin")) {
            sender.sendMessage(ChatColor.RED + "Missing permission: lootbox.admin");
            return true;
        }
        boolean ok = reload.get();
        sender.sendMessage(ok
                ? ChatColor.GREEN + "LootBox reloaded."
                : ChatColor.RED + "LootBox reload failed. Check console.");
        return true;
    }

    private static void showHistory(
            CommandSender sender,
            OfflinePlayer target,
            int page,
            List<HistoryRecord> records
    ) {
        sender.sendMessage(ChatColor.GOLD + "LootBox history for " + target.getName() + " page " + page + ":");
        for (HistoryRecord record : records) {
            sender.sendMessage(ChatColor.YELLOW + "- " + HISTORY_TIME.format(record.createdAt())
                    + ChatColor.GRAY + " " + record.boxId()
                    + " -> " + record.tier().name()
                    + " x" + record.amount()
                    + " via " + record.deliveredVia()
                    + " [" + record.status() + "]");
        }
        if (records.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No records.");
        }
    }

    private static int parseAmount(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("chance", "list", "give", "viewpool"));
            if (sender.hasPermission("lootbox.admin")) {
                base.addAll(List.of("createpool", "history", "reload"));
            }
            return matching(base, args[0]);
        }
        if (args.length == 2 && List.of("chance", "give").contains(args[0].toLowerCase(Locale.ROOT))) {
            if (args[0].equalsIgnoreCase("give")) {
                return null;
            }
            return matching(registry.definitions().stream().map(LootBoxDefinition::id).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return matching(registry.definitions().stream().map(LootBoxDefinition::id).toList(), args[2]);
        }
        return List.of();
    }

    private static List<String> matching(List<String> values, String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowered))
                .toList();
    }
}

