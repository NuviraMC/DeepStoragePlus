package me.darkolythe.deepstorageplus.dsu.managers;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import me.darkolythe.deepstorageplus.utils.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class DSUUpdateManager {

    private final DeepStoragePlus main;
    public DSUUpdateManager(DeepStoragePlus plugin) {
        main = plugin;
    }

    /*
    Update the items in the dsu. This is done when items are added, taken, Storage Containers are added, taken, and when opening the dsu.
     */
    public void updateItemsExact(Inventory inv) {
        if (inv.getLocation() == null) {
            return;
        }
        if (!DeepStoragePlus.recentDSUCalls.containsKey(inv.getLocation())) {
            DeepStoragePlus.recentDSUCalls.put(inv.getLocation(), 0L);
        }
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () -> {
            // if over a second has passed since the last bulk item was placed in the dsu, update it
            if (!DeepStoragePlus.recentDSUCalls.containsKey(inv.getLocation())) {
                return;
            }
            if (System.currentTimeMillis() - DeepStoragePlus.recentDSUCalls.get(inv.getLocation()) < 200) {
                return;
            }

            DeepStoragePlus.recentDSUCalls.put(inv.getLocation(), System.currentTimeMillis());

            refreshDisplayItems(inv);

            for (UUID key : DeepStoragePlus.stashedDSU.keySet()) {
                if (!DeepStoragePlus.openDSU.containsKey(key) || DeepStoragePlus.openDSU.get(key) == null) {
                    continue;
                }
                Inventory openInv = DeepStoragePlus.openDSU.get(key).getInventory();
                if (Objects.equals(inv.getItem(8), openInv.getItem(8))) {
                    sortInventory(inv);
                    return;
                }
            }
        }, 5L);
    }

    private void sortInventory(Inventory inv) {
        ItemStack ioSettings = inv.getItem(53);
        if (ioSettings != null && ioSettings.hasItemMeta()) {
            ItemMeta meta = ioSettings.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<String> lore = meta.getLore();
                String sort = lore != null && lore.size() > 2
                        ? lore.get(2).replace(ChatColor.GRAY + LanguageManager.getValue("sortingby") + ": " + ChatColor.BLUE, "")
                        : LanguageManager.getValue("container");

                if (sort.equals(LanguageManager.getValue("container"))) {
                    clearItems(inv);
                    addNewItems(inv);
                } else if (sort.equals(LanguageManager.getValue("amount"))) {
                    clearItems(inv);
                    Map<ItemStack, Double> data = new HashMap<>();
                    List<ItemStack> templates = getTemplates(inv);
                    for (ItemStack template : templates) {
                        data.put(template, (double) DSUManager.getTotalItemAmount(inv, template));
                    }
                    int dataAmount = data.size();
                    for (int i = 0; i < dataAmount; i++) {
                        double top = 0;
                        ItemStack topTemplate = null;
                        for (ItemStack template : data.keySet()) {
                            if (data.get(template) > top) {
                                topTemplate = template;
                                top = data.get(template);
                            }
                        }
                        inv.addItem(createItem(topTemplate, inv));
                        data.remove(topTemplate);
                    }
                } else if (sort.equalsIgnoreCase(LanguageManager.getValue("alpha"))) {
                    List<ItemStack> templates = getTemplates(inv);
                    templates.sort(Comparator.comparing(m -> m.getItemMeta() != null && m.getItemMeta().hasDisplayName() ? ChatColor.stripColor(m.getItemMeta().getDisplayName()) : m.getType().toString()));
                    clearItems(inv);
                    for (ItemStack template : templates) {
                        inv.addItem(createItem(template, inv));
                    }
                } else if (sort.equalsIgnoreCase("ID")) {
                    clearItems(inv);

                    List<ItemStack> list = DSUManager.getTotalTemplates(inv).stream()
                            .toList();
                    for (ItemStack template : list) {
                        inv.addItem(createItem(template, inv));
                    }
                }
            }
        }
        updateInventory(inv);
    }

    private static void addNewItems(Inventory inv) {
        Set<ItemStack> templates = DSUManager.getTotalTemplates(inv);
        for (ItemStack template : templates) {
            boolean canAdd = true;
            for (ItemStack it : inv.getContents()) {
                if (it != null && it.hasItemMeta() && template.isSimilar(it)) {
                    canAdd = false;
                    break;
                }
            }
            if (canAdd) {
                inv.addItem(createItem(template, inv));
            }
        }
    }

    private static void refreshDisplayItems(Inventory inv) {
        clearItems(inv);
        addNewItems(inv);
    }

    private void updateInventory(Inventory inv) {
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () -> {
            if (inv.getLocation() != null) {
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

    private static List<ItemStack> getTemplates(Inventory inv) {
        return new ArrayList<>(DSUManager.getTotalTemplates(inv));
    }


    public static ItemStack createItem(ItemStack template, Inventory inv) {
        ItemStack item = template == null ? new ItemStack(Material.AIR) : template.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (meta.hasLore() && meta.getLore() != null) {
                lore.addAll(meta.getLore());
            }
            lore.add(ChatColor.GRAY + "Item Count: " + DSUManager.getTotalItemAmount(inv, template));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }
}
