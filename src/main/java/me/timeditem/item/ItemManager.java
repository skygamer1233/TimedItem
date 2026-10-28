package me.timeditem.item;

import me.timeditem.TimedItem;
import me.timeditem.config.ConfigManager;
import me.timeditem.util.ColorUtil;
import me.timeditem.util.DateParser;
import me.timeditem.util.TimeParser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ItemManager {
    public static final String OLD_LORE_MARKER = "§0§x§t§i§m§e";
    private final TimedItem plugin;
    private final ConfigManager configManager;
    private final NamespacedKey expireKey;
    private final NamespacedKey durationKey;
    private final NamespacedKey loreKey;
    private final NamespacedKey actionKey;
    private final NamespacedKey sealedKey;
    private final NamespacedKey extensionsKey;
    private final NamespacedKey tokenKey;
    private final NamespacedKey tokenDurationKey;

    public ItemManager(TimedItem plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.expireKey = new NamespacedKey(plugin, "expire_time");
        this.durationKey = new NamespacedKey(plugin, "duration_time");
        this.loreKey = new NamespacedKey(plugin, "lore_line");
        this.actionKey = new NamespacedKey(plugin, "expire_action");
        this.sealedKey = new NamespacedKey(plugin, "sealed");
        this.extensionsKey = new NamespacedKey(plugin, "extensions_used");
        this.tokenKey = new NamespacedKey(plugin, "extension_token");
        this.tokenDurationKey = new NamespacedKey(plugin, "token_duration");
    }

    public NamespacedKey getExpireKey() {
        return expireKey;
    }

    public NamespacedKey getDurationKey() {
        return durationKey;
    }

    public NamespacedKey getActionKey() {
        return actionKey;
    }

    public NamespacedKey getSealedKey() {
        return sealedKey;
    }

    public NamespacedKey getExtensionsKey() {
        return extensionsKey;
    }

    public record TimedData(
            boolean isTimed,
            boolean isInactive,
            boolean isSealed,
            Long expireTime,
            Long duration,
            ExpireAction expireAction,
            int extensionsUsed
    ) {
        public boolean isExpired(long now) {
            return isSealed || (expireTime != null && now >= expireTime);
        }

        public boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }
    }

    public TimedData getTimedData(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return new TimedData(false, false, false, null, null, configManager.getDefaultExpireAction(), 0);
        }
        ItemMeta meta = item.getItemMeta();
        return getTimedData(meta);
    }

    public TimedData getTimedData(ItemMeta meta) {
        if (meta == null) {
            return new TimedData(false, false, false, null, null, configManager.getDefaultExpireAction(), 0);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Long expire = pdc.get(expireKey, PersistentDataType.LONG);
        Long duration = pdc.get(durationKey, PersistentDataType.LONG);
        boolean isSealed = pdc.has(sealedKey, PersistentDataType.BYTE);
        boolean isTimed = expire != null || duration != null || isSealed;
        boolean isInactive = duration != null && !isSealed;
        ExpireAction action = getExpireAction(meta);
        Integer extUsed = pdc.get(extensionsKey, PersistentDataType.INTEGER);
        return new TimedData(isTimed, isInactive, isSealed, expire, duration, action, extUsed != null ? extUsed : 0);
    }

    public boolean isTimedItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return isTimedItem(meta);
    }

    public boolean isTimedItem(ItemMeta meta) {
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(expireKey, PersistentDataType.LONG)
                || pdc.has(durationKey, PersistentDataType.LONG)
                || pdc.has(sealedKey, PersistentDataType.BYTE);
    }

    public boolean isInactiveTimedItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return isInactiveTimedItem(meta);
    }

    public boolean isInactiveTimedItem(ItemMeta meta) {
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(durationKey, PersistentDataType.LONG) && !pdc.has(sealedKey, PersistentDataType.BYTE);
    }

    public boolean isSealed(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return isSealed(meta);
    }

    public boolean isSealed(ItemMeta meta) {
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(sealedKey, PersistentDataType.BYTE);
    }

    public ExpireAction getExpireAction(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return configManager.getDefaultExpireAction();
        }
        return getExpireAction(item.getItemMeta());
    }

    public ExpireAction getExpireAction(ItemMeta meta) {
        if (meta == null) {
            return configManager.getDefaultExpireAction();
        }
        String raw = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        return ExpireAction.fromString(raw, configManager.getDefaultExpireAction());
    }

    public void setExpireAction(ItemStack item, ExpireAction action) {
        if (item == null || item.getType() == Material.AIR || action == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action.name());
        item.setItemMeta(meta);
    }

    public int getExtensionsUsed(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return 0;
        }
        return getExtensionsUsed(item.getItemMeta());
    }

    public int getExtensionsUsed(ItemMeta meta) {
        if (meta == null) {
            return 0;
        }
        Integer val = meta.getPersistentDataContainer().get(extensionsKey, PersistentDataType.INTEGER);
        return val != null ? Math.max(0, val) : 0;
    }

    public Long getInactiveDuration(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(durationKey, PersistentDataType.LONG);
    }

    public Long getExpireTime(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(expireKey, PersistentDataType.LONG);
    }

    public boolean isExpired(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return isExpired(meta, System.currentTimeMillis());
    }

    public boolean isExpired(ItemMeta meta, long currentTime) {
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(sealedKey, PersistentDataType.BYTE)) {
            return true;
        }
        Long expireTime = pdc.get(expireKey, PersistentDataType.LONG);
        return expireTime != null && currentTime >= expireTime;
    }

    /**
     * Marks an expired item as SEALED and updates its lore once.
     *
     * @return true if the item was newly sealed, false if it was already sealed
     */
    public boolean sealItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return sealItem(item, meta);
    }

    public boolean sealItem(ItemStack item, ItemMeta meta) {
        if (item == null || meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(sealedKey, PersistentDataType.BYTE)) {
            return false; // Already sealed, avoid redundant setItemMeta!
        }

        pdc.set(sealedKey, PersistentDataType.BYTE, (byte) 1);
        updateSealedLore(meta);
        item.setItemMeta(meta);
        return true;
    }

    public void setInactiveDuration(ItemStack item, long durationMillis) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(durationKey, PersistentDataType.LONG, durationMillis);
        pdc.remove(expireKey);
        pdc.remove(sealedKey);

        updateInactiveLore(meta, durationMillis);
        item.setItemMeta(meta);
    }

    public void setExpireTime(ItemStack item, long expireTime) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        long maxDur = configManager.getMaxDurationMillis();
        long maxAllowed;
        try {
            maxAllowed = Math.addExact(System.currentTimeMillis(), maxDur);
        } catch (ArithmeticException e) {
            maxAllowed = Long.MAX_VALUE;
        }
        if (expireTime > maxAllowed) {
            expireTime = maxAllowed;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(expireKey, PersistentDataType.LONG, expireTime);
        pdc.remove(durationKey);
        pdc.remove(sealedKey);

        updateLore(meta, expireTime);
        item.setItemMeta(meta);
    }

    public boolean activateTimedItem(org.bukkit.entity.Player player, ItemStack item) {
        return activateTimedItem(player, item, true);
    }

    public boolean activateTimedItem(org.bukkit.entity.Player player, ItemStack item, boolean announce) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        if (!configManager.isActivationGameMode(player.getGameMode())) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !isInactiveTimedItem(meta)) {
            return false;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Long duration = pdc.get(durationKey, PersistentDataType.LONG);
        if (duration == null || duration <= 0) {
            return false;
        }

        long newExpiry = System.currentTimeMillis() + duration;

        pdc.remove(durationKey);
        pdc.remove(sealedKey);
        pdc.set(expireKey, PersistentDataType.LONG, newExpiry);

        updateLore(meta, newExpiry);
        item.setItemMeta(meta);

        if (announce) {
            String formattedDate = DateParser.formatDate(newExpiry, configManager.getDateFormat());
            String itemName = getItemDisplayName(item, meta);
            String msg = configManager.getMessage("item-activated")
                    .replace("{item}", itemName)
                    .replace("{expiry}", formattedDate);
            if (!msg.isEmpty()) {
                player.sendMessage(msg);
            }

            try {
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            } catch (Exception ignored) {
            }
        }

        return true;
    }

    public int activatePlayerInventory(org.bukkit.entity.Player player) {
        if (player == null || !player.isOnline() || !configManager.isActivationGameMode(player.getGameMode())) {
            return 0;
        }

        int activatedCount = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
                if (activateTimedItem(player, item, true)) {
                    player.getInventory().setItem(i, item);
                    activatedCount++;
                }
            }
        }
        return activatedCount;
    }

    public long addExpireTime(ItemStack item, long durationMillis) {
        long maxDur = configManager.getMaxDurationMillis();
        durationMillis = Math.min(durationMillis, maxDur);

        if (isInactiveTimedItem(item)) {
            Long current = getInactiveDuration(item);
            long base = (current != null ? current : 0L);
            long newDuration;
            try {
                newDuration = Math.addExact(base, durationMillis);
            } catch (ArithmeticException e) {
                newDuration = maxDur;
            }
            newDuration = Math.min(newDuration, maxDur);
            setInactiveDuration(item, newDuration);
            return newDuration;
        }

        Long current = getExpireTime(item);
        long now = System.currentTimeMillis();
        if (current != null) {
            // If item was already expired (e.g. sealed), base new expiry from current time
            long baseTime = Math.max(now, current);
            long maxAllowedExpiry;
            try {
                maxAllowedExpiry = Math.addExact(now, maxDur);
            } catch (ArithmeticException e) {
                maxAllowedExpiry = Long.MAX_VALUE;
            }

            long newExpiry;
            try {
                newExpiry = Math.addExact(baseTime, durationMillis);
            } catch (ArithmeticException e) {
                newExpiry = maxAllowedExpiry;
            }
            if (newExpiry > maxAllowedExpiry) {
                newExpiry = maxAllowedExpiry;
            }
            setExpireTime(item, newExpiry);
            return newExpiry;
        }

        // Newly added item: set as inactive duration waiting for survival/adventure activation!
        setInactiveDuration(item, durationMillis);
        return durationMillis;
    }

    /**
     * Applies an Extension Token to a timed item (active, inactive, or sealed)
     * and increments its extensions_used counter.
     */
    public long applyTokenExtension(ItemStack item, long durationMillis) {
        long newResult = addExpireTime(item, durationMillis);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            int used = getExtensionsUsed(meta) + 1;
            pdc.set(extensionsKey, PersistentDataType.INTEGER, used);
            item.setItemMeta(meta);
        }
        return newResult;
    }

    /**
     * Inspects container items (Shulker boxes, Bundles) and removes or seals expired items inside.
     *
     * @param item the container ItemStack to inspect
     * @return the total number of expired items removed/sealed inside the container
     */
    public int cleanContainers(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        int affected = 0;
        long now = System.currentTimeMillis();

        // 1. BlockStateMeta: Shulker Boxes, Chests, Barrels
        if (meta instanceof org.bukkit.inventory.meta.BlockStateMeta bsm) {
            if (bsm.hasBlockState() && bsm.getBlockState() instanceof org.bukkit.block.Container container) {
                org.bukkit.inventory.Inventory inv = container.getInventory();
                ItemStack[] contents = inv.getContents();
                boolean modified = false;
                for (int i = 0; i < contents.length; i++) {
                    ItemStack sub = contents[i];
                    if (sub != null && sub.getType() != Material.AIR && sub.hasItemMeta()) {
                        ItemMeta subMeta = sub.getItemMeta();
                        if (subMeta != null && isExpired(subMeta, now)) {
                            if (getExpireAction(subMeta) == ExpireAction.SEAL) {
                                if (sealItem(sub, subMeta)) {
                                    inv.setItem(i, sub);
                                    affected += sub.getAmount();
                                    modified = true;
                                }
                            } else {
                                inv.setItem(i, null);
                                affected += sub.getAmount();
                                modified = true;
                            }
                        }
                    }
                }
                if (modified) {
                    bsm.setBlockState(container);
                    item.setItemMeta(bsm);
                }
            }
        }

        // 2. BundleMeta: Bundles in 1.21
        if (meta instanceof org.bukkit.inventory.meta.BundleMeta bundleMeta) {
            List<ItemStack> bundleItems = bundleMeta.getItems();
            if (bundleItems != null && !bundleItems.isEmpty()) {
                List<ItemStack> newItems = new ArrayList<>();
                boolean modified = false;
                for (ItemStack sub : bundleItems) {
                    if (sub != null && sub.getType() != Material.AIR && sub.hasItemMeta()) {
                        ItemMeta subMeta = sub.getItemMeta();
                        if (subMeta != null && isExpired(subMeta, now)) {
                            if (getExpireAction(subMeta) == ExpireAction.SEAL) {
                                if (sealItem(sub, subMeta)) {
                                    affected += sub.getAmount();
                                    modified = true;
                                }
                                newItems.add(sub);
                                continue;
                            } else {
                                affected += sub.getAmount();
                                modified = true;
                                continue;
                            }
                        }
                    }
                    newItems.add(sub);
                }
                if (modified) {
                    bundleMeta.setItems(newItems);
                    item.setItemMeta(bundleMeta);
                }
            }
        }

        return affected;
    }

    /**
     * Checks if a timed item is missing its lore (e.g. wiped by MMOItems updater)
     * and restores the expiration lore if PDC data exists.
     */
    public boolean ensureTimedLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean sealed = pdc.has(sealedKey, PersistentDataType.BYTE);
        Long expire = pdc.get(expireKey, PersistentDataType.LONG);
        Long duration = pdc.get(durationKey, PersistentDataType.LONG);
        if (!sealed && expire == null && duration == null) {
            return false;
        }

        String savedLore = pdc.get(loreKey, PersistentDataType.STRING);
        List<String> lore = meta.getLore();
        boolean needsUpdate = (lore == null || lore.isEmpty())
                || (savedLore != null && !lore.contains(savedLore));

        if (needsUpdate) {
            if (sealed) {
                updateSealedLore(meta);
            } else if (expire != null) {
                updateLore(meta, expire);
            } else if (duration != null) {
                updateInactiveLore(meta, duration);
            }
            item.setItemMeta(meta);
            return true;
        }
        return false;
    }

    public Long removeExpireTime(ItemStack item, long durationMillis) {
        if (isInactiveTimedItem(item)) {
            Long current = getInactiveDuration(item);
            if (current == null) return null;
            long newDuration = current - durationMillis;
            if (newDuration <= 0) {
                clearExpireTime(item);
                return -1L;
            }
            setInactiveDuration(item, newDuration);
            return newDuration;
        }

        Long current = getExpireTime(item);
        if (current == null) {
            return null;
        }
        long newExpiry = current - durationMillis;
        if (newExpiry <= System.currentTimeMillis()) {
            return -1L; // Item expired
        }
        setExpireTime(item, newExpiry);
        return newExpiry;
    }

    public boolean clearExpireTime(ItemStack item) {
        if (!isTimedItem(item)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(expireKey);
        pdc.remove(durationKey);
        pdc.remove(loreKey);
        pdc.remove(actionKey);
        pdc.remove(sealedKey);
        pdc.remove(extensionsKey);

        removeLore(meta);
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Prepares a clean copy of an item to be stored in saved_items.yml via /ti save <id>.
     * Strips active timer tags and expiration lore, while preserving custom expire_action if set.
     */
    public ItemStack prepareTemplateForSave(ItemStack rawItem) {
        ItemStack copy = rawItem.clone();
        copy.setAmount(1);
        if (!copy.hasItemMeta()) {
            return copy;
        }
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(expireKey);
        pdc.remove(durationKey);
        pdc.remove(sealedKey);
        pdc.remove(extensionsKey);
        removeLore(meta);
        copy.setItemMeta(meta);
        return copy;
    }

    public void updateLore(ItemMeta meta, long expireTime) {
        String formattedDate = DateParser.formatDate(expireTime, configManager.getDateFormat());
        String loreTemplate = configManager.getLoreFormat();
        if (loreTemplate == null || loreTemplate.trim().isEmpty()) {
            loreTemplate = "&cExpires at: &e{expiry}";
        }
        if (!loreTemplate.startsWith("&") && !loreTemplate.startsWith("§")) {
            loreTemplate = "&c" + loreTemplate;
        }

        String newLoreText = ColorUtil.colorize(loreTemplate.replace("{expiry}", formattedDate));
        replaceOrAddTimedLoreLine(meta, newLoreText);
    }

    public void updateInactiveLore(ItemMeta meta, long durationMillis) {
        String formattedDuration = TimeParser.formatDuration(durationMillis);
        String template = configManager.getLoreInactiveFormat();
        if (template == null || template.trim().isEmpty()) {
            template = "&eDuration: &a{duration}";
        }
        if (!template.startsWith("&") && !template.startsWith("§")) {
            template = "&e" + template;
        }

        String newLoreText = ColorUtil.colorize(template.replace("{duration}", formattedDuration));
        replaceOrAddTimedLoreLine(meta, newLoreText);
    }

    public void updateSealedLore(ItemMeta meta) {
        String template = configManager.getLoreSealedFormat();
        if (template == null || template.trim().isEmpty()) {
            template = "&c&l[EXPIRED - SEALED]";
        }
        String newLoreText = ColorUtil.colorize(template);
        replaceOrAddTimedLoreLine(meta, newLoreText);
    }

    private void replaceOrAddTimedLoreLine(ItemMeta meta, String newLoreText) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String oldSavedLore = pdc.get(loreKey, PersistentDataType.STRING);
        pdc.set(loreKey, PersistentDataType.STRING, newLoreText);

        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(lore);
        }

        int targetIndex = findTimedLoreLineIndex(lore, oldSavedLore);
        if (targetIndex >= 0) {
            lore.set(targetIndex, newLoreText);
        } else {
            lore.add(newLoreText);
        }

        meta.setLore(lore);
    }

    public void removeLore(ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String savedLore = pdc.get(loreKey, PersistentDataType.STRING);
        pdc.remove(loreKey);

        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            return;
        }

        String activePrefix = getStrippedPrefix(configManager.getLoreFormat(), "{expiry}");
        String inactivePrefix = getStrippedPrefix(configManager.getLoreInactiveFormat(), "{duration}");
        String sealedStripped = configManager.getLoreSealedFormat() != null
                ? ColorUtil.stripColor(ColorUtil.colorize(configManager.getLoreSealedFormat())).trim()
                : "";

        List<String> newLore = new ArrayList<>();
        for (String line : lore) {
            if (isMatchingTimedLoreLine(line, savedLore, activePrefix, inactivePrefix, sealedStripped)) {
                continue;
            }
            newLore.add(line);
        }

        meta.setLore(newLore.isEmpty() ? null : newLore);
    }

    private int findTimedLoreLineIndex(List<String> lore, String oldSavedLore) {
        String activePrefix = getStrippedPrefix(configManager.getLoreFormat(), "{expiry}");
        String inactivePrefix = getStrippedPrefix(configManager.getLoreInactiveFormat(), "{duration}");
        String sealedStripped = configManager.getLoreSealedFormat() != null
                ? ColorUtil.stripColor(ColorUtil.colorize(configManager.getLoreSealedFormat())).trim()
                : "";

        for (int i = 0; i < lore.size(); i++) {
            if (isMatchingTimedLoreLine(lore.get(i), oldSavedLore, activePrefix, inactivePrefix, sealedStripped)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isMatchingTimedLoreLine(
            String line,
            String oldSavedLore,
            String activePrefix,
            String inactivePrefix,
            String sealedStripped
    ) {
        if (oldSavedLore != null && line.equals(oldSavedLore)) {
            return true;
        }
        if (line.contains("§t§i§m§e") || line.contains(OLD_LORE_MARKER)) {
            return true;
        }
        String strippedLine = ColorUtil.stripColor(line).trim();
        if (!activePrefix.isEmpty() && strippedLine.startsWith(activePrefix)) {
            return true;
        }
        if (!inactivePrefix.isEmpty() && strippedLine.startsWith(inactivePrefix)) {
            return true;
        }
        if (!sealedStripped.isEmpty() && strippedLine.equals(sealedStripped)) {
            return true;
        }
        return false;
    }

    private String getStrippedPrefix(String template, String placeholder) {
        if (template == null || !template.contains(placeholder)) {
            return "";
        }
        String before = template.substring(0, template.indexOf(placeholder));
        return ColorUtil.stripColor(ColorUtil.colorize(before)).trim();
    }

    public String getItemDisplayName(ItemStack item) {
        if (item == null) {
            return "Unknown";
        }
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        return getItemDisplayName(item, meta);
    }

    public String getItemDisplayName(ItemStack item, ItemMeta meta) {
        if (item == null) {
            return "Unknown";
        }
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }

        String materialName = item.getType().name().replace('_', ' ').toLowerCase();
        return Arrays.stream(materialName.split(" "))
                .map(word -> word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    public boolean isExtensionToken(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(tokenKey, PersistentDataType.BYTE);
    }

    public Long getTokenDuration(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(tokenKey, PersistentDataType.BYTE)) {
            return null;
        }
        return meta.getPersistentDataContainer().get(tokenDurationKey, PersistentDataType.LONG);
    }

    public ItemStack createExtensionToken(long durationMillis, int amount) {
        Material material = configManager.getTokenMaterial();
        ItemStack token = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = token.getItemMeta();
        if (meta == null) {
            return token;
        }

        if (configManager.getTokenCustomModelData() > 0) {
            meta.setCustomModelData(configManager.getTokenCustomModelData());
        }

        if (configManager.isTokenGlow()) {
            try {
                meta.setEnchantmentGlintOverride(true);
            } catch (Throwable ignored) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
        }

        String formattedDuration = TimeParser.formatDuration(durationMillis);
        String nameFormat = configManager.getTokenName();
        if (nameFormat != null && !nameFormat.isEmpty()) {
            meta.setDisplayName(ColorUtil.colorize(nameFormat.replace("{duration}", formattedDuration)));
        }

        List<String> rawLore = configManager.getTokenLore();
        if (rawLore != null && !rawLore.isEmpty()) {
            List<String> coloredLore = rawLore.stream()
                    .map(line -> ColorUtil.colorize(line.replace("{duration}", formattedDuration)))
                    .collect(Collectors.toList());
            meta.setLore(coloredLore);
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(tokenKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(tokenDurationKey, PersistentDataType.LONG, durationMillis);

        token.setItemMeta(meta);
        return token;
    }

    public String getStatusFormatted(ItemStack item) {
        TimedData data = getTimedData(item);
        return getStatusFormatted(data);
    }

    public String getStatusFormatted(TimedData data) {
        if (!data.isTimed()) {
            return configManager.getStatusNone();
        }
        if (data.isSealed()) {
            return configManager.getStatusSealed();
        }
        if (data.isExpired()) {
            return configManager.getStatusExpired();
        }
        if (data.isInactive()) {
            return configManager.getStatusInactive();
        }
        return configManager.getStatusActive();
    }
}
