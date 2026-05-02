package me.darkolythe.deepstorageplus.dsu.managers;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import me.darkolythe.deepstorageplus.utils.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class DSUUpdateManager {

    private DeepStoragePlus main;
    public DSUUpdateManager(DeepStoragePlus plugin) {
        main = plugin;
    }

    /*
    Update the items in the dsu. This is done when items are added, taken, Storage Containers are added, taken, and when opening the dsu.
     */
    public void updateItems(Inventory inv, Material mat) {
        if (inv.getLocation() == null) {
            return;
        }
        if (!DeepStoragePlus.recentDSUCalls.containsKey(inv.getLocation())) {
            DeepStoragePlus.recentDSUCalls.put(inv.getLocation(), 0L);
        }
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, new Runnable() {
            @Override
            public void run() {
                // if over a second has passed since the last bulk item was placed in the dsu, update it
                if (!DeepStoragePlus.recentDSUCalls.containsKey(inv.getLocation())) {
                    return;
                }
                if (System.currentTimeMillis() - DeepStoragePlus.recentDSUCalls.get(inv.getLocation()) < 200) {
                    return;
                }

                DeepStoragePlus.recentDSUCalls.put(inv.getLocation(), System.currentTimeMillis());

                if (mat != null && DSUManager.getTotalTypes(inv).contains(mat)) {
                    for (int i = 0; i < 54; i++) {
                        if (i % 9 != 8 && i % 9 != 7) {
                            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                                break;
                            }
                            if (inv.getItem(i).getType() == mat) {
                                inv.setItem(i, createItem(inv.getItem(i).getType(), inv));
                                return;
                            }
                        }
                    }
                }

                for (UUID key : DeepStoragePlus.stashedDSU.keySet()) {
                    Inventory openInv = DeepStoragePlus.openDSU.get(key).getInventory();
                    if (inv.getItem(8).equals(openInv.getItem(8))) {
                        sortInventory(inv);
                        return;
                    }
                }
            }
        }, 5L);
    }

    private void sortInventory(Inventory inv) {
        ItemStack IOSettings = inv.getItem(53);
        if (IOSettings != null && IOSettings.hasItemMeta() && IOSettings.getItemMeta().hasLore()) {
            String sort = IOSettings.getItemMeta().getLore().get(2).replace(ChatColor.GRAY + LanguageManager.getValue("sortingby") + ": " + ChatColor.BLUE, "");

            if (sort.equals(LanguageManager.getValue("container"))) {
                clearItems(inv);
                addNewItems(inv);
            } else if (sort.equals(LanguageManager.getValue("amount"))) {
                clearItems(inv);
                Map<Material, Double> data = new HashMap<>();
                List<Material> mats = getMats(inv);
                for (Material m : mats) {
                    data.put(m, (double) DSUManager.getTotalMaterialAmount(inv, m));
                }
                int dataAmount = data.keySet().size();
                for (int i = 0; i < dataAmount; i++) {
                    double top = 0;
                    Material topMat = Material.AIR;
                    for (Material m : data.keySet()) {
                        if ((data.get(m) > top)) {
                            topMat = m;
                            top = data.get(m);
                        }
                    }
                    inv.addItem(createItem(topMat, inv));
                    data.remove(topMat);
                }
            } else if (sort.equalsIgnoreCase(LanguageManager.getValue("alpha"))) {
                List<Material> mats = getMats(inv);
                Collections.sort(mats, Comparator.comparing(Material::toString));
                clearItems(inv);
                for (Material m : mats) {
                    inv.addItem(createItem(m, inv));
                }
            } else if (sort.equalsIgnoreCase("ID")) {
                clearItems(inv);

                SortedSet<Material> list = new TreeSet<>();
                list.addAll(DSUManager.getTotalTypes(inv));
                for (Material m : list) {
                    inv.addItem(createItem(m, inv));
                }
            }
        }
        updateInventory(inv);
    }

    private static void addNewItems(Inventory inv) {
        Set<Material> mats = DSUManager.getTotalTypes(inv);
        for (Material m : mats) {
            ItemStack item = new ItemStack(m);
            boolean canAdd = true;
            for (ItemStack it : inv.getContents()) {
                if (item.equals(it)) {
                    canAdd = false;
                    break;
                }
            }
            if (canAdd) {
                inv.addItem(createItem(m, inv));
            }
        }
    }

    private void updateInventory(Inventory inv) {
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, new Runnable() {
            @Override
            public void run() {
                inv.getLocation().getBlock().getState().update();
            }
        }, 1);
    }

    private static void clearItems(Inventory inv) {
        for (int i = 0; i < 54; i++) {
            if (i % 9 != 8 && i % 9 != 7) {
                inv.setItem(i, null);
            }
        }
    }

    private static List<Material> getMats(Inventory inv) {
        Set<Material> mats = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            ItemStack container = inv.getItem(8 + (9 * i));
            if (container != null && container.getType() != Material.WHITE_STAINED_GLASS_PANE) {
                HashSet<Material> tempMats = DSUManager.getTypes(container.getItemMeta().getLore());
                mats.addAll(tempMats);
            }
        }
        return new ArrayList<>(mats);
    }

    /**
     * Creates the display item shown in the DSU grid for a given Material.
     * Preserves the item-model (NamespacedKey) configured for this material so
     * resource-pack textures survive the display-item round-trip.
     */
    static ItemStack createItem(Material mat, Inventory inv) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Item Count: " + DSUManager.getTotalMaterialAmount(inv, mat)));

        // Apply configured item-model so the display item keeps its resource-pack texture.
        DeepStoragePlus plugin = DeepStoragePlus.getInstance();
        if (plugin != null) {
            String materialKey = mat.getKey().getKey(); // e.g. "stone", "iron_ingot"
            // Check all known item-ids to see if any map to this material + have an item-model configured.
            for (String itemId : plugin.getItemList().itemListMap.keySet()) {
                ItemStack registered = plugin.getItemList().itemListMap.get(itemId);
                if (registered != null && registered.getType() == mat) {
                    String configuredModel = plugin.getConfig().getString("items." + itemId + ".item-model");
                    if (configuredModel != null && !configuredModel.isBlank() && !configuredModel.equalsIgnoreCase("none")) {
                        NamespacedKey modelKey = NamespacedKey.fromString(configuredModel, plugin);
                        if (modelKey != null) {
                            meta.setItemModel(modelKey);
                        }
                    }
                    break;
                }
            }
        }

        item.setItemMeta(meta);
        return item;
    }
}
