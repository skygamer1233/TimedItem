package me.timeditem.check;

import me.timeditem.TimedItem;
import me.timeditem.config.ConfigManager;
import me.timeditem.item.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public class CheckManager {
    private final TimedItem plugin;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final OfflinePlayerManager offlinePlayerManager;
    private BukkitTask scheduledTask;

    public CheckManager(TimedItem plugin, ConfigManager configManager, ItemManager itemManager, OfflinePlayerManager offlinePlayerManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemManager = itemManager;
        this.offlinePlayerManager = offlinePlayerManager;
    }

    public void start() {
        stop();
        long intervalTicks = configManager.getCheckInterval() * 20L;
        scheduledTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::runCheckCycle, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    public void restart() {
        stop();
        start();
    }

    /**
     * Periodic check cycle run asynchronously.
     */
    private void runCheckCycle() {
        // 1. Check offline player files asynchronously (heavy disk I/O off the main thread)
        if (configManager.isOfflineCheckEnabled()) {
            offlinePlayerManager.scanOfflinePlayers();
        }

        // 2. Safely perform online player inventory check and world check on main thread
        if (configManager.isInventoryCheckEnabled() || configManager.isWorldCheckEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (configManager.isInventoryCheckEnabled()) {
                    checkOnlinePlayers();
                }
                if (configManager.isWorldCheckEnabled()) {
                    checkWorldItems();
                }
            });
        }
    }

    public void checkOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayer(player, true);
        }
    }

    /**
     * Checks a player's inventory and ender chest for expired items and removes them.
     *
     * @param player   the player to check
     * @param announce whether to send expiry notification to the player
     * @return the total number of expired items removed
     */
    public int checkPlayer(Player player, boolean announce) {
        if (player == null || !player.isOnline()) {
            return 0;
        }

        // Activate any inactive timed items if player is in survival mode
        if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
            itemManager.activatePlayerInventory(player);
        }

        int totalRemoved = 0;

        // Check main inventory (includes hotbar, armor, offhand)
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (itemManager.isExpired(item)) {
                int amount = item.getAmount();
                String displayName = itemManager.getItemDisplayName(item);
                player.getInventory().setItem(i, null);
                totalRemoved += amount;

                if (announce) {
                    sendExpiryMessage(player, displayName, amount);
                }
            }
        }

        // Check ender chest
        ItemStack[] enderContents = player.getEnderChest().getContents();
        for (int i = 0; i < enderContents.length; i++) {
            ItemStack item = enderContents[i];
            if (itemManager.isExpired(item)) {
                int amount = item.getAmount();
                String displayName = itemManager.getItemDisplayName(item);
                player.getEnderChest().setItem(i, null);
                totalRemoved += amount;

                if (announce) {
                    sendExpiryMessage(player, displayName, amount);
                }
            }
        }

        return totalRemoved;
    }

    public void checkWorldItems() {
        for (World world : Bukkit.getWorlds()) {
            for (Item itemEntity : world.getEntitiesByClass(Item.class)) {
                ItemStack stack = itemEntity.getItemStack();
                if (itemManager.isExpired(stack)) {
                    itemEntity.remove();
                }
            }
        }
    }

    private void sendExpiryMessage(Player player, String itemName, int amount) {
        String msg = configManager.getMessage("item-expired")
                .replace("{item}", itemName)
                .replace("{amount}", String.valueOf(amount));
        player.sendMessage(msg);

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } catch (Exception ignored) {
        }
    }

    public OfflinePlayerManager getOfflinePlayerManager() {
        return offlinePlayerManager;
    }
}
