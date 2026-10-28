package me.timeditem.item;

import me.timeditem.TimedItem;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages custom item templates saved via /ti save <id>.
 * Keeps templates cached in memory for zero-disk-I/O retrieval during gameplay.
 * Only creates saved_items.yml on disk when at least one item is saved.
 */
public class SavedItemManager {
    private final TimedItem plugin;
    private final ItemManager itemManager;
    private final File file;
    private final Map<String, ItemStack> savedItems = new ConcurrentHashMap<>();

    public SavedItemManager(TimedItem plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.file = new File(plugin.getDataFolder(), "saved_items.yml");
        reload();
    }

    public synchronized void reload() {
        savedItems.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("items");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            ItemStack item = section.getItemStack(key);
            if (item != null && !item.getType().isAir()) {
                savedItems.put(key.toLowerCase(), item);
            }
        }
    }

    public synchronized boolean saveItem(String id, ItemStack rawItem) {
        if (id == null || id.isBlank() || rawItem == null || rawItem.getType().isAir()) {
            return false;
        }

        String normalizedId = id.trim().toLowerCase();
        ItemStack cleanCopy = itemManager.prepareTemplateForSave(rawItem);
        savedItems.put(normalizedId, cleanCopy);
        return saveToDisk();
    }

    public synchronized boolean deleteItem(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        String normalizedId = id.trim().toLowerCase();
        ItemStack removed = savedItems.remove(normalizedId);
        if (removed == null) {
            return false;
        }

        if (savedItems.isEmpty()) {
            if (file.exists()) {
                file.delete();
            }
            return true;
        }

        return saveToDisk();
    }

    public ItemStack getItem(String id, int amount) {
        if (id == null) {
            return null;
        }
        ItemStack template = savedItems.get(id.trim().toLowerCase());
        if (template == null) {
            return null;
        }
        ItemStack clone = template.clone();
        clone.setAmount(Math.max(1, Math.min(amount, clone.getMaxStackSize())));
        return clone;
    }

    public boolean hasItem(String id) {
        return id != null && savedItems.containsKey(id.trim().toLowerCase());
    }

    public Set<String> getSavedIds() {
        return Collections.unmodifiableSet(new TreeSet<>(savedItems.keySet()));
    }

    public Map<String, ItemStack> getAllSavedItems() {
        return Collections.unmodifiableMap(new TreeMap<>(savedItems));
    }

    private synchronized boolean saveToDisk() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, ItemStack> entry : savedItems.entrySet()) {
            config.set("items." + entry.getKey(), entry.getValue());
        }
        try {
            config.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save saved_items.yml", e);
            return false;
        }
    }
}
