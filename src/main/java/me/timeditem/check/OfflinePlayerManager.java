package me.timeditem.check;

import me.timeditem.TimedItem;
import me.timeditem.nbt.NBTCompound;
import me.timeditem.nbt.NBTIO;
import me.timeditem.nbt.NBTList;
import me.timeditem.nbt.NBTTag;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class OfflinePlayerManager {
    private final TimedItem plugin;
    private final File dataFile;
    private final Map<UUID, Integer> offlineExpiredCounts = new ConcurrentHashMap<>();

    public OfflinePlayerManager(TimedItem plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "offline_expired.yml");
        loadData();
    }

    private synchronized void loadData() {
        offlineExpiredCounts.clear();
        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int count = config.getInt(key, 0);
                if (count > 0) {
                    offlineExpiredCounts.put(uuid, count);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void saveData() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : offlineExpiredCounts.entrySet()) {
            if (entry.getValue() > 0) {
                config.set(entry.getKey().toString(), entry.getValue());
            }
        }
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save offline_expired.yml", e);
        }
    }

    public synchronized void addOfflineExpired(UUID uuid, int count) {
        if (count <= 0) return;
        offlineExpiredCounts.merge(uuid, count, Integer::sum);
        saveData();
    }

    public synchronized int consumeOfflineExpired(UUID uuid) {
        Integer count = offlineExpiredCounts.remove(uuid);
        if (count != null && count > 0) {
            saveData();
            return count;
        }
        return 0;
    }

    /**
     * Scans offline player data files asynchronously and removes expired items.
     */
    public void scanOfflinePlayers() {
        File worldFolder = Bukkit.getWorlds().isEmpty() ? new File("world") : Bukkit.getWorlds().get(0).getWorldFolder();
        File playerDataFolder = new File(worldFolder, "playerdata");

        if (!playerDataFolder.exists() || !playerDataFolder.isDirectory()) {
            return;
        }

        File[] files = playerDataFolder.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null || files.length == 0) {
            return;
        }

        long now = System.currentTimeMillis();

        for (File file : files) {
            String name = file.getName();
            String uuidStr = name.substring(0, name.length() - 4);
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue; // Not a valid UUID file
            }

            // Skip online players - they will be checked in memory and saved properly by the server
            if (Bukkit.getPlayer(uuid) != null) {
                continue;
            }

            try {
                NBTCompound root = NBTIO.readFile(file);
                int removedCount = 0;

                removedCount += removeExpiredFromList(root.getList("Inventory"), now);
                removedCount += removeExpiredFromList(root.getList("EnderItems"), now);

                if (removedCount > 0) {
                    NBTIO.writeFile(root, file);
                    addOfflineExpired(uuid, removedCount);
                    plugin.getLogger().info("Removed " + removedCount + " expired item(s) from offline player " + uuid);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to process offline player data for " + uuidStr, e);
            }
        }
    }

    private int removeExpiredFromList(NBTList list, long now) {
        if (list == null || list.isEmpty()) {
            return 0;
        }

        int removed = 0;
        Iterator<NBTTag> iterator = list.iterator();
        while (iterator.hasNext()) {
            NBTTag tag = iterator.next();
            if (!(tag instanceof NBTCompound itemCompound)) {
                continue;
            }

            Long expireTime = extractExpireTime(itemCompound);
            if (expireTime != null && now >= expireTime) {
                iterator.remove();
                removed++;
            }
        }

        return removed;
    }

    private Long extractExpireTime(NBTCompound itemCompound) {
        // Modern 1.20.5+ / 1.21: components -> minecraft:custom_data -> PublicBukkitValues -> timeditem:expire_time
        NBTCompound components = itemCompound.getCompound("components");
        if (components != null) {
            NBTCompound customData = components.getCompound("minecraft:custom_data");
            if (customData != null) {
                NBTCompound publicBukkitValues = customData.getCompound("PublicBukkitValues");
                if (publicBukkitValues != null) {
                    Long expire = publicBukkitValues.getLong("timeditem:expire_time");
                    if (expire != null) return expire;
                }
            }
        }

        // Legacy: tag -> PublicBukkitValues -> timeditem:expire_time
        NBTCompound legacyTag = itemCompound.getCompound("tag");
        if (legacyTag != null) {
            NBTCompound publicBukkitValues = legacyTag.getCompound("PublicBukkitValues");
            if (publicBukkitValues != null) {
                Long expire = publicBukkitValues.getLong("timeditem:expire_time");
                if (expire != null) return expire;
            }
        }

        // Direct PublicBukkitValues
        NBTCompound directValues = itemCompound.getCompound("PublicBukkitValues");
        if (directValues != null) {
            return directValues.getLong("timeditem:expire_time");
        }

        return null;
    }
}
