package me.timeditem.command;

import me.timeditem.TimedItem;
import me.timeditem.check.CheckManager;
import me.timeditem.config.ConfigManager;
import me.timeditem.item.ItemManager;
import me.timeditem.util.ColorUtil;
import me.timeditem.util.DateParser;
import me.timeditem.util.TimeParser;
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
                configManager.getHelpCommandMessage("add", "&6/ti add [time] &7- Thêm thời hạn cho vật phẩm. &8(vd: /ti add 1d2h)")));
        helpEntries.add(new HelpEntry("timeditem.command.remove",
                configManager.getHelpCommandMessage("remove", "&6/ti remove [time] &7- Xoá bớt thời hạn của vật phẩm. &8(vd: /ti remove 3d5h)")));
        helpEntries.add(new HelpEntry("timeditem.command.set",
                configManager.getHelpCommandMessage("set", "&6/ti set <format> &7- Đặt thời hạn cho vật phẩm. &8(vd: /ti set 12/07/2025)")));
        helpEntries.add(new HelpEntry("timeditem.command.clear",
                configManager.getHelpCommandMessage("clear", "&6/ti clear &7- Xoá thời hạn khỏi vật phẩm.")));
        helpEntries.add(new HelpEntry("timeditem.command.reload",
                configManager.getHelpCommandMessage("reload", "&6/ti reload &7- Nạp lại plugin.")));
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
                sender.sendMessage(configManager.getMessage("usage").replace("{usage}", "/ti <add|remove|set|clear|reload|help>"));
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
            sender.sendMessage(configManager.getMessage("usage").replace("{usage}", "/ti set <format> (ex: /ti set 12/07/2025 hoặc /ti set 1d2h)"));
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

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("timeditem.command.reload")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        configManager.reload();
        checkManager.restart();
        initHelpEntries();
        sender.sendMessage(configManager.getMessage("reloaded"));
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
            if (sender.hasPermission("timeditem.command.reload")) subcommands.add("reload");
            if (sender.hasPermission("timeditem.command.help")) subcommands.add("help");

            return filterCompletions(subcommands, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
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
