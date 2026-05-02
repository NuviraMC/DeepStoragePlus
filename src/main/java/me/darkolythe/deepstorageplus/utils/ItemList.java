package me.darkolythe.deepstorageplus.utils;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

import static me.darkolythe.deepstorageplus.dsu.managers.WirelessManager.createReceiver;
import static me.darkolythe.deepstorageplus.dsu.managers.WirelessManager.createTerminal;

public class ItemList {

    public static final String GROUP_STORAGE_CELL = "storage_cell";
    public static final String GROUP_STORAGE_CONTAINER = "storage_container";
    public static final String GROUP_TOOL = "tool";
    public static final String GROUP_SUPPORT = "support";

    public static final String KEY_STORAGE_CELL_1K = "storage_cell_1k";
    public static final String KEY_STORAGE_CELL_4K = "storage_cell_4k";
    public static final String KEY_STORAGE_CELL_16K = "storage_cell_16k";
    public static final String KEY_STORAGE_CELL_64K = "storage_cell_64k";
    public static final String KEY_STORAGE_CELL_256K = "storage_cell_256k";
    public static final String KEY_STORAGE_CELL_1M = "storage_cell_1m";
    public static final String KEY_STORAGE_CONTAINER_1K = "storage_container_1k";
    public static final String KEY_STORAGE_CONTAINER_4K = "storage_container_4k";
    public static final String KEY_STORAGE_CONTAINER_16K = "storage_container_16k";
    public static final String KEY_STORAGE_CONTAINER_64K = "storage_container_64k";
    public static final String KEY_STORAGE_CONTAINER_256K = "storage_container_256k";
    public static final String KEY_STORAGE_CONTAINER_1M = "storage_container_1m";
    public static final String KEY_CREATIVE_STORAGE_CONTAINER = "creative_storage_container";
    public static final String KEY_STORAGE_WRENCH = "storage_wrench";
    public static final String KEY_SORTER_WRENCH = "sorter_wrench";
    public static final String KEY_LINK_MODULE = "link_module";
    public static final String KEY_SPEED_UPGRADE = "speed_upgrade";
    public static final String KEY_RECEIVER = "receiver";
    public static final String KEY_TERMINAL = "terminal";
    public static final String KEY_DSU_WALL = "dsu_wall";
    public static final String KEY_DSU_EMPTY_BLOCK = "dsu_empty_block";
    public static final String KEY_DSU_LOCK = "dsu_lock";
    public static final String KEY_SORTER_WALL = "sorter_wall";
    public static final String KEY_SORTER_EMPTY_BLOCK = "sorter_empty_block";
    public static final String KEY_IO_SETTINGS = "dsu_io_settings";

    private static final String ITEM_ID_TAG = "item_id";
    private static final String ITEM_GROUP_TAG = "item_group";

    private DeepStoragePlus main;

    public ItemStack storageCell1K;
    public ItemStack storageCell4K;
    public ItemStack storageCell16K;
    public ItemStack storageCell64K;
    public ItemStack storageCell256K;
    public ItemStack storageCell1M;
    public ItemStack storageContainer1K;
    public ItemStack storageContainer4K;
    public ItemStack storageContainer16K;
    public ItemStack storageContainer64K;
    public ItemStack storageContainer256K;
    public ItemStack storageContainer1M;
    public ItemStack creativeStorageContainer;
    public ItemStack storageWrench;
    public ItemStack sorterWrench;
    public ItemStack receiver;
    public ItemStack terminal;
    public ItemStack speedUpgrade;
    public ItemStack linkModule;

    public Map<String, ItemStack> itemListMap = new HashMap<>();

    public ItemList(DeepStoragePlus plugin) {
        this.main = plugin;
        reloadFromConfig();
    }

    public final void reloadFromConfig() {
        itemListMap.clear();

        this.storageCell1K = createStorageItem(KEY_STORAGE_CELL_1K, GROUP_STORAGE_CELL, Material.STONE_AXE, ChatColor.WHITE + LanguageManager.getValue("storagecell") + " " + ChatColor.GRAY + ChatColor.BOLD + "1K", "deepstorageplus:storage_cell_1k", false);
        this.storageCell4K = createStorageItem(KEY_STORAGE_CELL_4K, GROUP_STORAGE_CELL, Material.STONE_AXE, ChatColor.WHITE + LanguageManager.getValue("storagecell") + " " + ChatColor.WHITE + ChatColor.BOLD + "4K", "deepstorageplus:storage_cell_4k", false);
        this.storageCell16K = createStorageItem(KEY_STORAGE_CELL_16K, GROUP_STORAGE_CELL, Material.STONE_AXE, ChatColor.WHITE + LanguageManager.getValue("storagecell") + " " + ChatColor.YELLOW + ChatColor.BOLD + "16K", "deepstorageplus:storage_cell_16k", false);
        this.storageCell64K = createStorageItem(KEY_STORAGE_CELL_64K, GROUP_STORAGE_CELL, Material.STONE_AXE, ChatColor.WHITE + LanguageManager.getValue("storagecell") + " " + ChatColor.GREEN + ChatColor.BOLD + "64K", "deepstorageplus:storage_cell_64k", false);
        this.storageCell256K = createStorageItem(KEY_STORAGE_CELL_256K, GROUP_STORAGE_CELL, Material.STONE_AXE, ChatColor.WHITE + LanguageManager.getValue("storagecell") + " " + ChatColor.BLUE + ChatColor.BOLD + "256K", "deepstorageplus:storage_cell_256k", false);
        this.storageCell1M = createStorageItem(KEY_STORAGE_CELL_1M, GROUP_STORAGE_CELL, Material.STONE_AXE, ChatColor.WHITE + LanguageManager.getValue("storagecell") + " " + ChatColor.LIGHT_PURPLE + ChatColor.BOLD + "1M", "deepstorageplus:storage_cell_1m", false);

        this.storageContainer1K = createStorageItem(KEY_STORAGE_CONTAINER_1K, GROUP_STORAGE_CONTAINER, Material.STONE_AXE, ChatColor.WHITE + LanguageManager.getValue("storagecontainer") + " " + ChatColor.GRAY + ChatColor.BOLD + "1K", "deepstorageplus:storage_container_1k", true);
        createLore(storageContainer1K, getStorageMaxConfig("1kmax"), DeepStoragePlus.maxTypes);
        this.storageContainer4K = createStorageItem(KEY_STORAGE_CONTAINER_4K, GROUP_STORAGE_CONTAINER, Material.STONE_AXE, ChatColor.WHITE + LanguageManager.getValue("storagecontainer") + " " + ChatColor.WHITE + ChatColor.BOLD + "4K", "deepstorageplus:storage_container_4k", true);
        createLore(storageContainer4K, getStorageMaxConfig("4kmax"), DeepStoragePlus.maxTypes);
        this.storageContainer16K = createStorageItem(KEY_STORAGE_CONTAINER_16K, GROUP_STORAGE_CONTAINER, Material.STONE_AXE, ChatColor.WHITE + LanguageManager.getValue("storagecontainer") + " " + ChatColor.YELLOW + ChatColor.BOLD + "16K", "deepstorageplus:storage_container_16k", true);
        createLore(storageContainer16K, getStorageMaxConfig("16kmax"), DeepStoragePlus.maxTypes);
        this.storageContainer64K = createStorageItem(KEY_STORAGE_CONTAINER_64K, GROUP_STORAGE_CONTAINER, Material.STONE_AXE, ChatColor.WHITE + LanguageManager.getValue("storagecontainer") + " " + ChatColor.GREEN + ChatColor.BOLD + "64K", "deepstorageplus:storage_container_64k", true);
        createLore(storageContainer64K, getStorageMaxConfig("64kmax"), DeepStoragePlus.maxTypes);
        this.storageContainer256K = createStorageItem(KEY_STORAGE_CONTAINER_256K, GROUP_STORAGE_CONTAINER, Material.STONE_AXE, ChatColor.WHITE + LanguageManager.getValue("storagecontainer") + " " + ChatColor.BLUE + ChatColor.BOLD + "256K", "deepstorageplus:storage_container_256k", true);
        createLore(storageContainer256K, this.getStorageMaxConfig("256kmax"), DeepStoragePlus.maxTypes);
        this.storageContainer1M = createStorageItem(KEY_STORAGE_CONTAINER_1M, GROUP_STORAGE_CONTAINER, Material.STONE_AXE, ChatColor.WHITE + LanguageManager.getValue("storagecontainer") + " " + ChatColor.LIGHT_PURPLE + ChatColor.BOLD + "1M", "deepstorageplus:storage_container_1m", true);
        createLore(storageContainer1M, getStorageMaxConfig("1mmax"), DeepStoragePlus.maxTypes);

        this.creativeStorageContainer = createStorageItem(KEY_CREATIVE_STORAGE_CONTAINER, GROUP_STORAGE_CONTAINER, Material.STONE_AXE, ChatColor.DARK_PURPLE + LanguageManager.getValue("creativestoragecontainer"), "deepstorageplus:creative_storage_container", true);
        createLore(creativeStorageContainer, Integer.MAX_VALUE, DeepStoragePlus.maxTypes);

        this.storageWrench = createStorageWrench();
        this.sorterWrench = createSorterWrench();
        this.linkModule = createLinkModule();
        this.receiver = createReceiver();
        this.terminal = createTerminal();
        this.speedUpgrade = createSpeedUpgrade();

        itemListMap.put("storage_cell_1k", storageCell1K);
        itemListMap.put("storage_cell_4k", storageCell4K);
        itemListMap.put("storage_cell_16k", storageCell16K);
        itemListMap.put("storage_cell_64k", storageCell64K);
        itemListMap.put("storage_cell_256k", storageCell256K);
        itemListMap.put("storage_cell_1m", storageCell1M);
        itemListMap.put("storage_container_1k", storageContainer1K);
        itemListMap.put("storage_container_4k", storageContainer4K);
        itemListMap.put("storage_container_16k", storageContainer16K);
        itemListMap.put("storage_container_64k", storageContainer64K);
        itemListMap.put("storage_container_256k", storageContainer256K);
        itemListMap.put("storage_container_1m", storageContainer1M);
        itemListMap.put("creative_storage_container", creativeStorageContainer);
        itemListMap.put("storage_wrench", storageWrench);
        itemListMap.put("sorter_wrench", sorterWrench);
        itemListMap.put("receiver", receiver);
        itemListMap.put("terminal", terminal);
        itemListMap.put("speed_upgrade", speedUpgrade);
        itemListMap.put("link_module", linkModule);
    }

    // Helper methods
    public Optional<ItemStack> getItem(String itemName) {
        ItemStack item = null;
        if (itemName == null) {
            return Optional.empty();
        }
        if (itemListMap.containsKey(itemName)) {
            item = itemListMap.get(itemName).clone();
        }
        return Optional.ofNullable(item);
    }

    public static boolean isItem(ItemStack item, String exactId) {
        return exactId != null && exactId.equals(getItemId(item));
    }

    public static boolean isGroup(ItemStack item, String groupId) {
        return groupId != null && groupId.equals(getItemGroup(item));
    }

    public static boolean isPluginItem(ItemStack item) {
        return getItemId(item) != null;
    }

    public static ItemStack tag(ItemStack item, String exactId, String group) {
        if (item == null || !item.hasItemMeta()) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        setIdentity(meta, exactId, group);
        item.setItemMeta(meta);
        return item;
    }

    public static String getItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(itemIdKey(), PersistentDataType.STRING);
    }

    public static String getItemGroup(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(itemGroupKey(), PersistentDataType.STRING);
    }

    private static NamespacedKey itemIdKey() {
        return new NamespacedKey(DeepStoragePlus.getInstance(), ITEM_ID_TAG);
    }

    private static NamespacedKey itemGroupKey() {
        return new NamespacedKey(DeepStoragePlus.getInstance(), ITEM_GROUP_TAG);
    }

    public static void applyConfiguredItemModel(ItemMeta meta, String configuredModel, DeepStoragePlus plugin) {
        if (meta == null || plugin == null || configuredModel == null || configuredModel.trim().isEmpty() || configuredModel.equalsIgnoreCase("none")) {
            return;
        }

        NamespacedKey modelKey = NamespacedKey.fromString(configuredModel, plugin);
        if (modelKey != null) {
            meta.setItemModel(modelKey);
        }
    }

    private static ItemStack createStorageItem(String exactId, String group, Material fallbackMaterial, String fallbackName, String fallbackModel, boolean unbreakable) {
        ItemStack item = new ItemStack(resolveMaterial(exactId, fallbackMaterial));
        ItemMeta meta = item.getItemMeta();
        DeepStoragePlus plugin = DeepStoragePlus.getInstance();
        meta.setDisplayName(color(plugin.getConfig().getString("items." + exactId + ".display-name", fallbackName)));
        meta.setUnbreakable(plugin.getConfig().getBoolean("items." + exactId + ".unbreakable", unbreakable));

        String configuredModel = plugin.getConfig().getString("items." + exactId + ".item-model");
        applyConfiguredItemModel(meta, configuredModel, plugin);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (meta.isUnbreakable()) {
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
        setIdentity(meta, exactId, group);
        item.setItemMeta(meta);
        return item;
    }

    public static Material resolveConfiguredMaterial(String configured, Material fallbackMaterial) {
        Material material = tryResolveMaterial(configured);
        return material != null ? material : fallbackMaterial;
    }

    public static Material parseConfiguredMaterial(String configured) {
        return tryResolveMaterial(configured);
    }

    public static Material resolveItemMaterial(String path, Material fallbackMaterial) {
        DeepStoragePlus plugin = DeepStoragePlus.getInstance();
        String configured = plugin.getConfig().getString("items." + path + ".material");
        Material resolved = tryResolveMaterial(configured);
        if (resolved == null) {
            if (configured != null && !configured.trim().isEmpty()) {
                plugin.getLogger().warning("Invalid material in config for items." + path + ".material='" + configured + "'. Using fallback " + fallbackMaterial.name());
            }
            return fallbackMaterial;
        }
        return resolved;
    }

    private static Material resolveMaterial(String path, Material fallbackMaterial) {
        return resolveItemMaterial(path, fallbackMaterial);
    }

    private static Material tryResolveMaterial(String configured) {
        if (configured == null || configured.trim().isEmpty()) {
            return null;
        }

        String candidate = configured.trim();
        Material material = Material.matchMaterial(candidate);
        if (material != null) {
            return material;
        }

        // Accept common config variants like "minecraft:stone_axe", "stone axe" or "stone-axe".
        String normalized = candidate;
        int namespaceIndex = normalized.indexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex < normalized.length() - 1) {
            normalized = normalized.substring(namespaceIndex + 1);
        }
        normalized = normalized.replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        return Material.matchMaterial(normalized);
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private static void setIdentity(ItemMeta meta, String exactId, String group) {
        meta.getPersistentDataContainer().set(itemIdKey(), PersistentDataType.STRING, exactId);
        meta.getPersistentDataContainer().set(itemGroupKey(), PersistentDataType.STRING, group);
    }

    private int getStorageMaxConfig(String size) {
        if (main.getConfig().getBoolean("countinstacks")) {
            return main.getConfig().getInt(size) * 1024 * 64;
        }
        return main.getConfig().getInt(size) * 1024;
    }

    private static ItemStack createStorageCell(int textureId, String name) {
        return new ItemStack(Material.STONE_AXE);
    }

    private static void createLore(ItemStack container, int storageMax, int maxTypes) {
        List<String> lore = new ArrayList<>();

        ItemMeta meta = container.getItemMeta();
        lore.add(ChatColor.GREEN + LanguageManager.getValue("currentstorage") + ": " + 0 + "/" + storageMax);

        lore.add(ChatColor.GREEN + LanguageManager.getValue("currenttypes") + ": " + 0 + "/" + maxTypes);

        for (int i = 0; i < maxTypes; i++) {
            lore.add(ChatColor.GRAY + " - " + LanguageManager.getValue("empty"));
        }

        meta.setLore(lore);
        container.setItemMeta(meta);
    }

    public static ItemStack createStorageWrench() {
        ItemStack storageWrench = new ItemStack(resolveMaterial(KEY_STORAGE_WRENCH, Material.STONE_AXE));
        ItemMeta wrenchmeta = storageWrench.getItemMeta();
        DeepStoragePlus plugin = DeepStoragePlus.getInstance();
        wrenchmeta.setDisplayName(color(plugin.getConfig().getString("items." + KEY_STORAGE_WRENCH + ".display-name", ChatColor.AQUA + LanguageManager.getValue("storageloader"))));
        wrenchmeta.setLore(Arrays.asList(ChatColor.GRAY + LanguageManager.getValue("clickempty"),
                ChatColor.GRAY + LanguageManager.getValue("tocreatedsu"), "", ChatColor.GRAY + LanguageManager.getValue("onetimeuse")));
        wrenchmeta.setUnbreakable(true);
        wrenchmeta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        String configuredModel = plugin.getConfig().getString("items." + KEY_STORAGE_WRENCH + ".item-model");
        applyConfiguredItemModel(wrenchmeta, configuredModel, plugin);
        setIdentity(wrenchmeta, KEY_STORAGE_WRENCH, GROUP_TOOL);
        storageWrench.setItemMeta(wrenchmeta);

        return storageWrench;
    }

    public static ItemStack createSorterWrench() {
        ItemStack sorterWrench = new ItemStack(resolveMaterial(KEY_SORTER_WRENCH, Material.STONE_AXE));
        ItemMeta wrenchmeta = sorterWrench.getItemMeta();
        DeepStoragePlus plugin = DeepStoragePlus.getInstance();
        wrenchmeta.setDisplayName(color(plugin.getConfig().getString("items." + KEY_SORTER_WRENCH + ".display-name", ChatColor.AQUA + LanguageManager.getValue("sorterloader"))));
        wrenchmeta.setLore(Arrays.asList(ChatColor.GRAY + LanguageManager.getValue("clickempty"),
                ChatColor.GRAY + LanguageManager.getValue("tocreatesorter"), "", ChatColor.GRAY + LanguageManager.getValue("onetimeuse")));
        wrenchmeta.setUnbreakable(true);
        wrenchmeta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        String configuredModel = plugin.getConfig().getString("items." + KEY_SORTER_WRENCH + ".item-model");
        applyConfiguredItemModel(wrenchmeta, configuredModel, plugin);
        setIdentity(wrenchmeta, KEY_SORTER_WRENCH, GROUP_TOOL);
        sorterWrench.setItemMeta(wrenchmeta);

        return sorterWrench;
    }

    public static ItemStack createLinkModule() {
        ItemStack linkModule = new ItemStack(resolveMaterial(KEY_LINK_MODULE, Material.STONE_AXE));
        ItemMeta wrenchmeta = linkModule.getItemMeta();
        DeepStoragePlus plugin = DeepStoragePlus.getInstance();
        wrenchmeta.setDisplayName(color(plugin.getConfig().getString("items." + KEY_LINK_MODULE + ".display-name", ChatColor.AQUA + LanguageManager.getValue("linkmodule"))));
        wrenchmeta.setLore(Arrays.asList(ChatColor.GRAY + "Click DSU",
                ChatColor.GRAY + "To save DSU coordinates to this link module"));
        wrenchmeta.setUnbreakable(true);
        wrenchmeta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        String configuredModel = plugin.getConfig().getString("items." + KEY_LINK_MODULE + ".item-model");
        applyConfiguredItemModel(wrenchmeta, configuredModel, plugin);
        setIdentity(wrenchmeta, KEY_LINK_MODULE, GROUP_TOOL);
        linkModule.setItemMeta(wrenchmeta);

        return linkModule;
    }

    public static ItemStack createSpeedUpgrade() {
        DeepStoragePlus plugin = DeepStoragePlus.getInstance();
        ItemStack item = new ItemStack(resolveMaterial(KEY_SPEED_UPGRADE, Material.GLOWSTONE_DUST));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(plugin.getConfig().getString("items." + KEY_SPEED_UPGRADE + ".display-name",
                ChatColor.WHITE.toString() + ChatColor.BOLD + LanguageManager.getValue("ioupgrade"))));
        meta.setLore(Arrays.asList(ChatColor.GRAY + LanguageManager.getValue("clicktoupgrade")));
        String configuredModel = plugin.getConfig().getString("items." + KEY_SPEED_UPGRADE + ".item-model");
        applyConfiguredItemModel(meta, configuredModel, plugin);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        setIdentity(meta, KEY_SPEED_UPGRADE, GROUP_SUPPORT);
        item.setItemMeta(meta);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);

        return item;
    }

    /**
     * Used to compare two ItemStacks from this mod. Returns true if they are of the same type, even if they have different lore.
     * @return true if the items are similar
     */
    public static boolean compareItem(ItemStack item1, ItemStack item2) {
        if (!isPluginItem(item1) || !isPluginItem(item2)) {
            return false;
        }

        return Objects.equals(getItemId(item1), getItemId(item2));
    }
}
