package me.timeditem.listener;

import me.timeditem.TimedItem;
import me.timeditem.check.CheckManager;
import me.timeditem.config.ConfigManager;
import me.timeditem.item.ItemManager;
import me.timeditem.util.DateParser;
import me.timeditem.util.TimeParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class PlayerListener implements Listener {
    private final TimedItem plugin;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final CheckManager checkManager;

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
            player.sendMessage(msg);

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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if (item != null && item.hasItemMeta() && itemManager.isExpired(item)) {
            event.setCancelled(true);
            int amount = item.getAmount();
            String displayName = itemManager.getItemDisplayName(item);

            // FIX: Remove from the exact hand used in the interact event
            if (event.getHand() == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }

            sendExpiryNotification(player, displayName, amount);
            return;
        }

        // If holding an inactive timed item, activate it directly without full inventory scanning
        if (item != null && item.hasItemMeta()) {
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
            if (weapon != null && weapon.hasItemMeta() && itemManager.isExpired(weapon)) {
                event.setCancelled(true);
                int amount = weapon.getAmount();
                String displayName = itemManager.getItemDisplayName(weapon);
                player.getInventory().setItemInMainHand(null);
                sendExpiryNotification(player, displayName, amount);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack bow = event.getBow();
            if (bow != null && bow.hasItemMeta() && itemManager.isExpired(bow)) {
                event.setCancelled(true);
                int amount = bow.getAmount();
                String displayName = itemManager.getItemDisplayName(bow);
                if (bow.equals(player.getInventory().getItemInMainHand())) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    player.getInventory().setItemInOffHand(null);
                }
                sendExpiryNotification(player, displayName, amount);
                return;
            }

            ItemStack consumable = event.getConsumable();
            if (consumable != null && consumable.hasItemMeta() && itemManager.isExpired(consumable)) {
                event.setCancelled(true);
                int amount = consumable.getAmount();
                String displayName = itemManager.getItemDisplayName(consumable);
                player.getInventory().removeItem(consumable);
                sendExpiryNotification(player, displayName, amount);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool != null && tool.hasItemMeta() && itemManager.isExpired(tool)) {
            event.setCancelled(true);
            int amount = tool.getAmount();
            String displayName = itemManager.getItemDisplayName(tool);
            player.getInventory().setItemInMainHand(null);
            sendExpiryNotification(player, displayName, amount);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.hasItemMeta() && itemManager.isExpired(item)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            int amount = item.getAmount();
            String displayName = itemManager.getItemDisplayName(item);
            if (item.equals(player.getInventory().getItemInMainHand())) {
                player.getInventory().setItemInMainHand(null);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
            sendExpiryNotification(player, displayName, amount);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        if (item != null && item.hasItemMeta() && itemManager.isExpired(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        if (current != null && current.hasItemMeta() && itemManager.isExpired(current)) {
            event.setCancelled(true);
            event.setCurrentItem(null);

            if (event.getWhoClicked() instanceof Player player) {
                int amount = current.getAmount();
                String displayName = itemManager.getItemDisplayName(current);
                sendExpiryNotification(player, displayName, amount);
            }
            return;
        }

        // Extension token application: cursor has token and clicks on a timed item
        ItemStack cursor = event.getCursor();
        if (cursor != null && itemManager.isExtensionToken(cursor)) {
            if (current != null && current.getType() != Material.AIR) {
                if (itemManager.isTimedItem(current)) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player player) {
                        if (itemManager.isExpired(current)) {
                            player.sendMessage(configManager.getMessage("token-cannot-extend"));
                            return;
                        }

                        Long tokenDuration = itemManager.getTokenDuration(cursor);
                        if (tokenDuration != null && tokenDuration > 0) {
                            long newResult = itemManager.addExpireTime(current, tokenDuration);
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

                            String msg = configManager.getMessage("token-applied")
                                    .replace("{time}", formattedDuration)
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
        }

        // Single slot activation instead of looping whole inventory on click
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
        if (item.hasItemMeta() && itemManager.isExpired(item)) {
            event.setCancelled(true);
            event.getItem().remove();

            if (event.getEntity() instanceof Player player) {
                int amount = item.getAmount();
                String displayName = itemManager.getItemDisplayName(item);
                sendExpiryNotification(player, displayName, amount);
            }
            return;
        }

        if (event.getEntity() instanceof Player player && configManager.isActivationGameMode(player.getGameMode())) {
            if (item.hasItemMeta() && itemManager.isInactiveTimedItem(item)) {
                if (itemManager.activateTimedItem(player, item)) {
                    event.getItem().setItemStack(item);
                }
            }
        }
    }

    private void sendExpiryNotification(Player player, String displayName, int amount) {
        String msg = configManager.getMessage("item-expired")
                .replace("{item}", displayName)
                .replace("{amount}", String.valueOf(amount));
        player.sendMessage(msg);

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } catch (Exception ignored) {
        }
    }
}
