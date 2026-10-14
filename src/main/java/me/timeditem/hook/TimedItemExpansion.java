package me.timeditem.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.timeditem.TimedItem;
import me.timeditem.config.ConfigManager;
import me.timeditem.item.ItemManager;
import me.timeditem.util.DateParser;
import me.timeditem.util.TimeParser;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TimedItemExpansion extends PlaceholderExpansion {
    private final TimedItem plugin;
    private final ConfigManager configManager;
    private final ItemManager itemManager;

    public TimedItemExpansion(TimedItem plugin, ConfigManager configManager, ItemManager itemManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemManager = itemManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "timeditem";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SkyGamer";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        ItemStack handItem = player.getInventory().getItemInMainHand();
        boolean hasItem = handItem != null && handItem.getType() != Material.AIR;

        String param = params.toLowerCase();

        switch (param) {
            case "hand_is_timed" -> {
                return (hasItem && itemManager.isTimedItem(handItem)) ? "true" : "false";
            }
            case "hand_status" -> {
                if (!hasItem || !itemManager.isTimedItem(handItem)) {
                    return "none";
                }
                if (itemManager.isExpired(handItem)) {
                    return "expired";
                }
                if (itemManager.isInactiveTimedItem(handItem)) {
                    return "inactive";
                }
                return "active";
            }
            case "hand_status_formatted" -> {
                if (!hasItem || !itemManager.isTimedItem(handItem)) {
                    return configManager.getStatusNone();
                }
                if (itemManager.isExpired(handItem)) {
                    return configManager.getStatusExpired();
                }
                if (itemManager.isInactiveTimedItem(handItem)) {
                    return configManager.getStatusInactive();
                }
                return configManager.getStatusActive();
            }
            case "hand_remaining" -> {
                if (!hasItem || !itemManager.isTimedItem(handItem)) {
                    return "";
                }
                if (itemManager.isInactiveTimedItem(handItem)) {
                    Long duration = itemManager.getInactiveDuration(handItem);
                    return duration != null ? TimeParser.formatDuration(duration) : "";
                }
                Long expire = itemManager.getExpireTime(handItem);
                if (expire == null) {
                    return "";
                }
                long diff = expire - System.currentTimeMillis();
                return diff > 0 ? TimeParser.formatDuration(diff) : "0s";
            }
            case "hand_remaining_seconds" -> {
                if (!hasItem || !itemManager.isTimedItem(handItem)) {
                    return "0";
                }
                if (itemManager.isInactiveTimedItem(handItem)) {
                    Long duration = itemManager.getInactiveDuration(handItem);
                    return duration != null ? String.valueOf(duration / 1000) : "0";
                }
                Long expire = itemManager.getExpireTime(handItem);
                if (expire == null) {
                    return "0";
                }
                long diff = expire - System.currentTimeMillis();
                return String.valueOf(Math.max(0, diff / 1000));
            }
            case "hand_expiry" -> {
                if (!hasItem || !itemManager.isTimedItem(handItem)) {
                    return "";
                }
                if (itemManager.isInactiveTimedItem(handItem)) {
                    return "inactive";
                }
                Long expire = itemManager.getExpireTime(handItem);
                if (expire == null) {
                    return "";
                }
                return DateParser.formatDate(expire, configManager.getDateFormat());
            }
            case "hand_duration" -> {
                if (!hasItem || !itemManager.isTimedItem(handItem)) {
                    return "";
                }
                Long duration = itemManager.getInactiveDuration(handItem);
                return duration != null ? TimeParser.formatDuration(duration) : "";
            }
            case "hand_name" -> {
                if (!hasItem) {
                    return "";
                }
                return itemManager.getItemDisplayName(handItem);
            }
            default -> {
                return null;
            }
        }
    }
}
