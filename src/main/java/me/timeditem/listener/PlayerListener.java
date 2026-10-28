package me.timeditem.listener;

import me.timeditem.TimedItem;
import me.timeditem.check.CheckManager;
import me.timeditem.config.ConfigManager;
import me.timeditem.item.ExpireAction;
import me.timeditem.item.ItemManager;
import me.timeditem.util.DateParser;
import me.timeditem.util.TimeParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final TimedItem plugin;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final CheckManager checkManager;

    /**
     * Cooldown map (Player UUID -> timestamp) to prevent chat spam when a player repeatedly
     * clicks or attacks with a SEALED item.
     */
    private final Map<UUID, Long> sealedMessageCooldown = new HashMap<>();

    public PlayerListener(TimedItem plugin, ConfigManager configManager, ItemManager itemManager, CheckManager checkManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemManager = itemManager;
        this.checkManager = checkManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 1. Consume any expired items recorded while offline
        int offlineExpired = checkManager.getOfflinePlayerManager().consumeOfflineExpired(player.getUniqueId());

        // 2. Immediate check of online inventory and ender chest on join
        int joinExpired = checkManager.checkPlayer(player, false);

        int totalExpired = offlineExpired + joinExpired;
        if (totalExpired > 0) {
            String msg = configManager.getMessage("items-expired-offline")
                    .replace("{count}", String.valueOf(totalExpired));
            if (!msg.isEmpty()) {
                player.sendMessage(msg);
            }

            try {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            } catch (Exception ignored) {
            }
        }

        // 3. Activate inactive timed items if in activation gamemode
        if (configManager.isActivationGameMode(player.getGameMode())) {
            itemManager.activatePlayerInventory(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        checkManager.clearPlayerWarnings(uuid);
        sealedMessageCooldown.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }

        // MMOItems updater recovery: restore lore if erased
        itemManager.ensureTimedLore(item);

        if (configManager.isActivationGameMode(player.getGameMode())) {
            if (itemManager.activateTimedItem(player, item)) {
                player.getInventory().setItem(event.getNewSlot(), item);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack main = event.getMainHandItem();
        if (main != null && main.hasItemMeta()) {
            itemManager.ensureTimedLore(main);
            if (configManager.isActivationGameMode(player.getGameMode()) && itemManager.activateTimedItem(player, main)) {
                event.setMainHandItem(main);
            }
        }

        ItemStack off = event.getOffHandItem();
        if (off != null && off.hasItemMeta()) {
            itemManager.ensureTimedLore(off);
            if (configManager.isActivationGameMode(player.getGameMode()) && itemManager.activateTimedItem(player, off)) {
                event.setOffHandItem(off);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (configManager.isActivationGameMode(event.getNewGameMode())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (event.getPlayer().isOnline()) {
                    itemManager.activatePlayerInventory(event.getPlayer());
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && itemManager.isExpired(meta, System.currentTimeMillis())) {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                event.setUseInteractedBlock(Event.Result.DENY);

                String displayName = itemManager.getItemDisplayName(item, meta);
                boolean isOffHand = (event.getHand() == EquipmentSlot.OFF_HAND);

                if (itemManager.getExpireAction(meta) == ExpireAction.SEAL) {
                    if (itemManager.sealItem(item, meta)) {
                        if (isOffHand) {
                            player.getInventory().setItemInOffHand(item);
                        } else {
                            player.getInventory().setItemInMainHand(item);
                        }
                    }
                    sendSealedBlockedNotification(player, displayName);
                } else {
                    int amount = item.getAmount();
                    if (isOffHand) {
                        player.getInventory().setItemInOffHand(null);
                    } else {
                        player.getInventory().setItemInMainHand(null);
                    }
                    sendExpiryNotification(player, displayName, amount);
                }
                return;
            }

            // If holding an inactive timed item, activate it directly without full inventory scanning
            itemManager.ensureTimedLore(item);
            if (configManager.isActivationGameMode(player.getGameMode()) && itemManager.isInactiveTimedItem(item)) {
                itemManager.activateTimedItem(player, item);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            ItemStack weapon = player.getInventory().getItemInMainHand();
            if (weapon != null && weapon.hasItemMeta()) {
                ItemMeta meta = weapon.getItemMeta();
                if (meta != null && itemManager.isExpired(meta, System.currentTimeMillis())) {
                    event.setCancelled(true);
                    String displayName = itemManager.getItemDisplayName(weapon, meta);

                    if (itemManager.getExpireAction(meta) == ExpireAction.SEAL) {
                        if (itemManager.sealItem(weapon, meta)) {
                            player.getInventory().setItemInMainHand(weapon);
                        }
                        sendSealedBlockedNotification(player, displayName);
                    } else {
                        int amount = weapon.getAmount();
                        player.getInventory().setItemInMainHand(null);
                        sendExpiryNotification(player, displayName, amount);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) {
            long now = System.currentTimeMillis();
            ItemStack bow = event.getBow();
            if (bow != null && bow.hasItemMeta()) {
                ItemMeta meta = bow.getItemMeta();
                if (meta != null && itemManager.isExpired(meta, now)) {
                    event.setCancelled(true);
                    String displayName = itemManager.getItemDisplayName(bow, meta);
                    boolean isMain = bow.equals(player.getInventory().getItemInMainHand());

                    if (itemManager.getExpireAction(meta) == ExpireAction.SEAL) {
                        if (itemManager.sealItem(bow, meta)) {
                            if (isMain) {
                                player.getInventory().setItemInMainHand(bow);
                            } else {
                                player.getInventory().setItemInOffHand(bow);
                            }
                        }
                        sendSealedBlockedNotification(player, displayName);
                    } else {
                        int amount = bow.getAmount();
                        if (isMain) {
                            player.getInventory().setItemInMainHand(null);
                        } else {
                            player.getInventory().setItemInOffHand(null);
                        }
                        sendExpiryNotification(player, displayName, amount);
                    }
                    return;
                }
            }

            ItemStack consumable = event.getConsumable();
            if (consumable != null && consumable.hasItemMeta()) {
                ItemMeta meta = consumable.getItemMeta();
                if (meta != null && itemManager.isExpired(meta, now)) {
                    event.setCancelled(true);
                    String displayName = itemManager.getItemDisplayName(consumable, meta);

                    if (itemManager.getExpireAction(meta) == ExpireAction.SEAL) {
                        itemManager.sealItem(consumable, meta);
                        sendSealedBlockedNotification(player, displayName);
                    } else {
                        int amount = consumable.getAmount();
                        player.getInventory().removeItem(consumable);
                        sendExpiryNotification(player, displayName, amount);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool != null && tool.hasItemMeta()) {
            ItemMeta meta = tool.getItemMeta();
            if (meta != null && itemManager.isExpired(meta, System.currentTimeMillis())) {
                event.setCancelled(true);
                String displayName = itemManager.getItemDisplayName(tool, meta);

                if (itemManager.getExpireAction(meta) == ExpireAction.SEAL) {
                    if (itemManager.sealItem(tool, meta)) {
                        player.getInventory().setItemInMainHand(tool);
                    }
                    sendSealedBlockedNotification(player, displayName);
                } else {
                    int amount = tool.getAmount();
                    player.getInventory().setItemInMainHand(null);
                    sendExpiryNotification(player, displayName, amount);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && itemManager.isExpired(meta, System.currentTimeMillis())) {
                event.setCancelled(true);
                Player player = event.getPlayer();
                String displayName = itemManager.getItemDisplayName(item, meta);
                boolean isMain = item.equals(player.getInventory().getItemInMainHand());

                if (itemManager.getExpireAction(meta) == ExpireAction.SEAL) {
                    if (itemManager.sealItem(item, meta)) {
                        if (isMain) {
                            player.getInventory().setItemInMainHand(item);
                        } else {
                            player.getInventory().setItemInOffHand(item);
                        }
                    }
                    sendSealedBlockedNotification(player, displayName);
                } else {
                    int amount = item.getAmount();
                    if (isMain) {
                        player.getInventory().setItemInMainHand(null);
                    } else {
                        player.getInventory().setItemInOffHand(null);
                    }
                    sendExpiryNotification(player, displayName, amount);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDispenseArmor(BlockDispenseArmorEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.hasItemMeta() && itemManager.isExpired(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && itemManager.isExpired(meta, System.currentTimeMillis())) {
                if (itemManager.getExpireAction(meta) == ExpireAction.SEAL) {
                    if (itemManager.sealItem(item, meta)) {
                        event.getEntity().setItemStack(item);
                    }
                } else {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // 1. Extension token application: cursor has token and clicks on a timed item
        if (cursor != null && itemManager.isExtensionToken(cursor)) {
            if (current != null && current.getType() != Material.AIR && itemManager.isTimedItem(current)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    ItemManager.TimedData data = itemManager.getTimedData(current);
                    boolean wasExpiredOrSealed = data.isExpired();

                    if (wasExpiredOrSealed) {
                        if (data.expireAction() != ExpireAction.SEAL || !configManager.isTokenAllowOnSealed()) {
                            player.sendMessage(configManager.getMessage("token-cannot-extend"));
                            return;
                        }
                    }

                    int maxExt = configManager.getTokenMaxExtensions();
                    if (maxExt > 0 && data.extensionsUsed() >= maxExt) {
                        String maxMsg = configManager.getMessage("token-max-reached")
                                .replace("{max}", String.valueOf(maxExt));
                        player.sendMessage(maxMsg);
                        return;
                    }

                    Long tokenDuration = itemManager.getTokenDuration(cursor);
                    if (tokenDuration != null && tokenDuration > 0) {
                        long newResult = itemManager.applyTokenExtension(current, tokenDuration);
                        event.setCurrentItem(current);

                        // Consume one token from cursor
                        if (cursor.getAmount() > 1) {
                            cursor.setAmount(cursor.getAmount() - 1);
                            event.getView().setCursor(cursor);
                        } else {
                            event.getView().setCursor(null);
                        }

                        String formattedDuration = TimeParser.formatDuration(tokenDuration);
                        String itemName = itemManager.getItemDisplayName(current);
                        String expiryStr;
                        if (itemManager.isInactiveTimedItem(current)) {
                            expiryStr = TimeParser.formatDuration(newResult);
                        } else {
                            expiryStr = DateParser.formatDate(newResult, configManager.getDateFormat());
                        }

                        String msgKey = wasExpiredOrSealed ? "token-unsealed" : "token-applied";
                        String msg = configManager.getMessage(msgKey);
                        if (msg.isEmpty()) {
                            msg = configManager.getMessage("token-applied");
                        }
                        msg = msg.replace("{time}", formattedDuration)
                                .replace("{item}", itemName)
                                .replace("{expiry}", expiryStr);
                        player.sendMessage(msg);

                        try {
                            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
                        } catch (Exception ignored) {
                        }
                    }
                }
                return;
            }
        }

        // 2. Prevent placing an expired/sealed cursor item into an ARMOR slot
        if (event.getSlotType() == InventoryType.SlotType.ARMOR && cursor != null && cursor.hasItemMeta()) {
            ItemMeta cursorMeta = cursor.getItemMeta();
            if (cursorMeta != null && itemManager.isExpired(cursorMeta, System.currentTimeMillis())) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    sendSealedBlockedNotification(player, itemManager.getItemDisplayName(cursor, cursorMeta));
                }
                return;
            }
        }

        // 3. Handle clicking on an expired item in a slot
        if (current != null && current.hasItemMeta()) {
            ItemMeta meta = current.getItemMeta();
            if (meta != null && itemManager.isExpired(meta, System.currentTimeMillis())) {
                if (itemManager.getExpireAction(meta) == ExpireAction.SEAL) {
                    if (itemManager.sealItem(current, meta)) {
                        event.setCurrentItem(current);
                    }
                    // Block shift-clicking sealed armor into equipment slots
                    if (event.isShiftClick() && isEquipableArmor(current.getType())) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player player) {
                            sendSealedBlockedNotification(player, itemManager.getItemDisplayName(current, meta));
                        }
                    }
                } else {
                    event.setCancelled(true);
                    event.setCurrentItem(null);

                    if (event.getWhoClicked() instanceof Player player) {
                        int amount = current.getAmount();
                        String displayName = itemManager.getItemDisplayName(current, meta);
                        sendExpiryNotification(player, displayName, amount);
                    }
                }
                return;
            }
        }

        // 4. Single slot activation instead of looping whole inventory on click
        if (event.getWhoClicked() instanceof Player player && configManager.isActivationGameMode(player.getGameMode())) {
            if (current != null && current.hasItemMeta() && itemManager.isInactiveTimedItem(current)) {
                if (itemManager.activateTimedItem(player, current)) {
                    event.setCurrentItem(current);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && configManager.isActivationGameMode(player.getGameMode())) {
            for (int slot : event.getRawSlots()) {
                ItemStack item = event.getView().getItem(slot);
                if (item != null && item.hasItemMeta() && itemManager.isInactiveTimedItem(item)) {
                    if (itemManager.activateTimedItem(player, item)) {
                        event.getView().setItem(slot, item);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && configManager.isActivationGameMode(player.getGameMode())) {
            itemManager.activatePlayerInventory(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && itemManager.isExpired(meta, System.currentTimeMillis())) {
                if (itemManager.getExpireAction(meta) == ExpireAction.SEAL) {
                    if (itemManager.sealItem(item, meta)) {
                        event.getItem().setItemStack(item);
                    }
                    return;
                } else {
                    event.setCancelled(true);
                    event.getItem().remove();

                    if (event.getEntity() instanceof Player player) {
                        int amount = item.getAmount();
                        String displayName = itemManager.getItemDisplayName(item, meta);
                        sendExpiryNotification(player, displayName, amount);
                    }
                    return;
                }
            }
        }

        if (event.getEntity() instanceof Player player && configManager.isActivationGameMode(player.getGameMode())) {
            if (item.hasItemMeta() && itemManager.isInactiveTimedItem(item)) {
                if (itemManager.activateTimedItem(player, item)) {
                    event.getItem().setItemStack(item);
                }
            }
        }
    }

    private boolean isEquipableArmor(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || name.equals("ELYTRA")
                || name.equals("CARVED_PUMPKIN")
                || name.endsWith("_HEAD")
                || name.endsWith("_SKULL");
    }

    private void sendExpiryNotification(Player player, String displayName, int amount) {
        String msg = configManager.getMessage("item-expired")
                .replace("{item}", displayName)
                .replace("{amount}", String.valueOf(amount));
        if (!msg.isEmpty()) {
            player.sendMessage(msg);
        }

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } catch (Exception ignored) {
        }
    }

    private void sendSealedBlockedNotification(Player player, String displayName) {
        long now = System.currentTimeMillis();
        Long lastSent = sealedMessageCooldown.get(player.getUniqueId());
        if (lastSent != null && (now - lastSent) < 1500L) {
            return;
        }
        sealedMessageCooldown.put(player.getUniqueId(), now);

        String msg = configManager.getMessage("item-is-sealed")
                .replace("{item}", displayName);
        if (!msg.isEmpty()) {
            player.sendMessage(msg);
        }

        try {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
        } catch (Exception ignored) {
        }
    }
}
