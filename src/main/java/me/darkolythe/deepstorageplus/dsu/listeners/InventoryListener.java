package me.darkolythe.deepstorageplus.dsu.listeners;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import me.darkolythe.deepstorageplus.dsu.StorageUtils;
import me.darkolythe.deepstorageplus.dsu.managers.DSUManager;
import me.darkolythe.deepstorageplus.dsu.managers.DSUUpdateManager;
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
    private static final String DEBUG_KEY = "debug";

    public InventoryListener(DeepStoragePlus plugin) {
        this.main = plugin;
    }

    private static ItemStack buildTakeItem(ItemStack template, int amount, Inventory dsuInv) {
        ItemStack display = DSUUpdateManager.createItem(template, dsuInv);
        ItemMeta meta = display.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> originalLore = meta.getLore();
            if (originalLore != null) {
                List<String> newLore = new ArrayList<>(originalLore);
                newLore.removeIf(line -> ChatColor.stripColor(line).startsWith("Item Count:"));
                if (newLore.isEmpty()) {
                    meta.setLore(null);
                } else {
                    meta.setLore(newLore);
                }
                display.setItemMeta(meta);
            }
        }
        display.setAmount(amount);
        return display;
    }

    private static ItemStack buildTakeTemplate(ItemStack displayItem) {
        if (displayItem == null || displayItem.getType() == Material.AIR) {
            return null;
        }
        ItemStack template = displayItem.clone();
        ItemMeta meta = template.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null) {
                List<String> newLore = new ArrayList<>(lore);
                newLore.removeIf(line -> ChatColor.stripColor(line).startsWith("Item Count:"));
                if (newLore.isEmpty()) {
                    meta.setLore(null);
                } else {
                    meta.setLore(newLore);
                }
                template.setItemMeta(meta);
            }
        }
        template.setAmount(1);
        return template;
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
                        main.dsuupdatemanager.updateItemsExact(event.getInventory());
                        return;
                    }
                    player.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + LanguageManager.getValue("notallowedtoopen"));
                    event.setCancelled(true);
                } else if (event.getView().getTitle().equals(DeepStoragePlus.sortername) || StorageUtils.isSorter(event.getInventory())) {
                    SorterManager.verifyInventory(event.getInventory(), player);
                    main.sorterUpdateManager.sortItems(event.getInventory(), DeepStoragePlus.minTimeSinceLastSortPlayer);
                }
            }
        }
    }

    @EventHandler
    private void onStorageInteract(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        ItemStack item = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        String ioConfigTitle = ChatColor.BLUE + "" + ChatColor.BOLD + LanguageManager.getValue("dsuioconfig");

        if (event.getView().getTitle().equals(ioConfigTitle)) {
            debug("IO config click: slot=" + event.getSlot() + ", click=" + event.getClick());
            event.setCancelled(true);
            if (event.getSlot() == 8 || event.getSlot() == 17) {
                ioSelectionSlot.put(player.getUniqueId(), event.getSlot());
                startSelection(event.getSlot(), inv);
                return;
            }

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
                return;
            }

            if (item != null && item.getType() == Material.COMPASS && event.getClick() != ClickType.DOUBLE_CLICK) {
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
                return;
            }

            if (item != null && ItemList.KEY_DSU_LOCK.equals(ItemList.getItemId(item))) {
                boolean isOwner = player.getUniqueId().toString().equals(getOwner(item)[1]);
                boolean isOp = player.hasPermission("deepstorageplus.adminopen");
                if (isOwner || isOp) {
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
            return;
        }

        if (event.getView().getTitle().equals(DeepStoragePlus.DSUname) || StorageUtils.isDSU(inv)) {
            debug("DSU click: slot=" + event.getSlot()
                    + ", click=" + event.getClick()
                    + ", shift=" + event.isShiftClick()
                    + ", topInv=" + (event.getClickedInventory() == inv)
                    + ", item=" + (item != null ? item.getType() : "null")
                    + ", cursor=" + (cursor != null ? cursor.getType() : "null"));

            if (event.getClickedInventory() != player.getInventory()) {
                if (event.getSlot() % 9 == 8) {
                    if (event.getSlot() != 53) {
                        if (cursor != null && cursor.getType() != Material.AIR) {
                            if (item != null && item.getType() == Material.WHITE_STAINED_GLASS_PANE) {
                                event.setCancelled(true);
                                if (cursor.hasItemMeta() && ItemList.isGroup(cursor, ItemList.GROUP_STORAGE_CONTAINER)) {
                                    inv.setItem(event.getSlot(), cursor);
                                    player.setItemOnCursor(new ItemStack(Material.AIR));
                                    main.dsuupdatemanager.updateItemsExact(inv);
                                }
                            } else {
                                if (!ItemList.isGroup(cursor, ItemList.GROUP_STORAGE_CONTAINER) || event.isShiftClick()) {
                                    event.setCancelled(true);
                                }
                            }
                        } else {
                            event.setCancelled(true);
                            if (item != null && item.getType() != Material.WHITE_STAINED_GLASS_PANE) {
                                player.setItemOnCursor(item.clone());
                                inv.setItem(event.getSlot(), DSUManager.getEmptyBlock());
                                main.dsuupdatemanager.updateItemsExact(inv);
                            }
                        }
                    } else {
                        event.setCancelled(true);
                        if (cursor == null || cursor.getType() == Material.AIR) {
                            if (item != null && item.hasItemMeta() && ItemList.isItem(item, ItemList.KEY_IO_SETTINGS)) {
                                player.openInventory(createIOInventory(inv));
                            }
                        }
                    }
                } else if (event.getSlot() % 9 == 7) {
                    event.setCancelled(true);
                } else {
                    event.setCancelled(true);
                    if (cursor != null && cursor.getType() != Material.AIR) {
                        ItemStack cursorClone = cursor.clone();
                        boolean isvaliditem = DSUManager.addToDSU(cursorClone, event.getClickedInventory(), player);
                        player.setItemOnCursor(cursorClone);
                        main.dsuupdatemanager.updateItemsExact(inv);
                        if (cursorClone.getAmount() > 0 && isvaliditem) {
                            player.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + LanguageManager.getValue("containersfull"));
                        }
                    } else if ((cursor == null || cursor.getType() == Material.AIR) && item != null) {
                        if (event.getClick() != ClickType.DOUBLE_CLICK) {
                            ItemStack takeTemplate = buildTakeTemplate(item);
                            if (takeTemplate == null || takeTemplate.getType() == Material.AIR) {
                                return;
                            }

                            int requestedAmount = event.getClick() == ClickType.RIGHT
                                    ? 1
                                    : takeTemplate.getMaxStackSize();

                            if (event.isShiftClick()) {
                                int amtTaken = DSUManager.takeItems(takeTemplate, inv, requestedAmount);
                                if (amtTaken > 0) {
                                    ItemStack toGive = buildTakeItem(takeTemplate, amtTaken, inv);
                                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(toGive);
                                    if (!leftover.isEmpty()) {
                                        for (ItemStack rest : leftover.values()) {
                                            DSUManager.addToDSUSilent(rest, inv);
                                        }
                                        player.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + LanguageManager.getValue("nomorespace"));
                                    }
                                }
                            } else {
                                int amtTaken = DSUManager.takeItems(takeTemplate, inv, requestedAmount);
                                if (amtTaken > 0) {
                                    player.setItemOnCursor(buildTakeItem(takeTemplate, amtTaken, inv));
                                }
                            }
                            main.dsuupdatemanager.updateItemsExact(inv);
                            player.updateInventory();
                        }
                    }
                }
            } else {
                if (event.isShiftClick()) {
                    if (item != null && item.getType() != Material.AIR) {
                        event.setCancelled(true);
                        ItemStack clone = item.clone();
                        main.dsumanager.addItemToDSU(clone, player);
                        if (clone.getAmount() > 0) {
                            event.getClickedInventory().setItem(event.getSlot(), clone);
                        } else {
                            event.getClickedInventory().setItem(event.getSlot(), null);
                        }
                    }
                } else if (event.getClick() == ClickType.DOUBLE_CLICK) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        if (event.getView().getTitle().equals(DeepStoragePlus.sortername) || StorageUtils.isSorter(inv)) {
            if (event.getClickedInventory() != player.getInventory()) {
                int slot = event.getSlot();
                if (slot > 26 || (slot > 17 && slot < 27)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (event.isShiftClick() && item != null && item.getType() != Material.AIR) {
                main.sorterUpdateManager.sortItems(inv, DeepStoragePlus.minTimeSinceLastSortPlayer);
            }
            return;
        }
    }

    @EventHandler
    private void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(DeepStoragePlus.DSUname) || StorageUtils.isDSU(event.getInventory())) {
            if (event.getWhoClicked() instanceof Player) {
                for (Integer slot : event.getRawSlots()) {
                    if (slot % 9 == 8 || slot % 9 == 7 || slot == 53) {
                        event.setCancelled(true);
                        break;
                    }
                }
            }
        }
    }

    @EventHandler
    private void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            if (event.getView().getTitle().equals(ChatColor.BLUE + "" + ChatColor.BOLD + LanguageManager.getValue("dsuioconfig"))) {
                Container DSUContainer = DeepStoragePlus.openDSU.get(player.getUniqueId());
                Inventory DSU = DSUContainer != null ? DSUContainer.getInventory() : DeepStoragePlus.stashedDSU.get(player.getUniqueId());
                if (DSU == null) return;
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
                    DSUManager.setIoTemplate(i, DSUManager.IO_INPUT_TEMPLATE_TAG,
                            input != null && input.getType() != Material.WHITE_STAINED_GLASS_PANE && input.getType() != Material.AIR ? input.clone() : null);
                    DSUManager.setIoTemplate(i, DSUManager.IO_OUTPUT_TEMPLATE_TAG,
                            output != null && output.getType() != Material.WHITE_STAINED_GLASS_PANE && output.getType() != Material.AIR ? output.clone() : null);
                }
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

    // NOTE: InventoryMoveItemEvent for DSU hoppers is handled exclusively
    // by IOListener. No handler here to avoid conflicts.

    private boolean isDebug() {
        return main.getConfig().getBoolean(DEBUG_KEY, false);
    }

    private void debug(String message) {
        if (isDebug()) {
            main.getLogger().info("[DSU DEBUG] " + message);
        }
    }
}
