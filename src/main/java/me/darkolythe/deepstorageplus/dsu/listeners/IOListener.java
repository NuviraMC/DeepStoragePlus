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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static me.darkolythe.deepstorageplus.dsu.StorageUtils.hasNoMeta;
import static me.darkolythe.deepstorageplus.dsu.managers.SettingsManager.addSpeedUpgrade;

public class IOListener implements Listener {

    private static final Set<Integer> PLUGIN_SLOTS = Set.of(7, 8, 16, 17, 25, 26, 34, 35, 43, 44, 52, 53);

    private final DeepStoragePlus main;
    private final Logger log;
    private final Map<String, Long> lastInputTick = new HashMap<>();
    private final Set<String> scheduledRescues = new HashSet<>();

    public IOListener(DeepStoragePlus plugin) {
        this.main = plugin;
        this.log = plugin.getLogger();
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

        Location hopperLoc = hopper.getLocation();
        String hopperKey = hopperLoc != null ? locKey(hopperLoc) : "unknown";
        long currentTick = main.getServer().getCurrentTick();

        Long last = lastInputTick.get(hopperKey);
        log.info("[HOPPER-IN] tick=" + currentTick + " lastTick=" + last
                + " item=" + event.getItem().getType() + "x" + event.getItem().getAmount());

        if (last != null && last == currentTick) {
            log.info("[HOPPER-IN] -> SKIPPED (duplicate tick)");
            return;
        }
        lastInputTick.put(hopperKey, currentTick);
        if (lastInputTick.size() > 512) {
            lastInputTick.entrySet().removeIf(e -> e.getValue() < currentTick - 40);
        }

        // Drain entire hopper into DSU right now
        for (int i = 0; i < hopper.getSize(); i++) {
            ItemStack slot = hopper.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) continue;
            if (!hasNoMeta(slot)) continue;

            ItemStack toStore = new ItemStack(slot.getType(), slot.getAmount());
            DSUManager.addToDSUSilent(toStore, dsu);
            int stored = slot.getAmount() - toStore.getAmount();
            log.info("[HOPPER-IN] slot=" + i + " type=" + slot.getType()
                    + " amt=" + slot.getAmount() + " stored=" + stored);

            if (stored <= 0) continue;

            if (toStore.getAmount() <= 0) {
                hopper.setItem(i, null);
            } else {
                hopper.setItem(i, toStore);
            }
        }
        main.dsuupdatemanager.updateItemsExact(dsu);

        // Schedule rescue task: catch items Vanilla may write directly into chest slots
        Location dsuLoc = dsu.getLocation();
        if (dsuLoc == null) return;
        String rescueKey = locKey(dsuLoc);
        if (scheduledRescues.contains(rescueKey)) return;
        scheduledRescues.add(rescueKey);

        main.getServer().getScheduler().runTaskLater(main, () -> {
            scheduledRescues.remove(rescueKey);
            if (dsuLoc.getWorld() == null) return;
            Block block = dsuLoc.getBlock();
            if (!(block.getState() instanceof Chest chest)) return;
            Inventory freshDsu = chest.getInventory();

            // ---- DEBUG: dump every non-empty slot ----
            log.info("[RESCUE] scanning DSU @ " + rescueKey);
            for (int i = 0; i < freshDsu.getSize(); i++) {
                ItemStack s = freshDsu.getItem(i);
                if (s != null && s.getType() != Material.AIR) {
                    log.info("  slot[" + i + "]=" + s.getType() + "x" + s.getAmount()
                            + " isPlugin=" + ItemList.isPluginItem(s)
                            + " inPluginSlotSet=" + PLUGIN_SLOTS.contains(i));
                }
            }
            // ------------------------------------------

            if (!StorageUtils.isDSU(freshDsu)) {
                log.info("[RESCUE] -> not a DSU anymore, skipping");
                return;
            }

            boolean anyRescued = false;
            for (int i = 0; i < freshDsu.getSize(); i++) {
                if (PLUGIN_SLOTS.contains(i)) continue;
                ItemStack slot = freshDsu.getItem(i);
                if (slot == null || slot.getType() == Material.AIR) continue;
                if (!hasNoMeta(slot)) continue;

                ItemStack toStore = slot.clone();
                DSUManager.addToDSUSilent(toStore, freshDsu);
                int stored = slot.getAmount() - toStore.getAmount();
                log.info("[RESCUE] rescued slot[" + i + "] type=" + slot.getType()
                        + " amt=" + slot.getAmount() + " stored=" + stored);

                if (stored <= 0) continue;
                anyRescued = true;
                if (toStore.getAmount() <= 0) {
                    freshDsu.setItem(i, null);
                } else {
                    freshDsu.setItem(i, toStore);
                }
            }

            if (anyRescued) {
                main.dsuupdatemanager.updateItemsExact(freshDsu);
            } else {
                log.info("[RESCUE] nothing to rescue");
            }
        }, 1L);
    }

    private void handleOutput(InventoryMoveItemEvent event, Inventory dsu, Inventory hopper) {
        event.setCancelled(true);

        for (ItemStack template : DSUManager.getTotalTemplates(dsu)) {
            if (template == null || template.getType() == Material.AIR) continue;

            ItemStack give = new ItemStack(template.getType(), 1);
            if (!canAddToInventory(hopper, give)) continue;

            int taken = DSUManager.takeItems(template, dsu, 1);
            if (taken <= 0) continue;

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
