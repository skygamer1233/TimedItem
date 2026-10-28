package me.timeditem.hook;

import me.timeditem.TimedItem;
import me.timeditem.item.SavedItemManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Unified custom item hook manager supporting:
 * - saved:<id> (Internal SavedItemManager)
 * - ia:<namespace:id> (ItemsAdder)
 * - oraxen:<id> (Oraxen)
 * - nexo:<id> (Nexo)
 * - ei:<id> (ExecutableItems)
 * - mmo:<type>:<id> (MMOItems)
 *
 * Uses cached reflection handles and 30s tab-completion caches for zero TPS overhead.
 */
public class CustomItemHookManager {
    public enum Status {
        SUCCESS,
        NOT_CUSTOM_PREFIX,
        PLUGIN_NOT_INSTALLED,
        ITEM_NOT_FOUND
    }

    public record ResolveResult(Status status, ItemStack item, String providerName) {}

    private final TimedItem plugin;
    private final MMOItemsHook mmoItemsHook;
    private final SavedItemManager savedItemManager;

    // Cached reflection handles for ItemsAdder
    private Method iaGetInstanceMethod;
    private Method iaGetItemStackMethod;
    private Method iaGetIdsMethod;

    // Cached reflection handles for Oraxen
    private Method oraxenGetItemByIdMethod;
    private Method oraxenGetItemNamesMethod;

    // Cached reflection handles for Nexo
    private Method nexoItemFromIdMethod;
    private Method nexoItemNamesMethod;

    // Cached reflection handles for ExecutableItems
    private Method eiGetManagerMethod;

    // 30-second TTL caches for tab-completion IDs
    private static final long CACHE_TTL_MS = 30_000L;
    private List<String> cachedIaIds = Collections.emptyList();
    private long cachedIaTimestamp = 0L;
    private List<String> cachedOraxenIds = Collections.emptyList();
    private long cachedOraxenTimestamp = 0L;
    private List<String> cachedNexoIds = Collections.emptyList();
    private long cachedNexoTimestamp = 0L;
    private List<String> cachedEiIds = Collections.emptyList();
    private long cachedEiTimestamp = 0L;

    public CustomItemHookManager(TimedItem plugin, MMOItemsHook mmoItemsHook, SavedItemManager savedItemManager) {
        this.plugin = plugin;
        this.mmoItemsHook = mmoItemsHook;
        this.savedItemManager = savedItemManager;
    }

    public boolean isItemsAdderEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    public boolean isOraxenEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("Oraxen");
    }

    public boolean isNexoEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("Nexo");
    }

    public boolean isExecutableItemsEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("ExecutableItems");
    }

    public void logDetectedHooks() {
        List<String> hooked = new ArrayList<>();
        if (mmoItemsHook.isAvailable()) hooked.add("MMOItems");
        if (isItemsAdderEnabled()) hooked.add("ItemsAdder");
        if (isOraxenEnabled()) hooked.add("Oraxen");
        if (isNexoEnabled()) hooked.add("Nexo");
        if (isExecutableItemsEnabled()) hooked.add("ExecutableItems");

        if (!hooked.isEmpty()) {
            plugin.getLogger().info("Hooked custom item providers: " + String.join(", ", hooked));
        }
    }

    /**
     * Resolves an item specification string into an ItemStack.
     */
    public ResolveResult resolveItem(String spec, int amount, Player target) {
        if (spec == null || !spec.contains(":")) {
            return new ResolveResult(Status.NOT_CUSTOM_PREFIX, null, null);
        }

        int firstColon = spec.indexOf(':');
        String prefix = spec.substring(0, firstColon).toLowerCase();
        String rest = spec.substring(firstColon + 1);

        return switch (prefix) {
            case "saved", "save" -> {
                ItemStack saved = savedItemManager.getItem(rest, amount);
                if (saved == null) {
                    yield new ResolveResult(Status.ITEM_NOT_FOUND, null, "SavedItems");
                }
                yield new ResolveResult(Status.SUCCESS, saved, "SavedItems");
            }
            case "ia", "itemsadder" -> {
                if (!isItemsAdderEnabled()) {
                    yield new ResolveResult(Status.PLUGIN_NOT_INSTALLED, null, "ItemsAdder");
                }
                ItemStack item = getItemsAdderItem(rest, amount);
                yield item != null
                        ? new ResolveResult(Status.SUCCESS, item, "ItemsAdder")
                        : new ResolveResult(Status.ITEM_NOT_FOUND, null, "ItemsAdder");
            }
            case "oraxen", "ox" -> {
                if (!isOraxenEnabled()) {
                    yield new ResolveResult(Status.PLUGIN_NOT_INSTALLED, null, "Oraxen");
                }
                ItemStack item = getOraxenItem(rest, amount);
                yield item != null
                        ? new ResolveResult(Status.SUCCESS, item, "Oraxen")
                        : new ResolveResult(Status.ITEM_NOT_FOUND, null, "Oraxen");
            }
            case "nexo", "nx" -> {
                if (!isNexoEnabled()) {
                    yield new ResolveResult(Status.PLUGIN_NOT_INSTALLED, null, "Nexo");
                }
                ItemStack item = getNexoItem(rest, amount);
                yield item != null
                        ? new ResolveResult(Status.SUCCESS, item, "Nexo")
                        : new ResolveResult(Status.ITEM_NOT_FOUND, null, "Nexo");
            }
            case "ei", "executableitems" -> {
                if (!isExecutableItemsEnabled()) {
                    yield new ResolveResult(Status.PLUGIN_NOT_INSTALLED, null, "ExecutableItems");
                }
                ItemStack item = getExecutableItem(rest, amount, target);
                yield item != null
                        ? new ResolveResult(Status.SUCCESS, item, "ExecutableItems")
                        : new ResolveResult(Status.ITEM_NOT_FOUND, null, "ExecutableItems");
            }
            case "mmo", "mmoitems" -> {
                if (!mmoItemsHook.isAvailable()) {
                    yield new ResolveResult(Status.PLUGIN_NOT_INSTALLED, null, "MMOItems");
                }
                int secondColon = rest.indexOf(':');
                if (secondColon <= 0 || secondColon >= rest.length() - 1) {
                    yield new ResolveResult(Status.ITEM_NOT_FOUND, null, "MMOItems");
                }
                String mmoType = rest.substring(0, secondColon);
                String mmoId = rest.substring(secondColon + 1);
                ItemStack item = mmoItemsHook.getItem(mmoType, mmoId);
                if (item != null) {
                    item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
                    yield new ResolveResult(Status.SUCCESS, item, "MMOItems");
                }
                yield new ResolveResult(Status.ITEM_NOT_FOUND, null, "MMOItems");
            }
            default -> new ResolveResult(Status.NOT_CUSTOM_PREFIX, null, null);
        };
    }

    private ItemStack getItemsAdderItem(String namespacedId, int amount) {
        try {
            if (iaGetInstanceMethod == null) {
                Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
                iaGetInstanceMethod = customStackClass.getMethod("getInstance", String.class);
                iaGetItemStackMethod = customStackClass.getMethod("getItemStack");
            }
            Object customStack = iaGetInstanceMethod.invoke(null, namespacedId);
            if (customStack != null) {
                Object result = iaGetItemStackMethod.invoke(customStack);
                if (result instanceof ItemStack is) {
                    ItemStack clone = is.clone();
                    clone.setAmount(Math.max(1, Math.min(amount, clone.getMaxStackSize())));
                    return clone;
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to get ItemsAdder item '" + namespacedId + "': " + t.getMessage());
        }
        return null;
    }

    private ItemStack getOraxenItem(String id, int amount) {
        try {
            if (oraxenGetItemByIdMethod == null) {
                Class<?> oraxenClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
                oraxenGetItemByIdMethod = oraxenClass.getMethod("getItemById", String.class);
            }
            Object builder = oraxenGetItemByIdMethod.invoke(null, id);
            if (builder != null) {
                Method buildMethod = builder.getClass().getMethod("build");
                Object result = buildMethod.invoke(builder);
                if (result instanceof ItemStack is) {
                    ItemStack clone = is.clone();
                    clone.setAmount(Math.max(1, Math.min(amount, clone.getMaxStackSize())));
                    return clone;
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to get Oraxen item '" + id + "': " + t.getMessage());
        }
        return null;
    }

    private ItemStack getNexoItem(String id, int amount) {
        try {
            if (nexoItemFromIdMethod == null) {
                Class<?> nexoClass = Class.forName("com.nexomc.nexo.api.NexoItems");
                nexoItemFromIdMethod = nexoClass.getMethod("itemFromId", String.class);
            }
            Object builder = nexoItemFromIdMethod.invoke(null, id);
            if (builder != null) {
                Method buildMethod = builder.getClass().getMethod("build");
                Object result = buildMethod.invoke(builder);
                if (result instanceof ItemStack is) {
                    ItemStack clone = is.clone();
                    clone.setAmount(Math.max(1, Math.min(amount, clone.getMaxStackSize())));
                    return clone;
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to get Nexo item '" + id + "': " + t.getMessage());
        }
        return null;
    }

    private ItemStack getExecutableItem(String id, int amount, Player target) {
        try {
            if (eiGetManagerMethod == null) {
                Class<?> apiClass = Class.forName("com.ssomar.score.api.executableitems.ExecutableItemsAPI");
                eiGetManagerMethod = apiClass.getMethod("getExecutableItemsManager");
            }
            Object manager = eiGetManagerMethod.invoke(null);
            if (manager != null) {
                Method getItemMethod = manager.getClass().getMethod("getExecutableItem", String.class);
                Object optionalObj = getItemMethod.invoke(manager, id);
                if (optionalObj instanceof Optional<?> opt && opt.isPresent()) {
                    Object eiObj = opt.get();
                    Method buildMethod = eiObj.getClass().getMethod("buildItem", int.class, Optional.class);
                    Object result = buildMethod.invoke(eiObj, Math.max(1, amount), Optional.ofNullable(target));
                    if (result instanceof ItemStack is) {
                        return is.clone();
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to get ExecutableItems item '" + id + "': " + t.getMessage());
        }
        return null;
    }

    /**
     * Generates tab-completion suggestions for /ti give <player> <item>.
     */
    public List<String> getTabCompletions(String input) {
        List<String> completions = new ArrayList<>();
        String lower = input.toLowerCase();

        // 1. If user typed a specific custom prefix, expand only that provider's IDs
        if (lower.startsWith("saved:")) {
            for (String id : savedItemManager.getSavedIds()) {
                completions.add("saved:" + id);
            }
            return completions;
        }
        if (lower.startsWith("ia:") && isItemsAdderEnabled()) {
            for (String id : getItemsAdderIds()) {
                completions.add("ia:" + id);
            }
            return completions;
        }
        if (lower.startsWith("oraxen:") && isOraxenEnabled()) {
            for (String id : getOraxenIds()) {
                completions.add("oraxen:" + id);
            }
            return completions;
        }
        if (lower.startsWith("nexo:") && isNexoEnabled()) {
            for (String id : getNexoIds()) {
                completions.add("nexo:" + id);
            }
            return completions;
        }
        if (lower.startsWith("ei:") && isExecutableItemsEnabled()) {
            for (String id : getExecutableItemIds()) {
                completions.add("ei:" + id);
            }
            return completions;
        }
        if (lower.startsWith("mmo:") && mmoItemsHook.isAvailable()) {
            String afterMmo = input.substring(4);
            int secondColon = afterMmo.indexOf(':');
            if (secondColon < 0) {
                for (String type : mmoItemsHook.getTypes()) {
                    completions.add("mmo:" + type + ":");
                }
            } else {
                String type = afterMmo.substring(0, secondColon).toUpperCase();
                for (String id : mmoItemsHook.getTemplateIds(type)) {
                    completions.add("mmo:" + type + ":" + id);
                }
            }
            return completions;
        }

        // 2. Otherwise suggest available prefixes + saved items
        for (String id : savedItemManager.getSavedIds()) {
            completions.add("saved:" + id);
        }
        if (isItemsAdderEnabled()) completions.add("ia:");
        if (isOraxenEnabled()) completions.add("oraxen:");
        if (isNexoEnabled()) completions.add("nexo:");
        if (isExecutableItemsEnabled()) completions.add("ei:");
        if (mmoItemsHook.isAvailable()) completions.add("mmo:");

        return completions;
    }

    private List<String> getItemsAdderIds() {
        long now = System.currentTimeMillis();
        if (!cachedIaIds.isEmpty() && (now - cachedIaTimestamp) < CACHE_TTL_MS) {
            return cachedIaIds;
        }
        List<String> list = new ArrayList<>();
        try {
            if (iaGetIdsMethod == null) {
                Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
                iaGetIdsMethod = customStackClass.getMethod("getNamespacedIdsInRegistry");
            }
            Object res = iaGetIdsMethod.invoke(null);
            if (res instanceof Collection<?> col) {
                for (Object o : col) {
                    if (o != null) list.add(o.toString());
                }
            }
        } catch (Throwable ignored) {
        }
        cachedIaIds = Collections.unmodifiableList(list);
        cachedIaTimestamp = now;
        return cachedIaIds;
    }

    private List<String> getOraxenIds() {
        long now = System.currentTimeMillis();
        if (!cachedOraxenIds.isEmpty() && (now - cachedOraxenTimestamp) < CACHE_TTL_MS) {
            return cachedOraxenIds;
        }
        List<String> list = new ArrayList<>();
        try {
            if (oraxenGetItemNamesMethod == null) {
                Class<?> oraxenClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
                oraxenGetItemNamesMethod = oraxenClass.getMethod("getItemNames");
            }
            Object res = oraxenGetItemNamesMethod.invoke(null);
            if (res instanceof Collection<?> col) {
                for (Object o : col) {
                    if (o != null) list.add(o.toString());
                }
            } else if (res instanceof String[] arr) {
                Collections.addAll(list, arr);
            }
        } catch (Throwable ignored) {
        }
        cachedOraxenIds = Collections.unmodifiableList(list);
        cachedOraxenTimestamp = now;
        return cachedOraxenIds;
    }

    private List<String> getNexoIds() {
        long now = System.currentTimeMillis();
        if (!cachedNexoIds.isEmpty() && (now - cachedNexoTimestamp) < CACHE_TTL_MS) {
            return cachedNexoIds;
        }
        List<String> list = new ArrayList<>();
        try {
            if (nexoItemNamesMethod == null) {
                Class<?> nexoClass = Class.forName("com.nexomc.nexo.api.NexoItems");
                nexoItemNamesMethod = nexoClass.getMethod("itemNames");
            }
            Object res = nexoItemNamesMethod.invoke(null);
            if (res instanceof Collection<?> col) {
                for (Object o : col) {
                    if (o != null) list.add(o.toString());
                }
            } else if (res instanceof String[] arr) {
                Collections.addAll(list, arr);
            }
        } catch (Throwable ignored) {
        }
        cachedNexoIds = Collections.unmodifiableList(list);
        cachedNexoTimestamp = now;
        return cachedNexoIds;
    }

    private List<String> getExecutableItemIds() {
        long now = System.currentTimeMillis();
        if (!cachedEiIds.isEmpty() && (now - cachedEiTimestamp) < CACHE_TTL_MS) {
            return cachedEiIds;
        }
        List<String> list = new ArrayList<>();
        try {
            if (eiGetManagerMethod == null) {
                Class<?> apiClass = Class.forName("com.ssomar.score.api.executableitems.ExecutableItemsAPI");
                eiGetManagerMethod = apiClass.getMethod("getExecutableItemsManager");
            }
            Object manager = eiGetManagerMethod.invoke(null);
            if (manager != null) {
                Method getIdsMethod = manager.getClass().getMethod("getExecutableItemIdsList");
                Object res = getIdsMethod.invoke(manager);
                if (res instanceof Collection<?> col) {
                    for (Object o : col) {
                        if (o != null) list.add(o.toString());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        cachedEiIds = Collections.unmodifiableList(list);
        cachedEiTimestamp = now;
        return cachedEiIds;
    }
}
