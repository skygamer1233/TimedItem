package me.timeditem.listener;

import me.timeditem.TimedItem;
import me.timeditem.check.CheckManager;
import me.timeditem.config.ConfigManager;
import me.timeditem.item.ItemManager;
import me.timeditem.util.DateParser;
import me.timeditem.util.TimeParser;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
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

        // 2. Immediate check of online inventory on join
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

        // 3. Activate any inactive timed items in inventory if player is in survival mode
        checkInventoryActivation(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SURVIVAL) {
            ItemStack item = player.getInventory().getItem(event.getNewSlot());
            if (itemManager.activateTimedItem(player, item)) {
                player.getInventory().setItem(event.getNewSlot(), item);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SURVIVAL) {
            ItemStack main = event.getMainHandItem();
            if (itemManager.activateTimedItem(player, main)) {
                event.setMainHandItem(main);
            }
            ItemStack off = event.getOffHandItem();
            if (itemManager.activateTimedItem(player, off)) {
                event.setOffHandItem(off);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SURVIVAL) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (event.getPlayer().isOnline()) {
                    checkInventoryActivation(event.getPlayer());
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if (item != null && itemManager.isExpired(item)) {
            event.setCancelled(true);
            int amount = item.getAmount();
            String displayName = itemManager.getItemDisplayName(item);

            player.getInventory().setItemInMainHand(null);

            String msg = configManager.getMessage("item-expired")
                    .replace("{item}", displayName)
                    .replace("{amount}", String.valueOf(amount));
            player.sendMessage(msg);

            try {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            } catch (Exception ignored) {
            }
            return;
        }

        if (player.getGameMode() == GameMode.SURVIVAL) {
            checkInventoryActivation(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        if (current != null && itemManager.isExpired(current)) {
            event.setCancelled(true);
            event.setCurrentItem(null);

            if (event.getWhoClicked() instanceof Player player) {
                int amount = current.getAmount();
                String displayName = itemManager.getItemDisplayName(current);

                String msg = configManager.getMessage("item-expired")
                        .replace("{item}", displayName)
                        .replace("{amount}", String.valueOf(amount));
                player.sendMessage(msg);

                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                } catch (Exception ignored) {
                }
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

        if (event.getWhoClicked() instanceof Player player && player.getGameMode() == GameMode.SURVIVAL) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    checkInventoryActivation(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && player.getGameMode() == GameMode.SURVIVAL) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    checkInventoryActivation(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && player.getGameMode() == GameMode.SURVIVAL) {
            checkInventoryActivation(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (itemManager.isExpired(item)) {
            event.setCancelled(true);
            event.getItem().remove();

            if (event.getEntity() instanceof Player player) {
                int amount = item.getAmount();
                String displayName = itemManager.getItemDisplayName(item);

                String msg = configManager.getMessage("item-expired")
                        .replace("{item}", displayName)
                        .replace("{amount}", String.valueOf(amount));
                player.sendMessage(msg);

                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                } catch (Exception ignored) {
                }
            }
            return;
        }

        if (event.getEntity() instanceof Player player && player.getGameMode() == GameMode.SURVIVAL) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    checkInventoryActivation(player);
                }
            });
        }
    }

    private void checkInventoryActivation(Player player) {
        if (player == null || !player.isOnline() || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        itemManager.activatePlayerInventory(player);
    }
}
