package me.timeditem.check;

import me.timeditem.TimedItem;
import me.timeditem.config.ConfigManager;
import me.timeditem.item.ExpireAction;
import me.timeditem.item.ItemManager;
import me.timeditem.util.TimeParser;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
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
import java.util.*;
import java.util.function.Consumer;

public class CheckManager {
    private final TimedItem plugin;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final OfflinePlayerManager offlinePlayerManager;

    private BukkitTask inventoryWorldTask;
    private BukkitTask equipmentTask;
    private BukkitTask offlineTask;

    /**
     * In-memory tracker for expiry warnings per player to avoid calling setItemMeta() on held items.
     * Key: Player UUID -> Set of "<expireTimestamp>:<thresholdSeconds>"
     */
    private final Map<UUID, Set<String>> warnedPlayerItems = new HashMap<>();

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

        // 2. Fast equipment & expiry warning check task (checks armor and hands every 1-5 seconds)
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
        warnedPlayerItems.clear();
    }

    public void restart() {
        stop();
        start();
    }

    public void clearPlayerWarnings(UUID uuid) {
        warnedPlayerItems.remove(uuid);
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
     * Eliminates delay where players could wear or use expired equipment before the 30s cycle,
     * and dispatches configurable expiry warnings with zero ItemMeta writes.
     */
    public void checkEquipmentSlots() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = player.getInventory();

            // Main hand
            checkEquipmentItem(player, inv.getItemInMainHand(), () -> inv.setItemInMainHand(null), inv::setItemInMainHand, false, now);

            // Off hand
            checkEquipmentItem(player, inv.getItemInOffHand(), () -> inv.setItemInOffHand(null), inv::setItemInOffHand, false, now);

            // Helmet
            checkEquipmentItem(player, inv.getHelmet(), () -> inv.setHelmet(null), inv::setHelmet, true, now);

            // Chestplate
            checkEquipmentItem(player, inv.getChestplate(), () -> inv.setChestplate(null), inv::setChestplate, true, now);

            // Leggings
            checkEquipmentItem(player, inv.getLeggings(), () -> inv.setLeggings(null), inv::setLeggings, true, now);

            // Boots
            checkEquipmentItem(player, inv.getBoots(), () -> inv.setBoots(null), inv::setBoots, true, now);
        }
    }

    private void checkEquipmentItem(
            Player player,
            ItemStack item,
            Runnable removeAction,
            Consumer<ItemStack> updateAction,
            boolean isArmor,
            long now
    ) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        ItemManager.TimedData data = itemManager.getTimedData(meta);
        if (!data.isTimed()) {
            return;
        }

        if (data.isExpired(now)) {
            if (data.expireAction() == ExpireAction.SEAL) {
                boolean newlySealed = itemManager.sealItem(item, meta);
                String name = itemManager.getItemDisplayName(item, meta);

                if (isArmor) {
                    removeAction.run();
                    unequipSealedArmorToInventory(player, item);
                    if (newlySealed) {
                        sendSealMessage(player, name);
                    }
                    sendArmorUnequipMessage(player, name);
                } else if (newlySealed) {
                    updateAction.accept(item);
                    sendSealMessage(player, name);
                }
            } else {
                int amount = item.getAmount();
                String name = itemManager.getItemDisplayName(item, meta);
                removeAction.run();
                sendExpiryMessage(player, name, amount);
            }
            return;
        }

        if (!data.isInactive() && data.expireTime() != null && configManager.isExpiryWarningEnabled()) {
            checkAndSendExpiryWarning(player, item, meta, data.expireTime(), now);
        }
    }

    private void checkAndSendExpiryWarning(Player player, ItemStack item, ItemMeta meta, long expireTime, long now) {
        long diffMillis = expireTime - now;
        if (diffMillis <= 0) {
            return;
        }
        long remainingSec = diffMillis / 1000L;
        List<Integer> thresholds = configManager.getExpiryWarningTimes();
        if (thresholds.isEmpty()) {
            return;
        }

        // Find the smallest threshold that remainingSec has reached (thresholds are sorted descending)
        Integer matchedThreshold = null;
        for (int threshold : thresholds) {
            if (remainingSec <= threshold) {
                matchedThreshold = threshold;
            }
        }

        if (matchedThreshold == null) {
            return;
        }

        Set<String> warnedSet = warnedPlayerItems.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        String targetKey = expireTime + ":" + matchedThreshold;
        if (warnedSet.contains(targetKey)) {
            return;
        }

        // Prevent unbounded growth if a player holds hundreds of distinct timed items in one session
        if (warnedSet.size() > 256) {
            warnedSet.clear();
        }

        // Mark all thresholds >= matchedThreshold as warned so higher thresholds never re-trigger
        for (int threshold : thresholds) {
            if (threshold >= matchedThreshold) {
                warnedSet.add(expireTime + ":" + threshold);
            }
        }

        String itemName = itemManager.getItemDisplayName(item, meta);
        String timeStr = TimeParser.formatDuration(Math.max(1000L, diffMillis));

        if (configManager.isExpiryWarningActionbar()) {
            String actionbarMsg = configManager.getMessageWithoutPrefix("expiry-warning-actionbar")
                    .replace("{item}", itemName)
                    .replace("{remaining}", timeStr)
                    .replace("{time}", timeStr);
            if (!actionbarMsg.isEmpty()) {
                try {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionbarMsg));
                } catch (Throwable ignored) {
                }
            }
        }

        if (configManager.isExpiryWarningChat()) {
            String chatMsg = configManager.getMessage("expiry-warning-chat")
                    .replace("{item}", itemName)
                    .replace("{remaining}", timeStr)
                    .replace("{time}", timeStr);
            if (!chatMsg.isEmpty()) {
                player.sendMessage(chatMsg);
            }
        }

        Sound sound = configManager.getExpiryWarningSound();
        if (sound != null) {
            try {
                player.playSound(player.getLocation(), sound, 1.0f, 1.2f);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Checks a player's full inventory and ender chest for expired items and removes or seals them.
     *
     * @param player   the player to check
     * @param announce whether to send expiry notification to the player
     * @return the total number of expired items removed or newly sealed
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

        // 1. Check main inventory (includes hotbar, storage, armor 36..39, offhand 40)
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            ItemManager.TimedData data = itemManager.getTimedData(meta);
            if (data.isExpired(now)) {
                String displayName = itemManager.getItemDisplayName(item, meta);
                if (data.expireAction() == ExpireAction.SEAL) {
                    boolean newlySealed = itemManager.sealItem(item, meta);
                    boolean isArmorSlot = (i >= 36 && i <= 39);

                    if (isArmorSlot) {
                        player.getInventory().setItem(i, null);
                        unequipSealedArmorToInventory(player, item);
                        if (newlySealed) {
                            totalRemoved += item.getAmount();
                            if (announce) {
                                sendSealMessage(player, displayName);
                            }
                        }
                        if (announce) {
                            sendArmorUnequipMessage(player, displayName);
                        }
                    } else if (newlySealed) {
                        player.getInventory().setItem(i, item);
                        totalRemoved += item.getAmount();
                        if (announce) {
                            sendSealMessage(player, displayName);
                        }
                    }
                } else {
                    int amount = item.getAmount();
                    player.getInventory().setItem(i, null);
                    totalRemoved += amount;

                    if (announce) {
                        sendExpiryMessage(player, displayName, amount);
                    }
                }
            } else if (configManager.isContainerCheckEnabled()) {
                // Inspect inside containers (e.g. Shulker Boxes, Bundles)
                int insideRemoved = itemManager.cleanContainers(item);
                if (insideRemoved > 0) {
                    player.getInventory().setItem(i, item);
                    totalRemoved += insideRemoved;
                    if (announce) {
                        sendExpiryMessage(player, itemManager.getItemDisplayName(item, meta), insideRemoved);
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

            ItemManager.TimedData data = itemManager.getTimedData(meta);
            if (data.isExpired(now)) {
                String displayName = itemManager.getItemDisplayName(item, meta);
                if (data.expireAction() == ExpireAction.SEAL) {
                    if (itemManager.sealItem(item, meta)) {
                        player.getEnderChest().setItem(i, item);
                        totalRemoved += item.getAmount();
                        if (announce) {
                            sendSealMessage(player, displayName);
                        }
                    }
                } else {
                    int amount = item.getAmount();
                    player.getEnderChest().setItem(i, null);
                    totalRemoved += amount;

                    if (announce) {
                        sendExpiryMessage(player, displayName, amount);
                    }
                }
            } else if (configManager.isContainerCheckEnabled()) {
                int insideRemoved = itemManager.cleanContainers(item);
                if (insideRemoved > 0) {
                    player.getEnderChest().setItem(i, item);
                    totalRemoved += insideRemoved;
                    if (announce) {
                        sendExpiryMessage(player, itemManager.getItemDisplayName(item, meta), insideRemoved);
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
                        if (itemManager.getExpireAction(meta) == ExpireAction.SEAL) {
                            if (itemManager.sealItem(stack, meta)) {
                                itemEntity.setItemStack(stack);
                            }
                        } else {
                            itemEntity.remove();
                        }
                    }
                }
            }
        }
    }

    private void unequipSealedArmorToInventory(Player player, ItemStack armorItem) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(armorItem);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            String fullMsg = configManager.getMessage("inventory-full-dropped")
                    .replace("{player}", player.getName());
            if (!fullMsg.isEmpty()) {
                player.sendMessage(fullMsg);
            }
        }
    }

    private void sendExpiryMessage(Player player, String itemName, int amount) {
        String msg = configManager.getMessage("item-expired")
                .replace("{item}", itemName)
                .replace("{amount}", String.valueOf(amount));
        if (!msg.isEmpty()) {
            player.sendMessage(msg);
        }

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } catch (Exception ignored) {
        }
    }

    private void sendSealMessage(Player player, String itemName) {
        String msg = configManager.getMessage("item-sealed")
                .replace("{item}", itemName);
        if (!msg.isEmpty()) {
            player.sendMessage(msg);
        }

        try {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 0.8f);
        } catch (Exception ignored) {
        }
    }

    private void sendArmorUnequipMessage(Player player, String itemName) {
        String msg = configManager.getMessage("sealed-armor-unequipped")
                .replace("{item}", itemName);
        if (!msg.isEmpty()) {
            player.sendMessage(msg);
        }
    }

    public OfflinePlayerManager getOfflinePlayerManager() {
        return offlinePlayerManager;
    }
}
