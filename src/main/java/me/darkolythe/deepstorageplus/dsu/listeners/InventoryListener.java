package me.darkolythe.deepstorageplus.dsu.listeners;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import me.darkolythe.deepstorageplus.dsu.StorageUtils;
import me.darkolythe.deepstorageplus.dsu.managers.DSUManager;
import me.darkolythe.deepstorageplus.dsu.managers.SorterManager;
import me.darkolythe.deepstorageplus.utils.ItemList;
import me.darkolythe.deepstorageplus.utils.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static me.darkolythe.deepstorageplus.dsu.StorageUtils.matToString;
import static me.darkolythe.deepstorageplus.dsu.managers.SettingsManager.*;

public class InventoryListener implements Listener {

    private final DeepStoragePlus main;
    private final Map<UUID, Integer> ioSelectionSlot = new ConcurrentHashMap<>();

    public InventoryListener(DeepStoragePlus plugin) {
        this.main = plugin; // set it equal to an instance of main
    }

    @EventHandler
    private void onStorageOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            if (event.getInventory().getSize() == 54) {
                if (event.getView().getTitle().equals(DeepStoragePlus.DSUname) || StorageUtils.isDSU(event.getInventory())) {
                    ItemStack lock = event.getInventory().getItem(53);
                    boolean isOp = player.hasPermission("deepstorageplus.adminopen");
                    boolean isLocked = isLocked(lock);
                    boolean canOpen = getLocked(lock, player);

                    if (canOpen || isOp || !isLocked) {
                        DeepStoragePlus.stashedDSU.put(player.getUniqueId(), event.getInventory());
                        if (event.getInventory().getLocation() != null) {
                            DeepStoragePlus.openDSU.put(player.getUniqueId(), (Container) event.getInventory().getLocation().getBlock().getState());
                        }
                        DSUManager.verifyInventory(event.getInventory(), player);
                        main.dsuupdatemanager.updateItems(event.getInventory(), null);
                        return;
                    }
                    player.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + LanguageManager.getValue("notallowedtoopen"));
                    event.setCancelled(true);
                }
                else if (event.getView().getTitle().equals(DeepStoragePlus.sortername) || StorageUtils.isSorter(event.getInventory())) {
                    SorterManager.verifyInventory(event.getInventory(), player);
                    main.sorterUpdateManager.sortItems(event.getInventory(), DeepStoragePlus.minTimeSinceLastSortPlayer);
                }
            }
        }
    }

    @EventHandler
    private void onStorageInteract(InventoryClickEvent event) {
        if (event.getClickedInventory() != null) {
            if (event.getWhoClicked() instanceof Player player) {
                Inventory inv = event.getInventory();
                ItemStack item = event.getCurrentItem();
                ItemStack cursor = event.getCursor();
                String ioConfigTitle = ChatColor.BLUE + "" + ChatColor.BOLD + LanguageManager.getValue("dsuioconfig");

                if (event.getView().getTitle().equals(ioConfigTitle)) {
                    event.setCancelled(true);
                    if (event.getSlot() == 8 || event.getSlot() == 17) {
                        ioSelectionSlot.put(player.getUniqueId(), event.getSlot());
                        startSelection(event.getSlot(), inv);
                    } else { //change selection and io items
                        int selectedSlot = ioSelectionSlot.getOrDefault(player.getUniqueId(), findActiveSelectionSlot(inv));
                        boolean canSelectFromTop = event.getClickedInventory() == inv && event.getSlot() % 9 != 8 && event.getSlot() % 9 != 7;
                        boolean canSelectFromBottom = event.getClickedInventory() == player.getInventory();
                        if ((canSelectFromTop || canSelectFromBottom) && item != null && item.getType() != Material.AIR) {
                            if (selectedSlot == 8 || selectedSlot == 17) {
                                ItemStack newitem = item.clone();
                                ItemMeta itemmeta = newitem.getItemMeta();
                                if (itemmeta != null) {
                                    if (selectedSlot == 8) {
                                        itemmeta.setDisplayName(ChatColor.GRAY + LanguageManager.getValue("input") + ": " + ChatColor.GREEN + matToString(newitem.getType()));
                                    } else {
                                        itemmeta.setDisplayName(ChatColor.GRAY + LanguageManager.getValue("output") + ": " + ChatColor.GREEN + matToString(newitem.getType()));
                                    }
                                    itemmeta.setLore(List.of(ChatColor.GRAY + LanguageManager.getValue("clicktoclear")));
                                    newitem.setItemMeta(itemmeta);
                                }
                                inv.setItem(selectedSlot, newitem);
                                ioSelectionSlot.remove(player.getUniqueId());
                            }
                        } else { //change sorting types in io config
                            if (item != null && item.getType() == Material.COMPASS) {
                                if (event.getClick() != ClickType.DOUBLE_CLICK) {
                                    ItemMeta meta = item.getItemMeta();
                                    if (meta != null) {
                                        String displayName = meta.getDisplayName();
                                        if (displayName.contains(LanguageManager.getValue("container"))) {
                                            meta.setDisplayName(displayName.replace(LanguageManager.getValue("container"), LanguageManager.getValue("alpha")));
                                        } else if (displayName.contains(LanguageManager.getValue("alpha"))) {
                                            meta.setDisplayName(displayName.replace(LanguageManager.getValue("alpha"), LanguageManager.getValue("amount")));
                                        } else if (displayName.contains(LanguageManager.getValue("amount"))) {
                                            meta.setDisplayName(displayName.replace(LanguageManager.getValue("amount"), "ID"));
                                        } else {
                                            meta.setDisplayName(displayName.replace("ID", LanguageManager.getValue("container")));
                                        }
                                        item.setItemMeta(meta);
                                    }
                                    inv.setItem(event.getSlot(), item);
                                }
                                } else if (item != null && ItemList.KEY_DSU_LOCK.equals(ItemList.getItemId(item))) {
                                boolean isOwner = player.getUniqueId().toString().equals(getOwner(item)[1]);
                                boolean isOp = player.hasPermission("deepstorageplus.adminopen");
                                if (isOwner || isOp) { //only the owner or admin can edit the lock settings
                                    if (event.getClick() == ClickType.RIGHT) {
                                        ItemMeta meta = item.getItemMeta();
                                            if (meta != null) {
                                                meta.setLore(List.of(
                                                        ChatColor.GRAY + LanguageManager.getValue("leftclicktoadd"),
                                                        ChatColor.GRAY + LanguageManager.getValue("rightclicktoremove"),
                                                        "",
                                                        ChatColor.GRAY + LanguageManager.getValue("owner") + ": " + ChatColor.BLUE + getOwner(item)[0],
                                                        ChatColor.GREEN + LanguageManager.getValue("unlocked")));
                                                item.setItemMeta(meta);
                                            }
                                    } else if (event.getClick() == ClickType.LEFT) {
                                        player.sendMessage(DeepStoragePlus.prefix + ChatColor.GRAY + LanguageManager.getValue("entername"));
                                        player.sendMessage(ChatColor.GRAY + LanguageManager.getValue("typecancel"));
                                        DeepStoragePlus.stashedIO.put(player.getUniqueId(), inv);
                                        DeepStoragePlus.gettingInput.put(player.getUniqueId(), true);
                                        player.closeInventory();
                                    }
                                } else {
                                    player.sendMessage(DeepStoragePlus.prefix + ChatColor.GRAY + LanguageManager.getValue("notowner"));
                                }
                            }
                        }
                    }
                } else if (event.getView().getTitle().equals(DeepStoragePlus.DSUname) || StorageUtils.isDSU(inv)) {
                    if (event.getClickedInventory() != player.getInventory()) {
                        if (event.getSlot() % 9 == 8) { //rightmost column
                            if (event.getSlot() != 53) { //if containers clicked
                                if (cursor != null && cursor.getType() != Material.AIR) { //if putting container in
                                    if (item != null && item.getType() == Material.WHITE_STAINED_GLASS_PANE) {
                                        event.setCancelled(true);
                                            if (cursor.hasItemMeta()) { //if putting a Storage Container in the dsu
                                                if (ItemList.isGroup(cursor, ItemList.GROUP_STORAGE_CONTAINER)) {
                                                inv.setItem(event.getSlot(), cursor);
                                                player.setItemOnCursor(new ItemStack(Material.AIR));
                                                main.dsuupdatemanager.updateItems(inv, null);
                                            }
                                        }
                                    } else { //if trying to take placeholder out
                                        if (!ItemList.isGroup(cursor, ItemList.GROUP_STORAGE_CONTAINER)) {
                                            event.setCancelled(true);
                                        } else if (event.isShiftClick()) {
                                            event.setCancelled(true);
                                        }
                                    }
                                } else { //if taking container out
                                    event.setCancelled(true);
                                    if (item != null && item.getType() != Material.WHITE_STAINED_GLASS_PANE) {
                                        player.setItemOnCursor(item.clone());
                                        inv.setItem(event.getSlot(), DSUManager.getEmptyBlock());
                                        main.dsuupdatemanager.updateItems(inv, null);
                                    }
                                }
                            } else { //if io is clicked
                                event.setCancelled(true);
                                if (cursor == null || cursor.getType() == Material.AIR) {
                                    if (item != null && item.hasItemMeta()) {
                                            if (ItemList.isItem(item, ItemList.KEY_IO_SETTINGS)) { //BOTTOM RIGHT FOR SETTINGS
                                            player.openInventory(createIOInventory(inv));
                                        }
                                    }
                                }
                            }
                        } else if (event.getSlot() % 9 == 7) { //walls
                            event.setCancelled(true);
                        } else { //items
                            event.setCancelled(true);
                            if (cursor != null && cursor.getType() != Material.AIR) {
                                Material mat = cursor.getType();
                                boolean isvaliditem = DSUManager.addToDSU(cursor, event.getClickedInventory(), player); //try to add item to dsu

                                main.dsuupdatemanager.updateItems(inv, mat);

                                if (cursor.getAmount() > 0 && isvaliditem) {
                                    player.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + LanguageManager.getValue("containersfull"));
                                }
                            } else if (cursor == null || cursor.getType() == Material.AIR && item != null) { //taking item out of dsu
                                if (event.getClick() != ClickType.DOUBLE_CLICK) {
                                    Material mat = item != null ? item.getType() : Material.AIR;
                                    if (event.isShiftClick()) {
                                        if (player.getInventory().firstEmpty() != -1) {
                                            int amtTaken = DSUManager.takeItems(mat, inv, mat.getMaxStackSize());
                                            if (amtTaken > 0) {
                                                player.getInventory().addItem(new ItemStack(mat, amtTaken));
                                            }
                                        } else {
                                                    player.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + LanguageManager.getValue("nomorespace"));
                                        }
                                    } else {
                                        int amtTaken = DSUManager.takeItems(mat, inv, mat.getMaxStackSize());
                                        if (amtTaken > 0) {
                                            player.setItemOnCursor(new ItemStack(mat, amtTaken));
                                        }
                                    }

                                    main.dsuupdatemanager.updateItems(inv, mat);
                                }
                            }
                        }
                    } else { //if click is in player inventory
                        if (event.isShiftClick()) {
                            if (item != null && item.getType() != Material.AIR) {
                                main.dsumanager.addItemToDSU(item, player);
                                event.setCancelled(true);
                            }
                        } else if (event.getClick() == ClickType.DOUBLE_CLICK) {
                            event.setCancelled(true);
                        }
                    }

                } else if (event.getView().getTitle().equals(DeepStoragePlus.sortername) || StorageUtils.isSorter(inv)) {
                    if (event.getClickedInventory() != player.getInventory()) {
                        if (event.getSlot() > 26) { // link module field
                            if (cursor != null && cursor.getType() != Material.AIR) { //if putting container in
                                if (item != null && item.getType() == Material.WHITE_STAINED_GLASS_PANE) {
                                    event.setCancelled(true);
                                    //if putting a link module into the sorter
                                    if (ItemList.isItem(cursor, ItemList.KEY_LINK_MODULE)) {
                                        inv.setItem(event.getSlot(), cursor);
                                        player.setItemOnCursor(new ItemStack(Material.AIR));
                                    }
                                } else { //if trying to take placeholder out
                                    if (!ItemList.isItem(cursor, ItemList.KEY_LINK_MODULE)) {
                                        event.setCancelled(true);
                                    } else if (event.isShiftClick()) {
                                        event.setCancelled(true);
                                    }
                                }
                            } else { //if taking link module out
                                event.setCancelled(true);
                                if (item != null && item.getType() != Material.WHITE_STAINED_GLASS_PANE) {
                                    player.setItemOnCursor(item.clone());
                                    inv.setItem(event.getSlot(), DSUManager.getEmptyBlock());
                                }
                            }
                        } else if (event.getSlot() > 17 && event.getSlot() < 27) { //walls
                            event.setCancelled(true);
                        } else { //items
                            if (cursor != null && cursor.getType() != Material.AIR) { //putting an item into the sorter
                                main.sorterUpdateManager.sortItems(inv, DeepStoragePlus.minTimeSinceLastSortPlayer);
                            }
                        }
                    }
                    else { //if click is in player inventory
                        if (event.isShiftClick()) {
                            if (item != null && item.getType() != Material.AIR) {
                                main.sorterUpdateManager.sortItems(inv, DeepStoragePlus.minTimeSinceLastSortPlayer);
                            }
                        }
                    }

                }
            }
        }
    }

    @EventHandler
    private void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(DeepStoragePlus.DSUname) || StorageUtils.isDSU(event.getInventory())) {
            if (event.getWhoClicked() instanceof Player) {
                for (Integer slot : event.getRawSlots()) {
                    if (slot <= 51) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    /*
     * Update DSU with IO settings when closing IO settings menu
     */
    @EventHandler
    private void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
                    if (event.getView().getTitle().equals(ChatColor.BLUE + "" + ChatColor.BOLD + LanguageManager.getValue("dsuioconfig"))) {
                Container DSUContainer = DeepStoragePlus.openDSU.get(player.getUniqueId());
                Inventory DSU = DSUContainer != null ? DSUContainer.getInventory() : DeepStoragePlus.stashedDSU.get(player.getUniqueId());
                if (DSU == null) {
                    return;
                }

                Inventory IOInv = event.getInventory();
                ItemStack input = IOInv.getItem(8);
                ItemStack output = IOInv.getItem(17);
                ItemStack sorting = IOInv.getItem(26);
                ItemStack lock = IOInv.getItem(53);

                ItemStack speedItem = DSU.getItem(53);
                int speedUpgrade = speedItem != null ? getSpeedUpgrade(speedItem) : 0;

                List<String> lore = new ArrayList<>();

                if (input != null && input.getType() != Material.WHITE_STAINED_GLASS_PANE && input.getType() != Material.AIR) {
                    lore.add(ChatColor.GRAY + LanguageManager.getValue("input") + ": " + ChatColor.GREEN + matToString(input.getType()));
                } else {
                    lore.add(ChatColor.GRAY + LanguageManager.getValue("input") + ": " + ChatColor.BLUE + LanguageManager.getValue("all"));
                }
                if (output != null && output.getType() != Material.WHITE_STAINED_GLASS_PANE && output.getType() != Material.AIR) {
                    lore.add(ChatColor.GRAY + LanguageManager.getValue("output") + ": " + ChatColor.GREEN + matToString(output.getType()));
                } else {
                    lore.add(ChatColor.GRAY + LanguageManager.getValue("output") + ": " + ChatColor.BLUE + LanguageManager.getValue("none"));
                }
                ItemMeta sortingMeta = sorting != null && sorting.hasItemMeta() ? sorting.getItemMeta() : null;
                if (sortingMeta != null) {
                    lore.add(sortingMeta.getDisplayName());
                }

                lore.add(ChatColor.GRAY + LanguageManager.getValue("iospeed") + ": " + ChatColor.GREEN + "+" + speedUpgrade);

                lore.add(ChatColor.GRAY + LanguageManager.getValue("owner") + ": " + ChatColor.BLUE + getOwner(lock)[0]);

                ItemMeta lockMeta = lock != null && lock.hasItemMeta() ? lock.getItemMeta() : null;
                List<String> locklore = lockMeta != null ? lockMeta.getLore() : null;
                if (locklore != null && locklore.contains(ChatColor.RED + LanguageManager.getValue("locked"))) {
                    lore.add(ChatColor.RED + LanguageManager.getValue("locked"));
                    for (String s : getLockedUsers(lock)) {
                        lore.add(ChatColor.WHITE + s);
                    }
                } else {
                    lore.add(ChatColor.GREEN + LanguageManager.getValue("unlocked"));
                }

                ItemStack i = DSU.getItem(53);
                if (i != null && i.getItemMeta() != null) {
                    ItemMeta m = i.getItemMeta();
                    m.setLore(lore);
                    i.setItemMeta(m);
                }

                // Open the DSU's main inventory after the player closes the settings menu
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () -> {
                        if (DeepStoragePlus.gettingInput.containsKey(player.getUniqueId()) && Boolean.FALSE.equals(DeepStoragePlus.gettingInput.get(player.getUniqueId()))) {
                        player.openInventory(DSU);
                    }
                }, 1L);
            } else if (event.getView().getTitle().equals(DeepStoragePlus.DSUname) || StorageUtils.isDSU(event.getInventory())) {
                DeepStoragePlus.stashedDSU.remove(player.getUniqueId());
                ioSelectionSlot.remove(player.getUniqueId());
                if (DeepStoragePlus.loadedChunks.containsKey(player.getUniqueId())) {
                    Chunk c = DeepStoragePlus.loadedChunks.get(player.getUniqueId());
                    c.unload();
                    DeepStoragePlus.loadedChunks.remove(player.getUniqueId());
                }
            } else if (event.getView().getTitle().equals(DeepStoragePlus.sortername) || StorageUtils.isSorter(event.getInventory())) {
                ioSelectionSlot.remove(player.getUniqueId());
                if (main.sorterUpdateManager != null) {
                    main.sorterUpdateManager.sortItems(event.getInventory(), DeepStoragePlus.minTimeSinceLastSortPlayer);
                }
            }
        }
    }

    private int findActiveSelectionSlot(Inventory inv) {
        ItemStack input = inv.getItem(8);
        if (input != null && !input.getEnchantments().isEmpty()) {
            return 8;
        }
        ItemStack output = inv.getItem(17);
        if (output != null && !output.getEnchantments().isEmpty()) {
            return 17;
        }
        return -1;
    }

    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (DeepStoragePlus.gettingInput.containsKey(player.getUniqueId()) && Boolean.TRUE.equals(DeepStoragePlus.gettingInput.get(player.getUniqueId()))) {
            if (event.getMessage().equalsIgnoreCase("cancel")) {
                DeepStoragePlus.gettingInput.put(player.getUniqueId(), false);
                DeepStoragePlus.openIOInv.put(player.getUniqueId(), true);
                event.setCancelled(true);
                return;
            }
            Inventory openIO = DeepStoragePlus.stashedIO.get(player.getUniqueId());
            ItemStack lock = createDSULock(openIO);
            ItemMeta meta = lock.getItemMeta();
            List<String> lore = meta != null ? meta.getLore() : null;
            if (lore != null && getLockedUsers(lock).isEmpty()) {
                lore.set(lore.size() - 1, ChatColor.RED + LanguageManager.getValue("locked"));
            }
            if (lore != null) {
                lore.add(ChatColor.WHITE + event.getMessage());
                meta.setLore(lore);
                lock.setItemMeta(meta);
                openIO.setItem(53, lock);
            }

            DeepStoragePlus.gettingInput.put(player.getUniqueId(), false);
            DeepStoragePlus.openIOInv.put(player.getUniqueId(), true);

            event.setCancelled(true);
        }
    }

    public void addText() {
        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(main, () -> {
            for (java.util.UUID playerId : DeepStoragePlus.openIOInv.keySet()) {
                Player player = Bukkit.getOnlinePlayers().stream().filter(p -> p.getUniqueId().equals(playerId)).findFirst().orElse(null);
                if (player == null) {
                    DeepStoragePlus.openIOInv.remove(playerId);
                    continue;
                }
                if (DeepStoragePlus.stashedIO.containsKey(playerId)) {
                    player.openInventory(DeepStoragePlus.stashedIO.get(playerId));
                    DeepStoragePlus.openIOInv.remove(playerId);
                    DeepStoragePlus.stashedIO.remove(playerId);
                }
            }
        }, 1L, 5L);
    }
}
