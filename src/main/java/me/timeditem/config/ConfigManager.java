package me.timeditem.config;

import me.timeditem.TimedItem;
import me.timeditem.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class ConfigManager {
    private final TimedItem plugin;
    private FileConfiguration config;
    private FileConfiguration langConfig;

    private String language;
    private String dateFormat;
    private int checkInterval;
    private boolean worldCheck;
    private boolean inventoryCheck;
    private boolean offlineCheck;
    private int helpPerPage;
    private String loreFormat;
    private String loreInactiveFormat;

    // Token configuration
    private Material tokenMaterial;
    private int tokenCustomModelData;
    private boolean tokenGlow;
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
        this.language = tempConfig.getString("language", "en").toLowerCase();

        // Synchronize config.yml comments and structure with config_vi.yml or config_en.yml
        ConfigUpdater.syncConfigLanguage(plugin, configFile, this.language);

        // Reload Bukkit plugin config
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // Load active language file from lang/ subdirectory
        loadLangConfig();

        this.dateFormat = config.getString("date-format", "dd/MM/yyyy HH:mm:ss");
        this.checkInterval = config.getInt("check-interval", 30);
        this.worldCheck = config.getBoolean("world-check", true);
        this.inventoryCheck = config.getBoolean("inventory-check", true);
        this.offlineCheck = config.getBoolean("offline-check", true);
        this.helpPerPage = config.getInt("help-per-page", 7);

        // Allow language files to override lore formats if defined there
        if (langConfig.contains("lore-format")) {
            this.loreFormat = langConfig.getString("lore-format");
        } else {
            this.loreFormat = config.getString("lore-format", "&cHết hạn vào: &e{expiry}");
        }

        if (langConfig.contains("lore-inactive-format")) {
            this.loreInactiveFormat = langConfig.getString("lore-inactive-format");
        } else {
            this.loreInactiveFormat = config.getString("lore-inactive-format", "&eHiệu lực: &a{duration}");
        }

        // Token settings
        String matName = config.getString("token.material", "PAPER");
        Material mat = Material.matchMaterial(matName != null ? matName : "PAPER");
        this.tokenMaterial = mat != null ? mat : Material.PAPER;
        this.tokenCustomModelData = config.getInt("token.custom-model-data", 0);
        this.tokenGlow = config.getBoolean("token.glow", true);

        // Allow language files to provide localized token name & lore
        if (langConfig.contains("token-name")) {
            this.tokenName = langConfig.getString("token-name");
        } else {
            this.tokenName = config.getString("token.name", "&6&lPhiếu Gia Hạn &7(+{duration})");
        }

        if (langConfig.contains("token-lore")) {
            this.tokenLore = langConfig.getStringList("token-lore");
        } else if (config.contains("token.lore")) {
            this.tokenLore = config.getStringList("token.lore");
        } else {
            this.tokenLore = Arrays.asList(
                    "&7Kéo và thả phiếu này vào một",
                    "&7vật phẩm có thời hạn để gia hạn.",
                    "",
                    "&eThời gian cộng thêm: &a+{duration}",
                    "&8&o(Nhấp chuột trái vào item để sử dụng)"
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

    public String getStatusNone() {
        return ColorUtil.colorize(langConfig.getString("status-none", "&7Không có hạn"));
    }

    public String getDateFormat() {
        return dateFormat;
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

    public int getHelpPerPage() {
        return Math.max(1, helpPerPage);
    }

    public String getLoreFormat() {
        return loreFormat;
    }

    public String getLoreInactiveFormat() {
        return loreInactiveFormat;
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

    public String getTokenName() {
        return tokenName;
    }

    public List<String> getTokenLore() {
        return tokenLore;
    }
}
