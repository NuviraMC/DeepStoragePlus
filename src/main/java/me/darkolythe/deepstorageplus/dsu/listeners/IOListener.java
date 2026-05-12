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

    // DSU inventory slots that belong to the plugin and must never be touched
    // Layout: wall=7,16,25,34,43,52 | container=8,17,26,35,44 | io=53
    private static final Set<Integer> PLUGIN_SLOTS = Set.of(7, 8, 16, 17, 25, 26, 34, 35, 43, 44, 52, 53);

    private final DeepStoragePlus main;
    // Prevents scheduling multiple rescue tasks for the same DSU location
    private final Set<String> scheduledRescues = new HashSet<>();

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

        // Drain entire hopper into DSU right now
        drainHopperToDSU(hopper, dsu);

        // Vanilla sometimes writes items directly into the physical chest slots
        // without firing another event. Rescue those items 1 tick later.
        Location dsuLoc = dsu.getLocation();
        if (dsuLoc == null) return;
        String key = locKey(dsuLoc);
        if (scheduledRescues.contains(key)) return;
        scheduledRescues.add(key);

        main.getServer().getScheduler().runTaskLater(main, () -> {
            scheduledRescues.remove(key);
            if (dsuLoc.getWorld() == null) return;
            Block block = dsuLoc.getBlock();
            if (!(block.getState() instanceof Chest chest)) return;
            Inventory freshDsu = chest.getInventory();
            if (!StorageUtils.isDSU(freshDsu)) return;

            // Rescue any non-plugin items that Vanilla smuggled into the chest slots
            boolean anyRescued = false;
            for (int i = 0; i < freshDsu.getSize(); i++) {
                if (PLUGIN_SLOTS.contains(i)) continue;
                ItemStack slot = freshDsu.getItem(i);
                if (slot == null || slot.getType() == Material.AIR) continue;
                if (!hasNoMeta(slot)) continue;

                ItemStack toStore = slot.clone();
                DSUManager.addToDSUSilent(toStore, freshDsu);
                int stored = slot.getAmount() - toStore.getAmount();
                if (stored <= 0) continue;

                anyRescued = true;
                if (toStore.getAmount() <= 0) {
                    freshDsu.setItem(i, null);
                } else {
                    freshDsu.setItem(i, toStore);
                }
            }

            // Also drain the hopper again in case it refilled
            Location hopperLoc = hopper.getLocation();
            if (hopperLoc != null) {
                drainHopperToDSU(hopper, freshDsu);
            }

            if (anyRescued) {
                main.dsuupdatemanager.updateItemsExact(freshDsu);
            }
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
