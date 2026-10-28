package me.timeditem.config;

import me.timeditem.TimedItem;
import me.timeditem.item.ExpireAction;
import me.timeditem.util.ColorUtil;
import me.timeditem.util.TimeParser;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ConfigManager {
    private final TimedItem plugin;
    private FileConfiguration config;
    private FileConfiguration langConfig;

    private String currentLoadedLanguage = null;
    private String language;
    private String dateFormat;
    private ExpireAction defaultExpireAction;
    private int checkInterval;
    private int offlineCheckInterval;
    private int equipmentCheckInterval;
    private long maxDurationMillis;
    private boolean worldCheck;
    private boolean inventoryCheck;
    private boolean offlineCheck;
    private boolean containerCheck;
    private final java.util.Set<org.bukkit.GameMode> activationGameModes = java.util.EnumSet.noneOf(org.bukkit.GameMode.class);
    private int helpPerPage;
    private String loreFormat;
    private String loreInactiveFormat;
    private String loreSealedFormat;

    // Expiry warning configuration
    private boolean expiryWarningEnabled;
    private List<Integer> expiryWarningTimes = Collections.emptyList();
    private boolean expiryWarningActionbar;
    private boolean expiryWarningChat;
    private Sound expiryWarningSound;

    // Token configuration
    private Material tokenMaterial;
    private int tokenCustomModelData;
    private boolean tokenGlow;
    private int tokenMaxExtensions;
    private boolean tokenAllowOnSealed;
    private String tokenName;
    private List<String> tokenLore;

    public ConfigManager(TimedItem plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        // Read active language from disk (default: "en")
        YamlConfiguration tempConfig = YamlConfiguration.loadConfiguration(configFile);
        String diskLang = tempConfig.getString("language", "en").toLowerCase();

        // If the language was explicitly changed by admin, sync comments & structure
        if (this.currentLoadedLanguage != null && !this.currentLoadedLanguage.equalsIgnoreCase(diskLang)) {
            ConfigUpdater.syncConfigLanguage(plugin, configFile, diskLang);
        } else {
            // Standard reload: merge missing keys while preserving admin comments
            ConfigUpdater.update(plugin, "config.yml", configFile);
        }
        this.language = diskLang;
        this.currentLoadedLanguage = diskLang;

        // Reload Bukkit plugin config
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // Load active language file from lang/ subdirectory
        loadLangConfig();

        this.dateFormat = config.getString("date-format", "dd/MM/yyyy HH:mm:ss");
        this.defaultExpireAction = ExpireAction.fromString(
                config.getString("default-expire-action", "DELETE"),
                ExpireAction.DELETE
        );
        this.checkInterval = config.getInt("check-interval", 30);
        this.offlineCheckInterval = Math.max(10, config.getInt("offline-check-interval", 1800));
        this.equipmentCheckInterval = Math.max(1, config.getInt("equipment-check-interval", 2));
        this.worldCheck = config.getBoolean("world-check", true);
        this.inventoryCheck = config.getBoolean("inventory-check", true);
        this.offlineCheck = config.getBoolean("offline-check", false);
        this.containerCheck = config.getBoolean("container-check", true);
        this.helpPerPage = config.getInt("help-per-page", 8);

        // Parse max duration
        String maxDurStr = config.getString("max-duration", "3650d");
        try {
            this.maxDurationMillis = TimeParser.parseDuration(maxDurStr);
        } catch (Exception e) {
            this.maxDurationMillis = 3650L * 86400000L;
        }

        // Parse activation gamemodes
        this.activationGameModes.clear();
        List<String> gmList = config.getStringList("activation-gamemodes");
        if (gmList.isEmpty()) {
            this.activationGameModes.add(org.bukkit.GameMode.SURVIVAL);
            this.activationGameModes.add(org.bukkit.GameMode.ADVENTURE);
        } else {
            for (String gmName : gmList) {
                try {
                    this.activationGameModes.add(org.bukkit.GameMode.valueOf(gmName.toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        // Parse expiry warning settings
        this.expiryWarningEnabled = config.getBoolean("expiry-warning.enabled", true);
        List<Integer> rawTimes = config.getIntegerList("expiry-warning.times");
        if (rawTimes.isEmpty()) {
            this.expiryWarningTimes = Arrays.asList(300, 60, 10);
        } else {
            List<Integer> sorted = new ArrayList<>();
            for (int t : rawTimes) {
                if (t > 0) sorted.add(t);
            }
            sorted.sort(Collections.reverseOrder());
            this.expiryWarningTimes = Collections.unmodifiableList(sorted);
        }
        this.expiryWarningActionbar = config.getBoolean("expiry-warning.actionbar", true);
        this.expiryWarningChat = config.getBoolean("expiry-warning.chat", true);
        String soundName = config.getString("expiry-warning.sound", "BLOCK_NOTE_BLOCK_PLING");
        this.expiryWarningSound = null;
        if (soundName != null && !soundName.isBlank()) {
            try {
                this.expiryWarningSound = Sound.valueOf(soundName.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        // Allow language files to provide lore formats
        if (langConfig.contains("lore-format")) {
            this.loreFormat = langConfig.getString("lore-format");
        } else {
            this.loreFormat = config.getString("lore-format", "&cExpires at: &e{expiry}");
        }

        if (langConfig.contains("lore-inactive-format")) {
            this.loreInactiveFormat = langConfig.getString("lore-inactive-format");
        } else {
            this.loreInactiveFormat = config.getString("lore-inactive-format", "&eDuration: &a{duration}");
        }

        if (langConfig.contains("lore-sealed-format")) {
            this.loreSealedFormat = langConfig.getString("lore-sealed-format");
        } else {
            this.loreSealedFormat = config.getString("lore-sealed-format", "&c&l[EXPIRED - SEALED]");
        }

        // Token settings
        String matName = config.getString("token.material", "PAPER");
        Material mat = Material.matchMaterial(matName != null ? matName : "PAPER");
        this.tokenMaterial = mat != null ? mat : Material.PAPER;
        this.tokenCustomModelData = config.getInt("token.custom-model-data", 0);
        this.tokenGlow = config.getBoolean("token.glow", true);
        this.tokenMaxExtensions = Math.max(0, config.getInt("token.max-extensions", 0));
        this.tokenAllowOnSealed = config.getBoolean("token.allow-on-sealed", true);

        // Allow language files to provide localized token name & lore
        if (langConfig.contains("token-name")) {
            this.tokenName = langConfig.getString("token-name");
        } else {
            this.tokenName = config.getString("token.name", "&6&lExtension Token &7(+{duration})");
        }

        if (langConfig.contains("token-lore")) {
            this.tokenLore = langConfig.getStringList("token-lore");
        } else if (config.contains("token.lore")) {
            this.tokenLore = config.getStringList("token.lore");
        } else {
            this.tokenLore = Arrays.asList(
                    "&7Drag and drop this token onto a timed item",
                    "&7to extend its duration.",
                    "",
                    "&eDuration: &a+{duration}"
            );
        }
    }

    private void loadLangConfig() {
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        // Clean up root folder so ONLY config.yml and offline_players.dat remain outside
        cleanupOldFile(new File(plugin.getDataFolder(), "config_vi.yml"));
        cleanupOldFile(new File(plugin.getDataFolder(), "config_en.yml"));
        cleanupOldFile(new File(plugin.getDataFolder(), "lang.yml"));
        cleanupOldFile(new File(langDir, "lang.yml"));

        // Migrate any old language files from root into lang/ subdirectory
        migrateOldLangFile("lang_vi.yml", langDir);
        migrateOldLangFile("lang_en.yml", langDir);

        // Resolve active language file: only export and load the file corresponding to 'language' setting
        String targetFileName = "lang_" + language + ".yml";
        File langFile = new File(langDir, targetFileName);

        if (!langFile.exists()) {
            saveLangResourceIfNotExists("lang/" + targetFileName);
        }

        // If target file still doesn't exist (e.g. invalid language entered), fallback to English
        if (!langFile.exists()) {
            targetFileName = "lang_en.yml";
            langFile = new File(langDir, targetFileName);
            if (!langFile.exists()) {
                saveLangResourceIfNotExists("lang/lang_en.yml");
            }
        }

        // Auto-update ONLY the active language file with newly added keys
        ConfigUpdater.update(plugin, "lang/" + targetFileName, langFile);

        this.langConfig = YamlConfiguration.loadConfiguration(langFile);

        // Set bundled default values from jar resource
        InputStream defStream = plugin.getResource("lang/" + targetFileName);
        if (defStream == null) {
            defStream = plugin.getResource(targetFileName);
        }
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            langConfig.setDefaults(defConfig);
        }
    }

    private void cleanupOldFile(File file) {
        if (file != null && file.exists()) {
            try {
                file.delete();
            } catch (Exception ignored) {
            }
        }
    }

    private void migrateOldLangFile(String fileName, File langDir) {
        File oldFile = new File(plugin.getDataFolder(), fileName);
        File newFile = new File(langDir, fileName);
        if (oldFile.exists() && !newFile.exists()) {
            try {
                java.nio.file.Files.move(oldFile.toPath(), newFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Moved " + fileName + " into lang/ directory.");
            } catch (Exception ignored) {
            }
        } else if (oldFile.exists()) {
            try {
                oldFile.delete();
            } catch (Exception ignored) {
            }
        }
    }

    private void saveLangResourceIfNotExists(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            try {
                plugin.saveResource(resourcePath, false);
            } catch (Exception ignored) {
                if (resourcePath.startsWith("lang/")) {
                    String altName = resourcePath.substring("lang/".length());
                    InputStream in = plugin.getResource(altName);
                    if (in != null) {
                        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                            in.transferTo(out);
                        } catch (Exception ignored2) {
                        }
                    }
                }
            }
        }
    }

    public String getLanguage() {
        return language;
    }

    public String getPrefix() {
        if (langConfig.contains("prefix")) {
            return ColorUtil.colorize(langConfig.getString("prefix", "&8[&6TimedItem&8] &r"));
        }
        return ColorUtil.colorize(config.getString("messages.prefix", "&8[&6TimedItem&8] &r"));
    }

    public String getRawMessage(String key) {
        if (langConfig.contains(key)) {
            return langConfig.getString(key, "");
        }
        return config.getString("messages." + key, "");
    }

    public String getMessage(String key) {
        String raw = getRawMessage(key);
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ColorUtil.colorize(getPrefix() + raw);
    }

    public String getMessageWithoutPrefix(String key) {
        String raw = getRawMessage(key);
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ColorUtil.colorize(raw);
    }

    public String getHelpCommandMessage(String key, String def) {
        String val = null;
        if (langConfig != null) {
            if (langConfig.contains("help-commands." + key)) {
                val = langConfig.getString("help-commands." + key);
            } else if (langConfig.contains("help-" + key)) {
                val = langConfig.getString("help-" + key);
            } else if (langConfig.contains("help." + key)) {
                val = langConfig.getString("help." + key);
            }
        }
        if (val == null && config != null) {
            if (config.contains("messages.help-commands." + key)) {
                val = config.getString("messages.help-commands." + key);
            } else if (config.contains("messages.help-" + key)) {
                val = config.getString("messages.help-" + key);
            }
        }
        if (val == null || val.isEmpty()) {
            val = def;
        }
        return ColorUtil.colorize(val);
    }

    public String getStatusActive() {
        return ColorUtil.colorize(langConfig.getString("status-active", "&aĐang hoạt động"));
    }

    public String getStatusInactive() {
        return ColorUtil.colorize(langConfig.getString("status-inactive", "&eChưa kích hoạt"));
    }

    public String getStatusExpired() {
        return ColorUtil.colorize(langConfig.getString("status-expired", "&cĐã hết hạn"));
    }

    public String getStatusSealed() {
        return ColorUtil.colorize(langConfig.getString("status-sealed", "&4&lĐã phong ấn (Hết hạn)"));
    }

    public String getStatusNone() {
        return ColorUtil.colorize(langConfig.getString("status-none", "&7Không có hạn"));
    }

    public String getActionFormatted(ExpireAction action) {
        if (action == ExpireAction.SEAL) {
            return ColorUtil.colorize(langConfig.getString("action-seal", "&6Seal & Keep (SEAL)"));
        }
        return ColorUtil.colorize(langConfig.getString("action-delete", "&cAuto-Delete (DELETE)"));
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public ExpireAction getDefaultExpireAction() {
        return defaultExpireAction != null ? defaultExpireAction : ExpireAction.DELETE;
    }

    public int getCheckInterval() {
        return Math.max(1, checkInterval);
    }

    public boolean isWorldCheckEnabled() {
        return worldCheck;
    }

    public boolean isInventoryCheckEnabled() {
        return inventoryCheck;
    }

    public boolean isOfflineCheckEnabled() {
        return offlineCheck;
    }

    public int getOfflineCheckInterval() {
        return Math.max(10, offlineCheckInterval);
    }

    public int getEquipmentCheckInterval() {
        return Math.max(1, equipmentCheckInterval);
    }

    public long getMaxDurationMillis() {
        return maxDurationMillis;
    }

    public boolean isActivationGameMode(org.bukkit.GameMode mode) {
        if (mode == null) return false;
        return activationGameModes.contains(mode);
    }

    public boolean isContainerCheckEnabled() {
        return containerCheck;
    }

    public boolean isExpiryWarningEnabled() {
        return expiryWarningEnabled;
    }

    public List<Integer> getExpiryWarningTimes() {
        return expiryWarningTimes;
    }

    public boolean isExpiryWarningActionbar() {
        return expiryWarningActionbar;
    }

    public boolean isExpiryWarningChat() {
        return expiryWarningChat;
    }

    public Sound getExpiryWarningSound() {
        return expiryWarningSound;
    }

    public int getHelpPerPage() {
        return Math.max(1, helpPerPage);
    }

    public String getLoreFormat() {
        return loreFormat;
    }

    public String getLoreInactiveFormat() {
        return loreInactiveFormat;
    }

    public String getLoreSealedFormat() {
        return loreSealedFormat;
    }

    public Material getTokenMaterial() {
        return tokenMaterial;
    }

    public int getTokenCustomModelData() {
        return tokenCustomModelData;
    }

    public boolean isTokenGlow() {
        return tokenGlow;
    }

    public int getTokenMaxExtensions() {
        return tokenMaxExtensions;
    }

    public boolean isTokenAllowOnSealed() {
        return tokenAllowOnSealed;
    }

    public String getTokenName() {
        return tokenName;
    }

    public List<String> getTokenLore() {
        return tokenLore;
    }
}
