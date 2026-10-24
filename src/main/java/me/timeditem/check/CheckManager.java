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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CheckManager {
    private final TimedItem plugin;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final OfflinePlayerManager offlinePlayerManager;

    private BukkitTask inventoryWorldTask;
    private BukkitTask equipmentTask;
    private BukkitTask offlineTask;

    public CheckManager(TimedItem plugin, ConfigManager configManager, ItemManager itemManager, OfflinePlayerManager offlinePlayerManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemManager = itemManager;
        this.offlinePlayerManager = offlinePlayerManager;
    }

    public void start() {
        stop();

        // 1. Regular inventory and world check task
        long intervalTicks = configManager.getCheckInterval() * 20L;
        inventoryWorldTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runPeriodicCheck, intervalTicks, intervalTicks);

        // 2. Fast equipment check task (checks armor and hands every 1-5 seconds to prevent wearing expired gear)
        long equipTicks = configManager.getEquipmentCheckInterval() * 20L;
        equipmentTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkEquipmentSlots, equipTicks, equipTicks);

        // 3. Optional offline player scanner with dedicated long interval (e.g. 30 minutes)
        if (configManager.isOfflineCheckEnabled()) {
            long offlineTicks = configManager.getOfflineCheckInterval() * 20L;
            offlineTask = Bukkit.getScheduler().runTaskTimer(plugin, this::triggerOfflineScan, offlineTicks, offlineTicks);
        }
    }

    public void stop() {
        if (inventoryWorldTask != null) {
            inventoryWorldTask.cancel();
            inventoryWorldTask = null;
        }
        if (equipmentTask != null) {
            equipmentTask.cancel();
            equipmentTask = null;
        }
        if (offlineTask != null) {
            offlineTask.cancel();
            offlineTask = null;
        }
    }

    public void restart() {
        stop();
        start();
    }

    private void runPeriodicCheck() {
        if (configManager.isInventoryCheckEnabled()) {
            checkOnlinePlayers();
        }
        if (configManager.isWorldCheckEnabled()) {
            checkWorldItems();
        }
    }

    private void triggerOfflineScan() {
        if (!configManager.isOfflineCheckEnabled()) {
            return;
        }

        // Safely capture main thread data before dispatching async
        File worldFolder = Bukkit.getWorlds().isEmpty() ? new File("world") : Bukkit.getWorlds().get(0).getWorldFolder();
        Set<UUID> onlineUUIDs = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            onlineUUIDs.add(p.getUniqueId());
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            offlinePlayerManager.scanOfflinePlayers(worldFolder, onlineUUIDs);
        });
    }

    public void checkOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayer(player, true);
        }
    }

    /**
     * Fast check specifically targeting equipment slots (Armor + Hands).
     * Eliminates delay where players could wear or use expired equipment before the 30s cycle.
     */
    public void checkEquipmentSlots() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = player.getInventory();

            // Main hand
            checkEquipmentItem(player, inv.getItemInMainHand(), () -> inv.setItemInMainHand(null), now);

            // Off hand
            checkEquipmentItem(player, inv.getItemInOffHand(), () -> inv.setItemInOffHand(null), now);

            // Helmet
            checkEquipmentItem(player, inv.getHelmet(), () -> inv.setHelmet(null), now);

            // Chestplate
            checkEquipmentItem(player, inv.getChestplate(), () -> inv.setChestplate(null), now);

            // Leggings
            checkEquipmentItem(player, inv.getLeggings(), () -> inv.setLeggings(null), now);

            // Boots
            checkEquipmentItem(player, inv.getBoots(), () -> inv.setBoots(null), now);
        }
    }

    private void checkEquipmentItem(Player player, ItemStack item, Runnable removeAction, long now) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && itemManager.isExpired(meta, now)) {
            int amount = item.getAmount();
            String name = itemManager.getItemDisplayName(item);
            removeAction.run();
            sendExpiryMessage(player, name, amount);
        }
    }

    /**
     * Checks a player's full inventory and ender chest for expired items and removes them.
     *
     * @param player   the player to check
     * @param announce whether to send expiry notification to the player
     * @return the total number of expired items removed
     */
    public int checkPlayer(Player player, boolean announce) {
        if (player == null || !player.isOnline()) {
            return 0;
        }

        // Activate any inactive timed items if player is in an activation game mode
        if (configManager.isActivationGameMode(player.getGameMode())) {
            itemManager.activatePlayerInventory(player);
        }

        int totalRemoved = 0;
        long now = System.currentTimeMillis();

        // 1. Check main inventory (includes hotbar, armor, offhand)
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            if (itemManager.isExpired(meta, now)) {
                int amount = item.getAmount();
                String displayName = itemManager.getItemDisplayName(item);
                player.getInventory().setItem(i, null);
                totalRemoved += amount;

                if (announce) {
                    sendExpiryMessage(player, displayName, amount);
                }
            } else if (configManager.isContainerCheckEnabled()) {
                // Inspect inside containers (e.g. Shulker Boxes, Bundles)
                int insideRemoved = itemManager.cleanContainers(item);
                if (insideRemoved > 0) {
                    totalRemoved += insideRemoved;
                    if (announce) {
                        sendExpiryMessage(player, itemManager.getItemDisplayName(item), insideRemoved);
                    }
                }
            }
        }

        // 2. Check ender chest
        ItemStack[] enderContents = player.getEnderChest().getContents();
        for (int i = 0; i < enderContents.length; i++) {
            ItemStack item = enderContents[i];
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            if (itemManager.isExpired(meta, now)) {
                int amount = item.getAmount();
                String displayName = itemManager.getItemDisplayName(item);
                player.getEnderChest().setItem(i, null);
                totalRemoved += amount;

                if (announce) {
                    sendExpiryMessage(player, displayName, amount);
                }
            } else if (configManager.isContainerCheckEnabled()) {
                int insideRemoved = itemManager.cleanContainers(item);
                if (insideRemoved > 0) {
                    totalRemoved += insideRemoved;
                    if (announce) {
                        sendExpiryMessage(player, itemManager.getItemDisplayName(item), insideRemoved);
                    }
                }
            }
        }

        return totalRemoved;
    }

    public void checkWorldItems() {
        long now = System.currentTimeMillis();
        for (World world : Bukkit.getWorlds()) {
            for (Item itemEntity : world.getEntitiesByClass(Item.class)) {
                if (!itemEntity.isValid() || itemEntity.isDead()) {
                    continue;
                }
                ItemStack stack = itemEntity.getItemStack();
                if (stack.hasItemMeta()) {
                    ItemMeta meta = stack.getItemMeta();
                    if (meta != null && itemManager.isExpired(meta, now)) {
                        itemEntity.remove();
                    }
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
