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
        ItemManager.TimedData data = hasItem ? itemManager.getTimedData(handItem) : null;

        String param = params.toLowerCase();

        switch (param) {
            case "hand_is_timed" -> {
                return (data != null && data.isTimed()) ? "true" : "false";
            }
            case "hand_status" -> {
                if (data == null || !data.isTimed()) {
                    return "none";
                }
                if (data.isSealed()) {
                    return "sealed";
                }
                if (data.isExpired()) {
                    return "expired";
                }
                if (data.isInactive()) {
                    return "inactive";
                }
                return "active";
            }
            case "hand_status_formatted" -> {
                if (data == null || !data.isTimed()) {
                    return configManager.getStatusNone();
                }
                return itemManager.getStatusFormatted(data);
            }
            case "hand_expire_action" -> {
                if (data == null || !data.isTimed()) {
                    return "none";
                }
                return data.expireAction().name().toLowerCase();
            }
            case "hand_expire_action_formatted" -> {
                if (data == null || !data.isTimed()) {
                    return configManager.getStatusNone();
                }
                return configManager.getActionFormatted(data.expireAction());
            }
            case "hand_extensions_used" -> {
                if (data == null || !data.isTimed()) {
                    return "0";
                }
                return String.valueOf(data.extensionsUsed());
            }
            case "hand_extensions_max" -> {
                int max = configManager.getTokenMaxExtensions();
                return max > 0 ? String.valueOf(max) : "unlimited";
            }
            case "hand_remaining" -> {
                if (data == null || !data.isTimed()) {
                    return "";
                }
                if (data.isInactive()) {
                    Long duration = data.duration();
                    return duration != null ? TimeParser.formatDuration(duration) : "";
                }
                if (data.isExpired()) {
                    return "0s";
                }
                Long expire = data.expireTime();
                if (expire == null) {
                    return "";
                }
                long diff = expire - System.currentTimeMillis();
                return diff > 0 ? TimeParser.formatDuration(diff) : "0s";
            }
            case "hand_remaining_seconds" -> {
                if (data == null || !data.isTimed()) {
                    return "0";
                }
                if (data.isInactive()) {
                    Long duration = data.duration();
                    return duration != null ? String.valueOf(duration / 1000) : "0";
                }
                if (data.isExpired()) {
                    return "0";
                }
                Long expire = data.expireTime();
                if (expire == null) {
                    return "0";
                }
                long diff = expire - System.currentTimeMillis();
                return String.valueOf(Math.max(0, diff / 1000));
            }
            case "hand_expiry" -> {
                if (data == null || !data.isTimed()) {
                    return "";
                }
                if (data.isInactive()) {
                    return "inactive";
                }
                Long expire = data.expireTime();
                if (expire == null) {
                    return "";
                }
                return DateParser.formatDate(expire, configManager.getDateFormat());
            }
            case "hand_duration" -> {
                if (data == null || !data.isTimed()) {
                    return "";
                }
                Long duration = data.duration();
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
