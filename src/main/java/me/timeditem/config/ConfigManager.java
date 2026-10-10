package me.timeditem.config;

import me.timeditem.TimedItem;
import me.timeditem.util.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigManager {
    private final TimedItem plugin;
    private FileConfiguration config;
    private FileConfiguration langConfig;

    private String dateFormat;
    private int checkInterval;
    private boolean worldCheck;
    private boolean inventoryCheck;
    private boolean offlineCheck;
    private int helpPerPage;
    private String loreFormat;
    private String loreInactiveFormat;

    public ConfigManager(TimedItem plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        loadLangConfig();

        this.dateFormat = config.getString("date-format", "dd/MM/yyyy HH:mm:ss");
        this.checkInterval = config.getInt("check-interval", 30);
        this.worldCheck = config.getBoolean("world-check", true);
        this.inventoryCheck = config.getBoolean("inventory-check", true);
        this.offlineCheck = config.getBoolean("offline-check", true);
        this.helpPerPage = config.getInt("help-per-page", 7);
        this.loreFormat = config.getString("lore-format", "&cHết hạn vào: &e{expiry}");
        this.loreInactiveFormat = config.getString("lore-inactive-format", "&eHiệu lực: &a{duration}");
    }

    private void loadLangConfig() {
        File langFile = new File(plugin.getDataFolder(), "lang.yml");
        if (!langFile.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        this.langConfig = YamlConfiguration.loadConfiguration(langFile);

        InputStream defLangStream = plugin.getResource("lang.yml");
        if (defLangStream != null) {
            YamlConfiguration defLang = YamlConfiguration.loadConfiguration(new InputStreamReader(defLangStream, StandardCharsets.UTF_8));
            langConfig.setDefaults(defLang);
        }
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
}
