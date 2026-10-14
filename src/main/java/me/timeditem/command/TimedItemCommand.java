package me.timeditem.command;

import me.timeditem.TimedItem;
import me.timeditem.check.CheckManager;
import me.timeditem.config.ConfigManager;
import me.timeditem.item.ItemManager;
import me.timeditem.util.ColorUtil;
import me.timeditem.util.DateParser;
import me.timeditem.util.TimeParser;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.ParseException;
import java.util.*;

public class TimedItemCommand implements CommandExecutor, TabCompleter {
    private final TimedItem plugin;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final CheckManager checkManager;

    private static class HelpEntry {
        final String permission;
        final String text;

        HelpEntry(String permission, String text) {
            this.permission = permission;
            this.text = text;
        }
    }

    private final List<HelpEntry> helpEntries = new ArrayList<>();

    public TimedItemCommand(TimedItem plugin, ConfigManager configManager, ItemManager itemManager, CheckManager checkManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemManager = itemManager;
        this.checkManager = checkManager;
        initHelpEntries();
    }

    public void initHelpEntries() {
        helpEntries.clear();
        helpEntries.add(new HelpEntry("timeditem.command.add",
                configManager.getHelpCommandMessage("add", "&6/ti add [time] &7- Thêm thời hạn cho vật phẩm trên tay.")));
        helpEntries.add(new HelpEntry("timeditem.command.remove",
                configManager.getHelpCommandMessage("remove", "&6/ti remove [time] &7- Xoá bớt thời hạn của vật phẩm trên tay.")));
        helpEntries.add(new HelpEntry("timeditem.command.set",
                configManager.getHelpCommandMessage("set", "&6/ti set <format> &7- Đặt thời hạn cho vật phẩm.")));
        helpEntries.add(new HelpEntry("timeditem.command.clear",
                configManager.getHelpCommandMessage("clear", "&6/ti clear &7- Xoá thời hạn khỏi vật phẩm.")));
        helpEntries.add(new HelpEntry("timeditem.command.check",
                configManager.getHelpCommandMessage("check", "&6/ti check &7- Kiểm tra thời hạn vật phẩm trên tay.")));
        helpEntries.add(new HelpEntry("timeditem.command.givemmo",
                configManager.getHelpCommandMessage("givemmo", "&6/ti givemmo <player> <type> <id> <time> &7- Tặng item MMOItems có thời hạn.")));
        helpEntries.add(new HelpEntry("timeditem.command.token",
                configManager.getHelpCommandMessage("token", "&6/ti token <time> [amount] [player] &7- Tạo/tặng Phiếu Gia Hạn thời gian.")));
        helpEntries.add(new HelpEntry("timeditem.command.reload",
                configManager.getHelpCommandMessage("reload", "&6/ti reload &7- Nạp lại plugin và ngôn ngữ.")));
        helpEntries.add(new HelpEntry("timeditem.command.help",
                configManager.getHelpCommandMessage("help", "&6/ti help <page> &7- Xem hướng dẫn sử dụng.")));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return handleHelp(sender, 1);
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "add" -> {
                return handleAdd(sender, args);
            }
            case "remove" -> {
                return handleRemove(sender, args);
            }
            case "set" -> {
                return handleSet(sender, args);
            }
            case "clear" -> {
                return handleClear(sender);
            }
            case "check" -> {
                return handleCheck(sender);
            }
            case "givemmo" -> {
                return handleGiveMMO(sender, args);
            }
            case "token" -> {
                return handleToken(sender, args);
            }
            case "reload" -> {
                return handleReload(sender);
            }
            case "help" -> {
                int page = 1;
                if (args.length >= 2) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                return handleHelp(sender, page);
            }
            default -> {
                sender.sendMessage(configManager.getMessage("usage").replace("{usage}", "/ti <add|remove|set|clear|check|givemmo|token|reload|help>"));
                return true;
            }
        }
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("timeditem.command.add")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("player-only"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(configManager.getMessage("usage").replace("{usage}", "/ti add [time] (ex: /ti add 1d2h)"));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            player.sendMessage(configManager.getMessage("no-item-in-hand"));
            return true;
        }

        long duration;
        try {
            duration = TimeParser.parseDuration(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(configManager.getMessage("invalid-time-format"));
            return true;
        }

        boolean wasInactive = itemManager.isInactiveTimedItem(item);
        boolean wasUntimed = !itemManager.isTimedItem(item);

        long result = itemManager.addExpireTime(item, duration);
        if (wasUntimed || wasInactive) {
            if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                itemManager.activateTimedItem(player, item, true);
            } else {
                String msg = configManager.getMessage("duration-set")
                        .replace("{duration}", TimeParser.formatDuration(result));
                player.sendMessage(msg);
            }
        } else {
            String formattedDate = DateParser.formatDate(result, configManager.getDateFormat());
            String msg = configManager.getMessage("time-added")
                    .replace("{time}", args[1])
                    .replace("{expiry}", formattedDate);
            player.sendMessage(msg);
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("timeditem.command.remove")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("player-only"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(configManager.getMessage("usage").replace("{usage}", "/ti remove [time] (ex: /ti remove 3d5h)"));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            player.sendMessage(configManager.getMessage("no-item-in-hand"));
            return true;
        }

        if (!itemManager.isTimedItem(item)) {
            player.sendMessage(configManager.getMessage("not-timed-item"));
            return true;
        }

        long duration;
        try {
            duration = TimeParser.parseDuration(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(configManager.getMessage("invalid-time-format"));
            return true;
        }

        Long result = itemManager.removeExpireTime(item, duration);
        if (result == null) {
            player.sendMessage(configManager.getMessage("not-timed-item"));
            return true;
        }

        if (result == -1L) {
            // Item expired immediately
            int amount = item.getAmount();
            String displayName = itemManager.getItemDisplayName(item);
            player.getInventory().setItemInMainHand(null);

            String msg = configManager.getMessage("item-expired")
                    .replace("{item}", displayName)
                    .replace("{amount}", String.valueOf(amount));
            player.sendMessage(msg);
            return true;
        }

        if (itemManager.isInactiveTimedItem(item)) {
            String msg = configManager.getMessage("duration-set")
                    .replace("{duration}", TimeParser.formatDuration(result));
            player.sendMessage(msg);
        } else {
            String formattedDate = DateParser.formatDate(result, configManager.getDateFormat());
            String msg = configManager.getMessage("time-removed")
                    .replace("{time}", args[1])
                    .replace("{expiry}", formattedDate);
            player.sendMessage(msg);
        }
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("timeditem.command.set")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("player-only"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(configManager.getMessage("usage").replace("{usage}", "/ti set <format> (ex: /ti set 12/07/2026 hoặc /ti set 1d2h)"));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            player.sendMessage(configManager.getMessage("no-item-in-hand"));
            return true;
        }

        String input = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        // 1. Try parsing as duration (e.g. 1d2h or 30m)
        try {
            long duration = TimeParser.parseDuration(input);
            itemManager.setInactiveDuration(item, duration);
            if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                itemManager.activateTimedItem(player, item, true);
            } else {
                String msg = configManager.getMessage("duration-set")
                        .replace("{duration}", TimeParser.formatDuration(duration));
                player.sendMessage(msg);
            }
            return true;
        } catch (IllegalArgumentException ignored) {
        }

        // 2. Otherwise parse as date
        long parsedDate;
        try {
            parsedDate = DateParser.parseDate(input, configManager.getDateFormat());
        } catch (ParseException e) {
            String msg = configManager.getMessage("invalid-date-format")
                    .replace("{format}", configManager.getDateFormat());
            player.sendMessage(msg);
            return true;
        }

        if (parsedDate <= System.currentTimeMillis()) {
            player.sendMessage(configManager.getMessage("date-in-past"));
            return true;
        }

        itemManager.setExpireTime(item, parsedDate);
        String formattedDate = DateParser.formatDate(parsedDate, configManager.getDateFormat());
        String msg = configManager.getMessage("time-set")
                .replace("{expiry}", formattedDate);
        player.sendMessage(msg);
        return true;
    }

    private boolean handleClear(CommandSender sender) {
        if (!sender.hasPermission("timeditem.command.clear")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("player-only"));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            player.sendMessage(configManager.getMessage("no-item-in-hand"));
            return true;
        }

        if (!itemManager.isTimedItem(item)) {
            player.sendMessage(configManager.getMessage("not-timed-item"));
            return true;
        }

        itemManager.clearExpireTime(item);
        player.sendMessage(configManager.getMessage("time-cleared"));
        return true;
    }

    private boolean handleCheck(CommandSender sender) {
        if (!sender.hasPermission("timeditem.command.check")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("player-only"));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            player.sendMessage(configManager.getMessage("no-item-in-hand"));
            return true;
        }

        if (!itemManager.isTimedItem(item)) {
            player.sendMessage(configManager.getMessage("not-timed-item"));
            return true;
        }

        String itemName = itemManager.getItemDisplayName(item);
        String status = itemManager.getStatusFormatted(item);
        String remaining;
        String expiry;

        if (itemManager.isInactiveTimedItem(item)) {
            Long dur = itemManager.getInactiveDuration(item);
            remaining = dur != null ? TimeParser.formatDuration(dur) : "0s";
            expiry = configManager.getStatusInactive();
        } else if (itemManager.isExpired(item)) {
            remaining = "0s";
            Long exp = itemManager.getExpireTime(item);
            expiry = exp != null ? DateParser.formatDate(exp, configManager.getDateFormat()) : "N/A";
        } else {
            Long exp = itemManager.getExpireTime(item);
            long diff = exp != null ? Math.max(0, exp - System.currentTimeMillis()) : 0;
            remaining = TimeParser.formatDuration(diff);
            expiry = exp != null ? DateParser.formatDate(exp, configManager.getDateFormat()) : "N/A";
        }

        sender.sendMessage(ColorUtil.colorize(configManager.getMessageWithoutPrefix("check-header")));
        sender.sendMessage(ColorUtil.colorize(configManager.getMessageWithoutPrefix("check-title")));
        sender.sendMessage(ColorUtil.colorize(configManager.getMessageWithoutPrefix("check-item").replace("{item}", itemName)));
        sender.sendMessage(ColorUtil.colorize(configManager.getMessageWithoutPrefix("check-status").replace("{status}", status)));
        sender.sendMessage(ColorUtil.colorize(configManager.getMessageWithoutPrefix("check-remaining").replace("{remaining}", remaining)));
        sender.sendMessage(ColorUtil.colorize(configManager.getMessageWithoutPrefix("check-expiry").replace("{expiry}", expiry)));
        sender.sendMessage(ColorUtil.colorize(configManager.getMessageWithoutPrefix("check-footer")));
        return true;
    }

    private boolean handleGiveMMO(CommandSender sender, String[] args) {
        if (!sender.hasPermission("timeditem.command.givemmo")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(configManager.getMessage("usage").replace("{usage}", "/ti givemmo <player> <type> <id> <time> (vd: /ti givemmo Steve SWORD CUTLASS 3d)"));
            return true;
        }

        if (!plugin.getMMOItemsHook().isAvailable()) {
            sender.sendMessage(configManager.getMessage("mmo-not-installed"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getPlayer(args[1]);
        }
        if (target == null) {
            sender.sendMessage(configManager.getMessage("player-not-found").replace("{player}", args[1]));
            return true;
        }

        String typeName = args[2];
        String itemId = args[3];

        long duration;
        try {
            duration = TimeParser.parseDuration(args[4]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(configManager.getMessage("invalid-time-format"));
            return true;
        }

        ItemStack item = plugin.getMMOItemsHook().getItem(typeName, itemId);
        if (item == null) {
            sender.sendMessage(configManager.getMessage("mmo-item-not-found")
                    .replace("{type}", typeName)
                    .replace("{id}", itemId));
            return true;
        }

        itemManager.setInactiveDuration(item, duration);
        if (target.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
            itemManager.activateTimedItem(target, item, false);
        }

        HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }
            target.sendMessage(configManager.getMessage("inventory-full-dropped").replace("{player}", target.getName()));
        }

        String formattedDuration = TimeParser.formatDuration(duration);
        String itemName = itemManager.getItemDisplayName(item);

        sender.sendMessage(configManager.getMessage("mmo-given")
                .replace("{item}", itemName)
                .replace("{time}", formattedDuration)
                .replace("{player}", target.getName()));

        target.sendMessage(configManager.getMessage("mmo-received")
                .replace("{item}", itemName)
                .replace("{time}", formattedDuration));

        return true;
    }

    private boolean handleToken(CommandSender sender, String[] args) {
        if (!sender.hasPermission("timeditem.command.token")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(configManager.getMessage("usage").replace("{usage}", "/ti token <time> [amount] [player] hoặc /ti token <player> <time> [amount]"));
            return true;
        }

        Player target = null;
        long duration = -1;
        int amount = 1;

        // Check if args[1] is a duration: /ti token <time> [amount] [player]
        try {
            duration = TimeParser.parseDuration(args[1]);
            if (args.length >= 3) {
                try {
                    amount = Integer.parseInt(args[2]);
                    if (amount <= 0) {
                        sender.sendMessage(configManager.getMessage("invalid-amount"));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    target = Bukkit.getPlayer(args[2]);
                    if (target == null) {
                        sender.sendMessage(configManager.getMessage("invalid-amount"));
                        return true;
                    }
                }
            }
            if (args.length >= 4 && target == null) {
                target = Bukkit.getPlayer(args[3]);
                if (target == null) {
                    sender.sendMessage(configManager.getMessage("player-not-found").replace("{player}", args[3]));
                    return true;
                }
            }
        } catch (IllegalArgumentException notDuration) {
            // Check if args[1] is player: /ti token <player> <time> [amount]
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(configManager.getMessage("invalid-time-format"));
                return true;
            }
            if (args.length >= 3) {
                try {
                    duration = TimeParser.parseDuration(args[2]);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(configManager.getMessage("invalid-time-format"));
                    return true;
                }
            } else {
                sender.sendMessage(configManager.getMessage("usage").replace("{usage}", "/ti token <player> <time> [amount]"));
                return true;
            }
            if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                    if (amount <= 0) {
                        sender.sendMessage(configManager.getMessage("invalid-amount"));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(configManager.getMessage("invalid-amount"));
                    return true;
                }
            }
        }

        if (target == null) {
            if (sender instanceof Player p) {
                target = p;
            } else {
                sender.sendMessage(configManager.getMessage("player-only"));
                return true;
            }
        }

        if (duration <= 0) {
            sender.sendMessage(configManager.getMessage("invalid-time-format"));
            return true;
        }

        ItemStack token = itemManager.createExtensionToken(duration, amount);
        HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(token);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }
            target.sendMessage(configManager.getMessage("inventory-full-dropped").replace("{player}", target.getName()));
        }

        String timeStr = TimeParser.formatDuration(duration);
        sender.sendMessage(configManager.getMessage("token-given")
                .replace("{amount}", String.valueOf(amount))
                .replace("{time}", timeStr)
                .replace("{player}", target.getName()));

        target.sendMessage(configManager.getMessage("token-received")
                .replace("{amount}", String.valueOf(amount))
                .replace("{time}", timeStr));

        try {
            target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
        } catch (Exception ignored) {
        }

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("timeditem.command.reload")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        configManager.reload();
        checkManager.restart();
        initHelpEntries();
        String reloadedMsg = configManager.getMessage("reloaded")
                .replace("{lang}", configManager.getLanguage().toUpperCase());
        sender.sendMessage(reloadedMsg);
        return true;
    }

    private boolean handleHelp(CommandSender sender, int page) {
        if (!sender.hasPermission("timeditem.command.help")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        initHelpEntries();

        List<HelpEntry> availableEntries = new ArrayList<>();
        for (HelpEntry entry : helpEntries) {
            if (sender.hasPermission(entry.permission)) {
                availableEntries.add(entry);
            }
        }

        if (availableEntries.isEmpty()) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        int perPage = configManager.getHelpPerPage();
        int maxPage = (int) Math.ceil((double) availableEntries.size() / perPage);
        if (maxPage <= 0) maxPage = 1;

        if (page < 1) page = 1;
        if (page > maxPage) page = maxPage;

        String header = configManager.getMessageWithoutPrefix("help-header")
                .replace("{page}", String.valueOf(page))
                .replace("{max_page}", String.valueOf(maxPage));
        sender.sendMessage(ColorUtil.colorize(header));

        int startIndex = (page - 1) * perPage;
        int endIndex = Math.min(startIndex + perPage, availableEntries.size());

        for (int i = startIndex; i < endIndex; i++) {
            sender.sendMessage(ColorUtil.colorize(availableEntries.get(i).text));
        }

        if (page < maxPage) {
            String footer = configManager.getMessageWithoutPrefix("help-footer")
                    .replace("{next_page}", String.valueOf(page + 1));
            sender.sendMessage(ColorUtil.colorize(footer));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            if (sender.hasPermission("timeditem.command.add")) subcommands.add("add");
            if (sender.hasPermission("timeditem.command.remove")) subcommands.add("remove");
            if (sender.hasPermission("timeditem.command.set")) subcommands.add("set");
            if (sender.hasPermission("timeditem.command.clear")) subcommands.add("clear");
            if (sender.hasPermission("timeditem.command.check")) subcommands.add("check");
            if (sender.hasPermission("timeditem.command.givemmo")) subcommands.add("givemmo");
            if (sender.hasPermission("timeditem.command.token")) subcommands.add("token");
            if (sender.hasPermission("timeditem.command.reload")) subcommands.add("reload");
            if (sender.hasPermission("timeditem.command.help")) subcommands.add("help");

            return filterCompletions(subcommands, args[0]);
        }

        String sub = args[0].toLowerCase();

        if (args.length == 2) {
            if (sub.equals("add") && sender.hasPermission("timeditem.command.add")) {
                return filterCompletions(Arrays.asList("1h", "1d", "3d", "7d", "30d"), args[1]);
            }
            if (sub.equals("remove") && sender.hasPermission("timeditem.command.remove")) {
                return filterCompletions(Arrays.asList("1h", "1d", "3d", "7d"), args[1]);
            }
            if (sub.equals("set") && sender.hasPermission("timeditem.command.set")) {
                return filterCompletions(Arrays.asList("1d", "3d", "7d", "30d", "12/07/2026", "27/05/2026 13:17:29"), args[1]);
            }
            if (sub.equals("help") && sender.hasPermission("timeditem.command.help")) {
                return filterCompletions(Arrays.asList("1", "2"), args[1]);
            }
            if (sub.equals("givemmo") && sender.hasPermission("timeditem.command.givemmo")) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    players.add(p.getName());
                }
                return filterCompletions(players, args[1]);
            }
            if (sub.equals("token") && sender.hasPermission("timeditem.command.token")) {
                List<String> options = new ArrayList<>(Arrays.asList("1h", "1d", "3d", "7d", "30d"));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    options.add(p.getName());
                }
                return filterCompletions(options, args[1]);
            }
        }

        if (args.length == 3) {
            if (sub.equals("givemmo") && sender.hasPermission("timeditem.command.givemmo")) {
                List<String> types = plugin.getMMOItemsHook().getTypes();
                if (types.isEmpty()) {
                    types = Arrays.asList("SWORD", "ARMOR", "BOW", "CONSUMABLE", "TOOL", "ACCESSORY");
                }
                return filterCompletions(types, args[2]);
            }
            if (sub.equals("token") && sender.hasPermission("timeditem.command.token")) {
                if (Bukkit.getPlayerExact(args[1]) != null) {
                    return filterCompletions(Arrays.asList("1h", "1d", "3d", "7d", "30d"), args[2]);
                } else {
                    return filterCompletions(Arrays.asList("1", "5", "10", "16", "32", "64"), args[2]);
                }
            }
        }

        if (args.length == 4) {
            if (sub.equals("givemmo") && sender.hasPermission("timeditem.command.givemmo")) {
                List<String> ids = plugin.getMMOItemsHook().getTemplateIds(args[2]);
                return filterCompletions(ids, args[3]);
            }
            if (sub.equals("token") && sender.hasPermission("timeditem.command.token")) {
                if (Bukkit.getPlayerExact(args[1]) != null) {
                    return filterCompletions(Arrays.asList("1", "5", "10", "16", "32", "64"), args[3]);
                } else {
                    List<String> players = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        players.add(p.getName());
                    }
                    return filterCompletions(players, args[3]);
                }
            }
        }

        if (args.length == 5) {
            if (sub.equals("givemmo") && sender.hasPermission("timeditem.command.givemmo")) {
                return filterCompletions(Arrays.asList("1h", "1d", "3d", "7d", "30d"), args[4]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterCompletions(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        String lowerPrefix = prefix.toLowerCase();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lowerPrefix)) {
                result.add(option);
            }
        }
        return result;
    }
}
