package me.timeditem.config;

import me.timeditem.TimedItem;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Automatically updates existing YAML configuration and language files on disk
 * when a newer plugin version introduces new keys or sections.
 *
 * Guarantees that server administrators never lose their customized values
 * while seamlessly receiving newly added options, messages, and comments.
 */
public class ConfigUpdater {

    /**
     * Updates an existing configuration file on disk with missing keys from the bundled default resource.
     *
     * @param plugin       The TimedItem plugin instance
     * @param resourcePath Path to bundled resource in JAR (e.g. "config.yml", "lang_vi.yml")
     * @param targetFile   Target file in plugin data directory
     * @return true if targetFile was modified and saved with new keys, false otherwise
     */
    public static boolean update(TimedItem plugin, String resourcePath, File targetFile) {
        if (plugin == null || targetFile == null || !targetFile.exists()) {
            return false;
        }

        InputStream in = plugin.getResource(resourcePath);
        if (in == null) {
            return false;
        }

        try {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            YamlConfiguration curConfig = YamlConfiguration.loadConfiguration(targetFile);

            boolean modified = false;

            // 1. Copy file header comments if the current file has no header
            try {
                List<String> defHeader = defConfig.options().getHeader();
                if (defHeader != null && !defHeader.isEmpty()) {
                    List<String> curHeader = curConfig.options().getHeader();
                    if (curHeader == null || curHeader.isEmpty()) {
                        curConfig.options().setHeader(defHeader);
                        modified = true;
                    }
                }
            } catch (Throwable ignored) {
            }

            // 2. Iterate recursively over all keys in the default resource
            for (String key : defConfig.getKeys(true)) {
                if (defConfig.isConfigurationSection(key)) {
                    if (!curConfig.contains(key)) {
                        curConfig.createSection(key);
                        modified = true;
                    }
                } else {
                    if (!curConfig.contains(key)) {
                        curConfig.set(key, defConfig.get(key));
                        modified = true;
                    }
                }

                // Copy comments for new or missing comment lines
                try {
                    List<String> defComments = defConfig.getComments(key);
                    if (defComments != null && !defComments.isEmpty()) {
                        List<String> curComments = curConfig.getComments(key);
                        if (curComments == null || curComments.isEmpty()) {
                            curConfig.setComments(key, defComments);
                            modified = true;
                        }
                    }
                    List<String> defInline = defConfig.getInlineComments(key);
                    if (defInline != null && !defInline.isEmpty()) {
                        List<String> curInline = curConfig.getInlineComments(key);
                        if (curInline == null || curInline.isEmpty()) {
                            curConfig.setInlineComments(key, defInline);
                            modified = true;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            // 3. Save if changes were made
            if (modified) {
                curConfig.save(targetFile);
                plugin.getLogger().info("Auto-updated " + targetFile.getName() + " with new options from plugin update!");
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to auto-update " + targetFile.getName() + ": " + e.getMessage());
        }

        return false;
    }

    /**
     * Synchronizes config.yml with the template corresponding to the selected language.
     * Updates header, comments, and missing keys to match the target language (config_vi.yml or config_en.yml),
     * while preserving all user-configured values (check-interval, date-format, token.material, etc.).
     *
     * @param plugin     The TimedItem plugin
     * @param configFile The config.yml file on disk
     * @param language   The active language ("vi" or "en")
     * @return true if config.yml was modified and saved
     */
    public static boolean syncConfigLanguage(TimedItem plugin, File configFile, String language) {
        if (plugin == null || configFile == null || !configFile.exists()) {
            return false;
        }

        String templateName = "vi".equalsIgnoreCase(language) ? "config_vi.yml" : "config_en.yml";
        InputStream in = plugin.getResource(templateName);
        if (in == null) {
            in = plugin.getResource("config.yml");
        }
        if (in == null) {
            return false;
        }

        try {
            YamlConfiguration templateConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            YamlConfiguration curConfig = YamlConfiguration.loadConfiguration(configFile);

            boolean modified = false;

            // 1. Sync header
            List<String> templateHeader = templateConfig.options().getHeader();
            if (templateHeader != null && !templateHeader.isEmpty()) {
                List<String> curHeader = curConfig.options().getHeader();
                if (curHeader == null || !curHeader.equals(templateHeader)) {
                    curConfig.options().setHeader(templateHeader);
                    modified = true;
                }
            }

            // 2. Sync keys and comments
            for (String key : templateConfig.getKeys(true)) {
                if (templateConfig.isConfigurationSection(key)) {
                    if (!curConfig.contains(key)) {
                        curConfig.createSection(key);
                        modified = true;
                    }
                } else {
                    if (!curConfig.contains(key)) {
                        curConfig.set(key, templateConfig.get(key));
                        modified = true;
                    }
                }

                // Update comments to match template language
                List<String> templateComments = templateConfig.getComments(key);
                List<String> curComments = curConfig.getComments(key);
                if (templateComments != null && !templateComments.isEmpty()) {
                    if (curComments == null || !curComments.equals(templateComments)) {
                        curConfig.setComments(key, templateComments);
                        modified = true;
                    }
                }

                List<String> templateInline = templateConfig.getInlineComments(key);
                List<String> curInline = curConfig.getInlineComments(key);
                if (templateInline != null && !templateInline.isEmpty()) {
                    if (curInline == null || !curInline.equals(templateInline)) {
                        curConfig.setInlineComments(key, templateInline);
                        modified = true;
                    }
                }
            }

            // Preserve current language setting
            if (!language.equalsIgnoreCase(curConfig.getString("language", ""))) {
                curConfig.set("language", language);
                modified = true;
            }

            if (modified) {
                curConfig.save(configFile);
                plugin.getLogger().info("Synchronized config.yml comments to language: " + language.toUpperCase());
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to sync config.yml language: " + e.getMessage());
        }

        return false;
    }
}
