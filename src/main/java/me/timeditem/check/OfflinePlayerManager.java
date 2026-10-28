package me.timeditem.check;

import me.timeditem.TimedItem;
import me.timeditem.item.ExpireAction;
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

    private final Map<String, Long> lastScannedModified = new ConcurrentHashMap<>();

    /**
     * Scans offline player data files asynchronously and removes expired items.
     * Thread-safe: worldFolder and onlineUUIDs must be captured on the main thread before dispatching.
     *
     * @param worldFolder the server world folder containing playerdata
     * @param onlineUUIDs set of UUIDs of currently online players to skip
     */
    public void scanOfflinePlayers(File worldFolder, java.util.Set<UUID> onlineUUIDs) {
        if (worldFolder == null) {
            return;
        }
        File playerDataFolder = new File(worldFolder, "playerdata");
        if (!playerDataFolder.exists() || !playerDataFolder.isDirectory()) {
            return;
        }

        File[] files = playerDataFolder.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null || files.length == 0) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean anyModified = false;

        for (File file : files) {
            String name = file.getName();
            String uuidStr = name.substring(0, name.length() - 4);
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue; // Not a valid UUID file
            }

            // Skip online players - they are checked live in memory
            if (onlineUUIDs != null && onlineUUIDs.contains(uuid)) {
                continue;
            }

            long lastMod = file.lastModified();
            // Skip files touched in the last 5 minutes (player just logged off / server saving)
            if (now - lastMod < 300_000L) {
                continue;
            }

            // Skip files whose lastModified has not changed since our last scan
            Long previousMod = lastScannedModified.get(name);
            if (previousMod != null && previousMod == lastMod) {
                continue;
            }

            try {
                long modBeforeRead = file.lastModified();
                NBTCompound root = NBTIO.readFile(file);
                int removedCount = 0;

                removedCount += removeExpiredFromList(root.getList("Inventory"), now);
                removedCount += removeExpiredFromList(root.getList("EnderItems"), now);

                if (removedCount > 0) {
                    // Race condition guard: verify file was not modified while parsing
                    if (file.lastModified() != modBeforeRead) {
                        plugin.getLogger().warning("Skipped saving offline data for " + uuid + " (file was modified during scan)");
                        continue;
                    }

                    NBTIO.writeFile(root, file);
                    lastScannedModified.put(name, file.lastModified());
                    offlineExpiredCounts.merge(uuid, removedCount, Integer::sum);
                    anyModified = true;
                    plugin.getLogger().info("Removed " + removedCount + " expired item(s) from offline player " + uuid);
                } else {
                    lastScannedModified.put(name, lastMod);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to process offline player data for " + uuidStr, e);
            }
        }

        // Batch save all recorded counts once at the end of the scan cycle
        if (anyModified) {
            saveData();
        }
    }

    public void scanOfflinePlayers() {
        // Fallback for direct invocations (captures Bukkit state safely)
        File worldFolder = Bukkit.getWorlds().isEmpty() ? new File("world") : Bukkit.getWorlds().get(0).getWorldFolder();
        java.util.Set<UUID> onlineUUIDs = new java.util.HashSet<>();
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            onlineUUIDs.add(p.getUniqueId());
        }
        scanOfflinePlayers(worldFolder, onlineUUIDs);
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
                ExpireAction defaultAction = (plugin.getConfigManager() != null)
                        ? plugin.getConfigManager().getDefaultExpireAction()
                        : ExpireAction.DELETE;
                ExpireAction itemAction = extractExpireAction(itemCompound, defaultAction);
                if (itemAction == ExpireAction.SEAL) {
                    continue; // SEAL items are preserved and sealed when player joins
                }
                iterator.remove();
                removed++;
            }
        }

        return removed;
    }

    private ExpireAction extractExpireAction(NBTCompound itemCompound, ExpireAction fallback) {
        NBTCompound pbv = getPublicBukkitValues(itemCompound);
        if (pbv != null) {
            String raw = pbv.getString("timeditem:expire_action");
            return ExpireAction.fromString(raw, fallback);
        }
        return fallback;
    }

    private Long extractExpireTime(NBTCompound itemCompound) {
        NBTCompound pbv = getPublicBukkitValues(itemCompound);
        return pbv != null ? pbv.getLong("timeditem:expire_time") : null;
    }

    private NBTCompound getPublicBukkitValues(NBTCompound itemCompound) {
        // Modern 1.20.5+ / 1.21: components -> minecraft:custom_data -> PublicBukkitValues
        NBTCompound components = itemCompound.getCompound("components");
        if (components != null) {
            NBTCompound customData = components.getCompound("minecraft:custom_data");
            if (customData != null) {
                NBTCompound publicBukkitValues = customData.getCompound("PublicBukkitValues");
                if (publicBukkitValues != null) {
                    return publicBukkitValues;
                }
            }
        }

        // Legacy: tag -> PublicBukkitValues
        NBTCompound legacyTag = itemCompound.getCompound("tag");
        if (legacyTag != null) {
            NBTCompound publicBukkitValues = legacyTag.getCompound("PublicBukkitValues");
            if (publicBukkitValues != null) {
                return publicBukkitValues;
            }
        }

        // Direct PublicBukkitValues
        return itemCompound.getCompound("PublicBukkitValues");
    }
}
