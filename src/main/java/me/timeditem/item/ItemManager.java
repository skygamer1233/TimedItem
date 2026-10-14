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
    private final NamespacedKey tokenKey;
    private final NamespacedKey tokenDurationKey;

    public ItemManager(TimedItem plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.expireKey = new NamespacedKey(plugin, "expire_time");
        this.durationKey = new NamespacedKey(plugin, "duration_time");
        this.loreKey = new NamespacedKey(plugin, "lore_line");
        this.tokenKey = new NamespacedKey(plugin, "extension_token");
        this.tokenDurationKey = new NamespacedKey(plugin, "token_duration");
    }

    public NamespacedKey getExpireKey() {
        return expireKey;
    }

    public NamespacedKey getDurationKey() {
        return durationKey;
    }

    public boolean isTimedItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(expireKey, PersistentDataType.LONG) || pdc.has(durationKey, PersistentDataType.LONG);
    }

    public boolean isInactiveTimedItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(durationKey, PersistentDataType.LONG);
    }

    public Long getInactiveDuration(ItemStack item) {
        if (!isInactiveTimedItem(item)) {
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
        Long expireTime = getExpireTime(item);
        return expireTime != null && System.currentTimeMillis() >= expireTime;
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

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(expireKey, PersistentDataType.LONG, expireTime);
        pdc.remove(durationKey);

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
        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
            return false;
        }
        if (!isInactiveTimedItem(item)) {
            return false;
        }

        Long duration = getInactiveDuration(item);
        if (duration == null || duration <= 0) {
            return false;
        }

        long newExpiry = System.currentTimeMillis() + duration;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(durationKey);
        pdc.set(expireKey, PersistentDataType.LONG, newExpiry);

        updateLore(meta, newExpiry);
        item.setItemMeta(meta);

        if (announce) {
            String formattedDate = DateParser.formatDate(newExpiry, configManager.getDateFormat());
            String itemName = getItemDisplayName(item);
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
        if (player == null || !player.isOnline() || player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
            return 0;
        }

        int activatedCount = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR && isInactiveTimedItem(item)) {
                if (activateTimedItem(player, item, true)) {
                    player.getInventory().setItem(i, item);
                    activatedCount++;
                }
            }
        }
        return activatedCount;
    }

    public long addExpireTime(ItemStack item, long durationMillis) {
        if (isInactiveTimedItem(item)) {
            Long current = getInactiveDuration(item);
            long newDuration = (current != null ? current : 0L) + durationMillis;
            setInactiveDuration(item, newDuration);
            return newDuration;
        }

        Long current = getExpireTime(item);
        if (current != null) {
            long newExpiry = current + durationMillis;
            setExpireTime(item, newExpiry);
            return newExpiry;
        }

        // Newly added item: set as inactive duration waiting for survival activation!
        setInactiveDuration(item, durationMillis);
        return durationMillis;
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

        removeLore(meta);
        item.setItemMeta(meta);
        return true;
    }

    public void updateLore(ItemMeta meta, long expireTime) {
        String formattedDate = DateParser.formatDate(expireTime, configManager.getDateFormat());
        String loreTemplate = configManager.getLoreFormat();
        if (loreTemplate == null || loreTemplate.trim().isEmpty()) {
            loreTemplate = "&cExpires: &e{expiry}";
        }

        // If template doesn't specify a starting color code, default to &c so text is bright and visible
        if (!loreTemplate.startsWith("&") && !loreTemplate.startsWith("§")) {
            loreTemplate = "&c" + loreTemplate;
        }

        String newLoreText = ColorUtil.colorize(loreTemplate.replace("{expiry}", formattedDate));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String oldSavedLore = pdc.get(loreKey, PersistentDataType.STRING);
        pdc.set(loreKey, PersistentDataType.STRING, newLoreText);

        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(lore);
        }

        int targetIndex = -1;
        String strippedTemplatePrefix = (loreTemplate.contains("{expiry}"))
                ? ColorUtil.stripColor(loreTemplate.split("\\{expiry}")[0]).trim()
                : "";

        String inactiveTemplate = configManager.getLoreInactiveFormat();
        String strippedInactivePrefix = (inactiveTemplate != null && inactiveTemplate.contains("{duration}"))
                ? ColorUtil.stripColor(inactiveTemplate.split("\\{duration}")[0]).trim()
                : "";

        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            // Match previously stored lore line
            if (oldSavedLore != null && line.equals(oldSavedLore)) {
                targetIndex = i;
                break;
            }
            // Match legacy marker from earlier versions
            if (line.contains("§t§i§m§e") || line.contains(OLD_LORE_MARKER)) {
                targetIndex = i;
                break;
            }
            // Match stripped prefix of active format (e.g. "Hết hạn vào:" or "Expires:")
            if (!strippedTemplatePrefix.isEmpty()) {
                String strippedLine = ColorUtil.stripColor(line).trim();
                if (strippedLine.startsWith(strippedTemplatePrefix)) {
                    targetIndex = i;
                    break;
                }
            }
            // Match stripped prefix of inactive format (e.g. "Hiệu lực:")
            if (!strippedInactivePrefix.isEmpty()) {
                String strippedLine = ColorUtil.stripColor(line).trim();
                if (strippedLine.startsWith(strippedInactivePrefix)) {
                    targetIndex = i;
                    break;
                }
            }
        }

        if (targetIndex >= 0) {
            lore.set(targetIndex, newLoreText);
        } else {
            lore.add(newLoreText);
        }

        meta.setLore(lore);
    }

    public void updateInactiveLore(ItemMeta meta, long durationMillis) {
        String formattedDuration = TimeParser.formatDuration(durationMillis);
        String template = configManager.getLoreInactiveFormat();
        if (template == null || template.trim().isEmpty()) {
            template = "&eHiệu lực: &a{duration}";
        }
        if (!template.startsWith("&") && !template.startsWith("§")) {
            template = "&e" + template;
        }

        String newLoreText = ColorUtil.colorize(template.replace("{duration}", formattedDuration));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String oldSavedLore = pdc.get(loreKey, PersistentDataType.STRING);
        pdc.set(loreKey, PersistentDataType.STRING, newLoreText);

        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(lore);
        }

        int targetIndex = -1;
        String strippedTemplatePrefix = (template.contains("{duration}"))
                ? ColorUtil.stripColor(template.split("\\{duration}")[0]).trim()
                : "";

        String activeTemplate = configManager.getLoreFormat();
        String strippedActivePrefix = (activeTemplate != null && activeTemplate.contains("{expiry}"))
                ? ColorUtil.stripColor(activeTemplate.split("\\{expiry}")[0]).trim()
                : "";

        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (oldSavedLore != null && line.equals(oldSavedLore)) {
                targetIndex = i;
                break;
            }
            if (line.contains("§t§i§m§e") || line.contains(OLD_LORE_MARKER)) {
                targetIndex = i;
                break;
            }
            if (!strippedTemplatePrefix.isEmpty() && ColorUtil.stripColor(line).trim().startsWith(strippedTemplatePrefix)) {
                targetIndex = i;
                break;
            }
            if (!strippedActivePrefix.isEmpty() && ColorUtil.stripColor(line).trim().startsWith(strippedActivePrefix)) {
                targetIndex = i;
                break;
            }
        }

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

        String loreTemplate = configManager.getLoreFormat();
        String strippedTemplatePrefix = (loreTemplate != null && loreTemplate.contains("{expiry}"))
                ? ColorUtil.stripColor(loreTemplate.split("\\{expiry}")[0]).trim()
                : "";

        String inactiveTemplate = configManager.getLoreInactiveFormat();
        String strippedInactivePrefix = (inactiveTemplate != null && inactiveTemplate.contains("{duration}"))
                ? ColorUtil.stripColor(inactiveTemplate.split("\\{duration}")[0]).trim()
                : "";

        List<String> newLore = new ArrayList<>();
        for (String line : lore) {
            if (savedLore != null && line.equals(savedLore)) {
                continue;
            }
            if (line.contains("§t§i§m§e") || line.contains(OLD_LORE_MARKER)) {
                continue;
            }
            if (!strippedTemplatePrefix.isEmpty()) {
                String strippedLine = ColorUtil.stripColor(line).trim();
                if (strippedLine.startsWith(strippedTemplatePrefix)) {
                    continue;
                }
            }
            if (!strippedInactivePrefix.isEmpty()) {
                String strippedLine = ColorUtil.stripColor(line).trim();
                if (strippedLine.startsWith(strippedInactivePrefix)) {
                    continue;
                }
            }
            newLore.add(line);
        }

        meta.setLore(newLore.isEmpty() ? null : newLore);
    }

    public String getItemDisplayName(ItemStack item) {
        if (item == null) {
            return "Unknown";
        }
        if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
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
        if (!isExtensionToken(item)) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
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
        if (!isTimedItem(item)) {
            return configManager.getStatusNone();
        }
        if (isExpired(item)) {
            return configManager.getStatusExpired();
        }
        if (isInactiveTimedItem(item)) {
            return configManager.getStatusInactive();
        }
        return configManager.getStatusActive();
    }
}
