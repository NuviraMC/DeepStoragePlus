package me.darkolythe.deepstorageplus.dsu.listeners;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import me.darkolythe.deepstorageplus.dsu.StorageUtils;
import me.darkolythe.deepstorageplus.dsu.managers.DSUManager;
import me.darkolythe.deepstorageplus.utils.ItemList;
import me.darkolythe.deepstorageplus.utils.LanguageManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

import static me.darkolythe.deepstorageplus.dsu.StorageUtils.hasNoMeta;
import static me.darkolythe.deepstorageplus.dsu.managers.SettingsManager.addSpeedUpgrade;

public class IOListener implements Listener {

    private final DeepStoragePlus main;
    // Tracks hopper locations currently scheduled for a follow-up drain
    private final Set<String> scheduledDrains = new HashSet<>();

    public IOListener(DeepStoragePlus plugin) {
        this.main = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onDSUClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
            Player player = event.getPlayer();
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.CHEST) {
                if (!event.isCancelled()) {
                    Chest chest = (Chest) block.getState();
                    if (chest.getInventory().contains(DSUManager.getDSUWall())) {
                        ItemStack item = player.getInventory().getItemInMainHand();
                        if (ItemList.isItem(item, ItemList.KEY_SPEED_UPGRADE)) {
                            var inv = chest.getInventory();
                            ItemStack IOItem = inv.getItem(53);
                            ItemStack newIOItem = addSpeedUpgrade(IOItem);
                            if (newIOItem != null) {
                                inv.setItem(53, newIOItem);
                                player.sendMessage(DeepStoragePlus.prefix + ChatColor.GREEN + LanguageManager.getValue("upgradesuccess"));
                                item.setAmount(item.getAmount() - 1);
                            } else {
                                player.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + LanguageManager.getValue("upgradefail"));
                            }
                            if (inv.getLocation() != null) {
                                inv.getLocation().getBlock().getState().update();
                            }
                            event.setCancelled(true);
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onHopperMove(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        Inventory dest   = event.getDestination();

        boolean dsuIsSource = source.getSize() == 54 && StorageUtils.isDSU(source);
        boolean dsuIsDest   = dest.getSize()   == 54 && StorageUtils.isDSU(dest);

        if (!dsuIsSource && !dsuIsDest) return;

        if (!hasNoMeta(event.getItem())) {
            event.setCancelled(true);
            return;
        }

        if (dsuIsDest) {
            handleInput(event, source, dest);
        } else {
            handleOutput(event, source, dest);
        }
    }

    private void handleInput(InventoryMoveItemEvent event, Inventory hopper, Inventory dsu) {
        event.setCancelled(true);

        // Drain the entire hopper into the DSU immediately
        drainHopperToDSU(hopper, dsu);

        // Schedule a follow-up drain 1 tick later — Vanilla may move remaining
        // items into the chest without firing another event after setCancelled(true)
        Location hopperLoc = hopper.getLocation();
        if (hopperLoc == null) return;
        String key = locKey(hopperLoc);
        if (scheduledDrains.contains(key)) return;
        scheduledDrains.add(key);

        main.getServer().getScheduler().runTaskLater(main, () -> {
            scheduledDrains.remove(key);
            if (hopperLoc.getWorld() == null) return;
            Location dsuLoc = dsu.getLocation();
            if (dsuLoc == null) return;
            Block dsuBlock = dsuLoc.getBlock();
            if (!(dsuBlock.getState() instanceof Chest dsuChest)) return;
            Inventory freshDsu = dsuChest.getInventory();
            if (!StorageUtils.isDSU(freshDsu)) return;
            drainHopperToDSU(hopper, freshDsu);
        }, 1L);
    }

    private void drainHopperToDSU(Inventory hopper, Inventory dsu) {
        boolean anyStored = false;
        for (int i = 0; i < hopper.getSize(); i++) {
            ItemStack slot = hopper.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) continue;
            if (!hasNoMeta(slot)) continue;

            ItemStack toStore = slot.clone();
            DSUManager.addToDSUSilent(toStore, dsu);
            int stored = slot.getAmount() - toStore.getAmount();
            if (stored <= 0) continue;

            anyStored = true;
            if (toStore.getAmount() <= 0) {
                hopper.setItem(i, null);
            } else {
                hopper.setItem(i, toStore);
            }
        }
        if (anyStored) {
            main.dsuupdatemanager.updateItemsExact(dsu);
        }
    }

    private void handleOutput(InventoryMoveItemEvent event, Inventory dsu, Inventory hopper) {
        event.setCancelled(true);

        int transferAmount = event.getItem().getAmount();

        for (ItemStack template : DSUManager.getTotalTemplates(dsu)) {
            if (template == null || template.getType() == Material.AIR) continue;

            ItemStack give = new ItemStack(template.getType(), transferAmount);
            if (!canAddToInventory(hopper, give)) continue;

            int taken = DSUManager.takeItems(template, dsu, transferAmount);
            if (taken <= 0) continue;

            give.setAmount(taken);
            hopper.addItem(give);
            main.dsuupdatemanager.updateItemsExact(dsu);
            return;
        }
    }

    private boolean canAddToInventory(Inventory inv, ItemStack item) {
        for (ItemStack slot : inv.getContents()) {
            if (slot == null || slot.getType() == Material.AIR) return true;
            if (slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize()) return true;
        }
        return false;
    }

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + "|" + loc.getBlockX() + "|" + loc.getBlockY() + "|" + loc.getBlockZ();
    }
}
