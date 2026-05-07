package me.darkolythe.deepstorageplus.dsu.managers;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import me.darkolythe.deepstorageplus.dsu.StorageUtils;
import me.darkolythe.deepstorageplus.utils.ItemList;
import me.darkolythe.deepstorageplus.utils.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Level;

import static org.bukkit.Bukkit.getLogger;

public class SorterManager {

    private final DeepStoragePlus main;
    public SorterManager(DeepStoragePlus plugin) {
        main = plugin;
    }

    /*
    Create the sorter inventory and make it so that it's correct upon opening
    */
    public static void verifyInventory(Inventory inv, Player player) {
        for (int i = 0; i < 9; i++) {
            inv.setItem( 18 + i, getSorterWall());
        }

        for (int i = 0; i < 27; i++) {
            if (inv.getItem(27 + i) == null) {
                inv.setItem(27 + i, getEmptyBlock());
            }
        }
    }

    private static ItemStack sorterWall;
    
    /*
    Create a dsu Wall item to fill the dsu Inventory
     */
    public static ItemStack getSorterWall() {
    	if (sorterWall != null)
    		return sorterWall;
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bordermeta = border.getItemMeta();
        if (bordermeta != null) {
            bordermeta.setDisplayName(ChatColor.DARK_GRAY + LanguageManager.getValue("sorterwalls"));
            bordermeta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(DeepStoragePlus.getInstance(), "item_id"), org.bukkit.persistence.PersistentDataType.STRING, "sorter_wall");
            bordermeta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(DeepStoragePlus.getInstance(), "item_group"), org.bukkit.persistence.PersistentDataType.STRING, ItemList.GROUP_SUPPORT);
            border.setItemMeta(bordermeta);
        }

        return sorterWall = border;
    }

    /*
    Create an Empty Block item to fill the dsu Inventory
     */
    public static ItemStack getEmptyBlock() {
        ItemStack storage = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta storagemeta = storage.getItemMeta();
        if (storagemeta != null) {
            storagemeta.setDisplayName(ChatColor.YELLOW + LanguageManager.getValue("emptysorterblock"));
            storagemeta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(DeepStoragePlus.getInstance(), "item_id"), org.bukkit.persistence.PersistentDataType.STRING, "sorter_empty_block");
            storagemeta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(DeepStoragePlus.getInstance(), "item_group"), org.bukkit.persistence.PersistentDataType.STRING, ItemList.GROUP_SUPPORT);
            storage.setItemMeta(storagemeta);
        }

        return storage;
    }


    public static boolean sortItems(Inventory sorterInventory) {
        Set<Location> locations = new HashSet<>(SorterManager.getLinkedLocations(sorterInventory));
        Set<Inventory> dsuInventories = SorterManager.getDSUInventories(locations);
        return moveItems(sorterInventory, dsuInventories);
    }

    private static boolean moveItems(Inventory sorterInventory, Set<Inventory> dsuInventories) {
        boolean success = true;
        for (int i = 0; i < 18; i++) {
            ItemStack item = sorterInventory.getItem(i);
            if (item == null || item.getType() == Material.AIR || ItemList.isPluginItem(item)) {
                continue;
            }
            ItemStack moving = item.clone();
            for (Inventory dsu : dsuInventories) {
                if (moving.getAmount() <= 0) {
                    break;
                }
                DSUManager.addToDSUSilent(moving, dsu);
            }
            sorterInventory.setItem(i, moving.getAmount() > 0 ? moving : null);
            success = success && moving.getAmount() <= 0;
        }
        return success;
    }

    /**
     * Recursively find the location of all sorters and DSUs linked to this sorter and linked to sorters linked to this sorter.
     * We should cache this where possible, but we can't know when it's invalidated with certainty because sorters down the tree
     * may be changed without our knowing. Refreshing only when we fail to find a spot for something or a link card is added to the root sorter
     * should be fine.
     * @param inv
     * @return
     */
    private static List<Location> getLinkedLocations(Inventory inv) {
        List<Location> locations = getLinkedLocations(inv, new ArrayList<>());
        for (int i = 0; i < locations.size(); i++) {
            // Check if this location has another sorter
            Block block = locations.get(i).getBlock();
            if (block.getType() == Material.CHEST && StorageUtils.getChestCustomName(block).orElse("").equals(DeepStoragePlus.sortername)) {
                List<Location> newLocations = getLinkedLocations(((Container) block.getState()).getInventory(), locations);
                locations.addAll(newLocations);
            }
        }
        return locations;
    }

    private static List<Location> getLinkedLocations(Inventory inv, List<Location> locations) {
        // Check each link module slot for locations referenced in this sorter
        List<Location> newLocations = new ArrayList<>();
        for (int i = 27; i < 54; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null) { // Only link modules should be possible in these slots
                getLinkModuleLocation(item).ifPresent(x -> {
                    if (!locations.contains(x)) {
                        newLocations.add(x);
                    }
                });
            }
        }
        return newLocations;
    }

    private static Optional<Location> getLinkModuleLocation(ItemStack linkModule) {
        ItemMeta meta = linkModule != null ? linkModule.getItemMeta() : null;
        List<String> lore = meta != null ? meta.getLore() : null;
        if (ItemList.isItem(linkModule, ItemList.KEY_LINK_MODULE) && lore != null && !lore.isEmpty()) {
            try {
                String[] loreLocationArr = ChatColor.stripColor(lore.get(0)).split("\\s+");
                if (loreLocationArr.length == 4) {
                    return Optional.of(new Location(
                            Bukkit.getWorld(loreLocationArr[0]),
                            Double.parseDouble(loreLocationArr[1]),
                            Double.parseDouble(loreLocationArr[2]),
                            Double.parseDouble(loreLocationArr[3]))
                    );
                }
            }
            catch (Exception ignored) {
                getLogger().log(Level.INFO, "Exception parsing link module lore " + lore.get(0));
            }
        }
        return Optional.empty();
    }

    /**
     * Get each DSU inventory from a list of locations (which may contain DSUs and Sorters
     * @param locations
     * @return
     */
    public static Set<Inventory> getDSUInventories(Set<Location> locations) {
        Set<Inventory> inventories = new HashSet<>();
        for (Location location: locations) {
            Block block = location.getBlock();
            if (block.getType() == Material.CHEST && StorageUtils.getChestCustomName(block).orElse("").equals(DeepStoragePlus.DSUname)) {
                inventories.add(((Container) block.getState()).getInventory());
            }
        }
        return inventories;
    }

}
