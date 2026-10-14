package me.timeditem;

import me.timeditem.check.CheckManager;
import me.timeditem.check.OfflinePlayerManager;
import me.timeditem.command.TimedItemCommand;
import me.timeditem.config.ConfigManager;
import me.timeditem.hook.MMOItemsHook;
import me.timeditem.hook.TimedItemExpansion;
import me.timeditem.item.ItemManager;
import me.timeditem.listener.PlayerListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class TimedItem extends JavaPlugin {
    private static TimedItem instance;

    private ConfigManager configManager;
    private ItemManager itemManager;
    private OfflinePlayerManager offlinePlayerManager;
    private CheckManager checkManager;
    private MMOItemsHook mmoItemsHook;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize configuration & language
        this.configManager = new ConfigManager(this);

        // Initialize item manager
        this.itemManager = new ItemManager(this, configManager);

        // Initialize offline player manager
        this.offlinePlayerManager = new OfflinePlayerManager(this);

        // Initialize MMOItems hook
        this.mmoItemsHook = new MMOItemsHook(this);
        if (mmoItemsHook.isAvailable()) {
            getLogger().info("MMOItems detected and hooked successfully!");
        }

        // Initialize PlaceholderAPI hook
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new TimedItemExpansion(this, configManager, itemManager).register();
            getLogger().info("PlaceholderAPI expansion registered successfully!");
        }

        // Initialize asynchronous & synchronous check manager
        this.checkManager = new CheckManager(this, configManager, itemManager, offlinePlayerManager);
        this.checkManager.start();

        // Register commands
        TimedItemCommand cmdHandler = new TimedItemCommand(this, configManager, itemManager, checkManager);
        PluginCommand tiCommand = getCommand("ti");
        if (tiCommand != null) {
            tiCommand.setExecutor(cmdHandler);
            tiCommand.setTabCompleter(cmdHandler);
        }

        // Register listeners
        getServer().getPluginManager().registerEvents(
                new PlayerListener(this, configManager, itemManager, checkManager),
                this
        );

        getLogger().info("TimedItem v" + getDescription().getVersion() + " has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        if (checkManager != null) {
            checkManager.stop();
        }

        if (offlinePlayerManager != null) {
            offlinePlayerManager.saveData();
        }

        getServer().getScheduler().cancelTasks(this);
        getLogger().info("TimedItem has been disabled.");
        instance = null;
    }

    public static TimedItem getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public OfflinePlayerManager getOfflinePlayerManager() {
        return offlinePlayerManager;
    }

    public CheckManager getCheckManager() {
        return checkManager;
    }

    public MMOItemsHook getMMOItemsHook() {
        return mmoItemsHook;
    }
}
