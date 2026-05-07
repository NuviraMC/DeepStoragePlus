package me.darkolythe.deepstorageplus.dsu.managers;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import me.darkolythe.deepstorageplus.utils.ItemList;
import me.darkolythe.deepstorageplus.utils.LanguageManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.*;

import static me.darkolythe.deepstorageplus.dsu.StorageUtils.*;

public class DSUManager {

    private final DeepStoragePlus main;
    public DSUManager(DeepStoragePlus plugin) {
        main = plugin;
    }

    /*
    Add an item to the dsu
     */
    public void addItemToDSU(ItemStack item, Player player) {
        if (item == null || player == null) {
            return;
        }
        boolean isvaliditem = addToDSU(item, player.getOpenInventory().getTopInventory(), player); //try to add item to dsu
        main.dsuupdatemanager.updateItemsExact(player.getOpenInventory().getTopInventory());
        if (item.getAmount() > 0 && isvaliditem) {
            player.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + LanguageManager.getValue("containersfull"));
        }
    }

    /*
    Create the dsu inventory and make it so that it's correct upon opening
    */
    public static void verifyInventory(Inventory inv, Player player) {
        for (int i = 0; i < 6; i++) {
            inv.setItem(7 + (9 * i), getDSUWall());
        }

        for (int i = 0; i < 5; i++) {
            if (inv.getItem(8 + (9 * i)) == null) {
                inv.setItem(8 + (9 * i), getEmptyBlock());
            }
        }

        ItemStack IOItem = inv.getItem(53);
        if (IOItem == null
                || !ItemList.isItem(IOItem, ItemList.KEY_IO_SETTINGS)) {
            inv.setItem(53, createIOItem(player));
        }
    }

    public static ItemStack createIOItem(Player player) {
        ItemStack settings = new ItemStack(ItemList.resolveItemMaterial(ItemList.KEY_IO_SETTINGS, Material.REDSTONE));
        ItemMeta settingsmeta = settings.getItemMeta();
        if (settingsmeta != null) {
            settingsmeta.setDisplayName(ChatColor.WHITE + ChatColor.stripColor(LanguageManager.getValue("dsuioconfig")));
            settingsmeta.setLore(Arrays.asList(ChatColor.GRAY + LanguageManager.getValue("input") + ": " + ChatColor.BLUE + LanguageManager.getValue("all"),
                    ChatColor.GRAY + LanguageManager.getValue("output") + ": " + ChatColor.BLUE + LanguageManager.getValue("none"),
                    ChatColor.GRAY + LanguageManager.getValue("sortingby") + ": " + ChatColor.BLUE + LanguageManager.getValue("container"),
                    ChatColor.GRAY + LanguageManager.getValue("owner") + ": " + ChatColor.BLUE + player.getName(),
                    ChatColor.RED + LanguageManager.getValue("locked")));
        }

        DeepStoragePlus plugin = DeepStoragePlus.getInstance();
        if (plugin != null && settingsmeta != null) {
            String configuredModel = plugin.getConfig().getString("items." + ItemList.KEY_IO_SETTINGS + ".item-model");
            ItemList.applyConfiguredItemModel(settingsmeta, configuredModel, plugin);
            settingsmeta.setUnbreakable(true);
            settingsmeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            settingsmeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "item_id"), org.bukkit.persistence.PersistentDataType.STRING, ItemList.KEY_IO_SETTINGS);
            settingsmeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "item_group"), org.bukkit.persistence.PersistentDataType.STRING, ItemList.GROUP_SUPPORT);
            settingsmeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "dsu_owner_uuid"), org.bukkit.persistence.PersistentDataType.STRING, player.getUniqueId().toString());
            settings.setItemMeta(settingsmeta);
        }

        return settings;
    }

    
    private static ItemStack dsuWall;
    /*
    Create a dsu Wall item to fill the dsu Inventory
     */
    public static ItemStack getDSUWall() {
    	if (dsuWall != null)
    		return dsuWall;
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bordermeta = border.getItemMeta();
        if (bordermeta != null) {
            bordermeta.setDisplayName(ChatColor.DARK_GRAY + LanguageManager.getValue("dsuwalls"));
            bordermeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            bordermeta.getPersistentDataContainer().set(new NamespacedKey(DeepStoragePlus.getInstance(), "item_id"), org.bukkit.persistence.PersistentDataType.STRING, ItemList.KEY_DSU_WALL);
            bordermeta.getPersistentDataContainer().set(new NamespacedKey(DeepStoragePlus.getInstance(), "item_group"), org.bukkit.persistence.PersistentDataType.STRING, ItemList.GROUP_SUPPORT);
            border.setItemMeta(bordermeta);
        }

        return dsuWall = border;
    }

    /*
    Create an Empty Block item to fill the dsu Inventory
     */
    public static ItemStack getEmptyBlock() {
        ItemStack storage = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta storagemeta = storage.getItemMeta();
        if (storagemeta != null) {
            storagemeta.setDisplayName(ChatColor.YELLOW + LanguageManager.getValue("emptystorageblock"));
            storagemeta.getPersistentDataContainer().set(new NamespacedKey(DeepStoragePlus.getInstance(), "item_id"), org.bukkit.persistence.PersistentDataType.STRING, ItemList.KEY_DSU_EMPTY_BLOCK);
            storagemeta.getPersistentDataContainer().set(new NamespacedKey(DeepStoragePlus.getInstance(), "item_group"), org.bukkit.persistence.PersistentDataType.STRING, ItemList.GROUP_SUPPORT);
            storage.setItemMeta(storagemeta);
        }

        return storage;
    }

    private static NamespacedKey entryTemplateKey(int slot) {
        return new NamespacedKey(DeepStoragePlus.getInstance(), "dsu_entry_template_" + slot);
    }

    private static NamespacedKey entryAmountKey(int slot) {
        return new NamespacedKey(DeepStoragePlus.getInstance(), "dsu_entry_amount_" + slot);
    }

    public static final String IO_INPUT_TEMPLATE_TAG = "io_input_template";
    public static final String IO_OUTPUT_TEMPLATE_TAG = "io_output_template";

    public static String serializeTemplate(ItemStack item) {
        return serializeItem(item);
    }

    public static ItemStack deserializeTemplate(String encoded) {
        return deserializeItem(encoded);
    }

    public static void setIoTemplate(ItemStack ioSettings, String tag, ItemStack template) {
        if (ioSettings == null) {
            return;
        }
        ItemMeta meta = ioSettings.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String encoded = serializeTemplate(template);
        if (encoded == null) {
            pdc.remove(new NamespacedKey(DeepStoragePlus.getInstance(), tag));
        } else {
            pdc.set(new NamespacedKey(DeepStoragePlus.getInstance(), tag), PersistentDataType.STRING, encoded);
        }
        ioSettings.setItemMeta(meta);
    }

    public static ItemStack getIoTemplate(ItemStack ioSettings, String tag) {
        if (ioSettings == null) {
            return null;
        }
        ItemMeta meta = ioSettings.getItemMeta();
        if (meta == null) {
            return null;
        }
        String encoded = meta.getPersistentDataContainer().get(new NamespacedKey(DeepStoragePlus.getInstance(), tag), PersistentDataType.STRING);
        return deserializeTemplate(encoded);
    }

    private static ItemStack normalize(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemStack clone = item.clone();
        clone.setAmount(1);
        return clone;
    }

    private static String serializeItem(ItemStack item) {
        if (item == null) {
            return null;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); BukkitObjectOutputStream out = new BukkitObjectOutputStream(baos)) {
            out.writeObject(normalize(item));
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    private static ItemStack deserializeItem(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(encoded)); BukkitObjectInputStream in = new BukkitObjectInputStream(bais)) {
            Object obj = in.readObject();
            return obj instanceof ItemStack stack ? normalize(stack) : null;
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }

    private static ItemStack getStoredTemplate(ItemStack container, int slot) {
        if (container == null) {
            return null;
        }
        ItemMeta meta = container.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return deserializeItem(pdc.get(entryTemplateKey(slot), PersistentDataType.STRING));
    }

    private static void setStoredTemplate(ItemStack container, int slot, ItemStack template) {
        if (container == null) {
            return;
        }
        ItemMeta meta = container.getItemMeta();
        if (meta == null) {
            return;
        }
        String encoded = serializeItem(template);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (encoded == null) {
            pdc.remove(entryTemplateKey(slot));
        } else {
            pdc.set(entryTemplateKey(slot), PersistentDataType.STRING, encoded);
        }
        container.setItemMeta(meta);
    }

    private static void setStoredAmount(ItemStack container, int slot, int amount) {
        if (container == null) {
            return;
        }
        ItemMeta meta = container.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (amount <= 0) {
            pdc.remove(entryAmountKey(slot));
        } else {
            pdc.set(entryAmountKey(slot), PersistentDataType.INTEGER, amount);
        }
        container.setItemMeta(meta);
    }

    private static int getStoredAmount(ItemStack container, int slot) {
        if (container == null) {
            return 0;
        }
        ItemMeta meta = container.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer amount = meta.getPersistentDataContainer().get(entryAmountKey(slot), PersistentDataType.INTEGER);
        if (amount != null) {
            return amount;
        }
        List<String> lore = meta.getLore();
        if (lore != null && lore.size() > 2 + slot) {
            return getMaterialAmount(lore.get(2 + slot));
        }
        return 0;
    }

    private static int findMatchingSlot(ItemStack container, ItemStack item) {
        ItemMeta meta = container != null ? container.getItemMeta() : null;
        List<String> lore = meta != null ? meta.getLore() : null;
        int slots = getTypeSlotCount(lore);
        ItemStack normalized = normalize(item);
        for (int i = 0; i < slots; i++) {
            ItemStack template = getStoredTemplate(container, i);
            if (template != null && template.isSimilar(normalized)) {
                return i;
            }
        }
        return -1;
    }

    private static int findEmptySlot(ItemStack container) {
        ItemMeta meta = container.getItemMeta();
        List<String> lore = meta != null ? meta.getLore() : null;
        int slots = getTypeSlotCount(lore);
        for (int i = 0; i < slots; i++) {
            if (getStoredTemplate(container, i) == null || getStoredAmount(container, i) <= 0) {
                return i;
            }
        }
        return -1;
    }

    private static String getTemplateName(ItemStack template) {
        if (template == null) {
            return LanguageManager.getValue("empty");
        }
        ItemMeta meta = template.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return ChatColor.stripColor(meta.getDisplayName());
        }
        return matToString(template.getType());
    }

    private static void rewriteContainerLore(ItemStack container) {
        if (container == null) {
            return;
        }
        ItemMeta meta = container.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> oldLore = meta.getLore();
        if (oldLore == null || oldLore.isEmpty()) {
            return;
        }

        int slots = getTypeSlotCount(oldLore);
        int totalStorage = 0;
        int totalTypes = 0;
        List<String> lore = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            ItemStack template = getStoredTemplate(container, i);
            int amount = getStoredAmount(container, i);
            totalStorage += Math.max(0, amount);
            if (template != null && amount > 0) {
                totalTypes++;
                lore.add(ChatColor.WHITE + " - " + getTemplateName(template) + " " + amount);
            } else {
                lore.add(ChatColor.GRAY + " - " + LanguageManager.getValue("empty"));
            }
        }

        int storageMax = getMaxData(getData(oldLore.getFirst()));
        lore.add(0, ChatColor.GREEN + LanguageManager.getValue("currentstorage") + ": " + totalStorage + "/" + storageMax);
        lore.add(1, ChatColor.GREEN + LanguageManager.getValue("currenttypes") + ": " + totalTypes + "/" + slots);
        meta.setLore(lore);
        container.setItemMeta(meta);
    }

    /*
    Get the remaining data in a container by passing it a type field. Data or Type. (not literal strings)
     */
    private static int countStorage(ItemStack container, String typeString) {
        int spaceTotal = 0;
        int spaceCur = 0;
        if (container != null) {
            ItemMeta meta = container.getItemMeta();
            List<String> lore = meta != null ? meta.getLore() : null;
            if (lore == null) {
                return 0;
            }
            for (String l : lore) {
                if (l.contains(typeString)) {
                    String data = getData(l);
                    spaceCur += getCurrentData(data);
                    spaceTotal += getMaxData(data);
                }
            }
        }
        return spaceTotal - spaceCur;
    }

    /*
    Get the data portion of the lore string. Format: Type: data
     */
    private static String getData(String lore) {
        int colon = lore.indexOf(':');
        if (colon < 0 || colon + 1 >= lore.length()) {
            return "";
        }
        return lore.substring(colon + 1).trim();
    }

    /*
    Get the current data in the storage info format: Type: cur/max
     */
    private static int getCurrentData(String data) {
        int slash = data.indexOf('/');
        return Integer.parseInt((slash >= 0 ? data.substring(0, slash) : data).trim());
    }

    /*
    Get the max data in the storage info format: Type: cur/max
     */
    private static int getMaxData(String data) {
        int slash = data.indexOf('/');
        return Integer.parseInt((slash >= 0 ? data.substring(slash + 1) : data).trim());
    }

    /*
    Get the list of types that a container has
     */
    public static HashSet<Material> getTypes(List<String> lore) {
        LinkedHashSet<Material> list = new LinkedHashSet<>();
        if (lore == null) {
            return list;
        }
        for (String str : lore) {
            if (str.contains(" - ") && !str.contains(LanguageManager.getValue("empty"))) {
                Material mat = getType(str);
                if (mat != null) {
                    list.add(mat);
                }
            }
        }
        return list;
    }

    /*
    Get the type of Material from a specific lore line of a Storage Container
     */
    private static Material getType(String lore) {
        String cleaned = lore.replace(ChatColor.WHITE + " - ", "").trim();
        int lastSpace = cleaned.lastIndexOf(' ');
        String matName = lastSpace < 0 ? cleaned : cleaned.substring(0, lastSpace);
        return Material.matchMaterial(matName.replace(' ', '_').toUpperCase(Locale.ROOT));
    }

    /*
    Update the container with the itemstack being added
     */
    public static void addDataToContainer(ItemStack container, ItemStack item) {
        if (container == null || item == null || !ItemList.isGroup(container, ItemList.GROUP_STORAGE_CONTAINER)) {
            return;
        }

        int amount = item.getAmount();
        int storage = countStorage(container, LanguageManager.getValue("currentstorage") + ": ");
        int canAdd = Math.min(storage, amount);
        if (canAdd <= 0) {
            return;
        }

        int slot = findMatchingSlot(container, item);
        if (slot < 0) {
            slot = findEmptySlot(container);
            if (slot < 0) {
                return;
            }
            setStoredTemplate(container, slot, item.clone());
            setStoredAmount(container, slot, 0);
        }

        int current = getStoredAmount(container, slot);
        setStoredAmount(container, slot, current + canAdd);
        item.setAmount(amount - canAdd);
        rewriteContainerLore(container);
    }

    /*
    This method loops until the item trying to be added is either done being added, or the containers run out of memory.
     */
    public static boolean addToDSU(ItemStack toAdd, Inventory inv, Player player) {
        if (toAdd == null || inv == null || player == null || ItemList.isPluginItem(toAdd)) {
            return false;
        }
        for (int i = 0; i < 5; i++) {
            if (toAdd.getAmount() > 0) {
                addDataToContainer(inv.getItem(8 + (9 * i)), toAdd);
            } else {
                break;
            }
        }
        return toAdd.getAmount() <= 0;
    }

    /*
    This method loops until the item trying to be added is either done being added, or the containers run out of memory.
     */
    public static boolean addToDSUSilent(ItemStack toAdd, Inventory inv) {
        if (toAdd == null || inv == null || ItemList.isPluginItem(toAdd)) {
            return false;
        }
        for (int i = 0; i < 5; i++) {
            ItemStack container = inv.getItem(8 + (9 * i));
            if (container == null) {
                continue;
            }
            addDataToContainer(container, toAdd);
            if (toAdd.getAmount() < 1) {
                break;
            }
        }
        return toAdd.getAmount() <= 0;
    }

    /*
    Get the amount of material being stored on the one line of lore in a container
     */
    private static int getMaterialAmount(String str) {
        int len = str.split("\\s+").length;
        String matAmt = str.split("\\s+")[len - 1];
        return Integer.parseInt(matAmt);
    }

    public static int getTotalItemAmount(Inventory inv, ItemStack template) {
        if (inv == null || template == null) {
            return 0;
        }
        int amount = 0;
        ItemStack normalized = normalize(template);
        for (int i = 0; i < 5; i++) {
            ItemStack container = inv.getItem(8 + (9 * i));
            if (container == null) {
                continue;
            }
            ItemMeta meta = container.getItemMeta();
            List<String> lore = meta != null ? meta.getLore() : null;
            int slots = getTypeSlotCount(lore);
            for (int s = 0; s < slots; s++) {
                ItemStack stored = getStoredTemplate(container, s);
                if (stored != null && stored.isSimilar(normalized)) {
                    amount += getStoredAmount(container, s);
                }
            }
        }
        return amount;
    }

    /*
    Get a complete list of types in a DSU
     */
    public static Set<ItemStack> getTotalTemplates(Inventory dsu) {
        LinkedHashSet<ItemStack> list = new LinkedHashSet<>();
        if (dsu == null) {
            return list;
        }
        for (int i = 0; i < 5; i++) {
            ItemStack container = dsu.getItem(8 + (9 * i));
            if (!ItemList.isGroup(container, ItemList.GROUP_STORAGE_CONTAINER)) {
                continue;
            }
            ItemMeta meta = container.getItemMeta();
            List<String> lore = meta != null ? meta.getLore() : null;
            int slots = getTypeSlotCount(lore);
            for (int s = 0; s < slots; s++) {
                ItemStack stored = getStoredTemplate(container, s);
                if (stored != null && getStoredAmount(container, s) > 0) {
                    list.add(stored);
                }
            }
        }
        return list;
    }

    public static boolean dsuContainsItem(Inventory dsu, ItemStack template) {
        ItemStack normalized = normalize(template);
        for (ItemStack stored : getTotalTemplates(dsu)) {
            if (stored != null && stored.isSimilar(normalized)) {
                return true;
            }
        }
        return false;
    }


    public static int takeItems(ItemStack template, Inventory inv, int amt) {
        if (inv == null || template == null) {
            return 0;
        }
        int remaining = amt;
        int taken = 0;
        ItemStack normalized = normalize(template);
        for (int i = 4; i >= 0 && remaining > 0; i--) {
            ItemStack container = inv.getItem(8 + (9 * i));
            if (!ItemList.isGroup(container, ItemList.GROUP_STORAGE_CONTAINER)) {
                continue;
            }
            ItemMeta meta = container.getItemMeta();
            List<String> lore = meta != null ? meta.getLore() : null;
            int slots = getTypeSlotCount(lore);
            for (int s = 0; s < slots && remaining > 0; s++) {
                ItemStack stored = getStoredTemplate(container, s);
                int storedAmount = getStoredAmount(container, s);
                if (stored != null && stored.isSimilar(normalized) && storedAmount > 0) {
                    int remove = Math.min(storedAmount, remaining);
                    setStoredAmount(container, s, storedAmount - remove);
                    if (storedAmount - remove <= 0) {
                        setStoredTemplate(container, s, null);
                    }
                    remaining -= remove;
                    taken += remove;
                    rewriteContainerLore(container);
                }
            }
        }
        return taken;
    }

    private static int getTypeSlotCount(List<String> lore) {
        if (lore == null || lore.size() < 2) {
            return DeepStoragePlus.maxTypes;
        }

        int maxFromLore = DeepStoragePlus.maxTypes;
        try {
            String data = getData(lore.get(1));
            maxFromLore = getMaxData(data);
        } catch (Exception ignored) {
            // Fallback to configured default when parsing legacy/invalid lore.
        }

        int availableLines = Math.max(0, lore.size() - 2);
        int capped = Math.max(1, maxFromLore);
        return Math.min(capped, availableLines);
    }
}
