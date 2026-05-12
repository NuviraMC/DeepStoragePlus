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
import java.util.Map;
import java.util.logging.Logger;

import static me.darkolythe.deepstorageplus.dsu.StorageUtils.hasNoMeta;
import static me.darkolythe.deepstorageplus.dsu.managers.SettingsManager.addSpeedUpgrade;

public class IOListener implements Listener {

    private final DeepStoragePlus main;
    private final Logger log;
    private final Map<String, Long> lastInputTick = new HashMap<>();

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
        String key = hopperLoc != null ? locKey(hopperLoc) : "unknown";
        long currentTick = main.getServer().getCurrentTick();

        Long last = lastInputTick.get(key);
        log.info("[HOPPER-IN DEBUG] tick=" + currentTick
                + " lastTick=" + last
                + " eventItem=" + event.getItem().getType()
                + " eventItemAmt=" + event.getItem().getAmount());

        if (last != null && last == currentTick) {
            log.info("[HOPPER-IN DEBUG] -> SKIPPED (duplicate tick)");
            return;
        }
        lastInputTick.put(key, currentTick);
        if (lastInputTick.size() > 512) {
            lastInputTick.entrySet().removeIf(e -> e.getValue() < currentTick - 40);
        }

        // Log all hopper slots
        int hopperTotal = 0;
        for (int i = 0; i < hopper.getSize(); i++) {
            ItemStack s = hopper.getItem(i);
            int amt = (s != null && s.getType() != Material.AIR) ? s.getAmount() : 0;
            hopperTotal += amt;
            log.info("[HOPPER-IN DEBUG] hopperSlot[" + i + "] = "
                    + (s == null ? "null" : s.getType() + "x" + s.getAmount())
                    + " hasNoMeta=" + (s != null && hasNoMeta(s)));
        }
        log.info("[HOPPER-IN DEBUG] hopperTotal=" + hopperTotal);

        for (int i = 0; i < hopper.getSize(); i++) {
            ItemStack slot = hopper.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) continue;
            if (!hasNoMeta(slot)) continue;

            ItemStack toStore = new ItemStack(slot.getType(), 1);
            int before = toStore.getAmount();
            DSUManager.addToDSUSilent(toStore, dsu);
            int stored = before - toStore.getAmount();

            // Count total in DSU after store
            int dsuTotal = DSUManager.getTotalItemAmount(dsu, new ItemStack(slot.getType(), 1));

            log.info("[HOPPER-IN DEBUG] -> slot=" + i
                    + " type=" + slot.getType()
                    + " hopperSlotAmt=" + slot.getAmount()
                    + " stored=" + stored
                    + " DSU_total_now=" + dsuTotal);

            if (stored <= 0) {
                log.info("[HOPPER-IN DEBUG] -> DSU full/no container, aborting");
                return;
            }

            int amtBefore = slot.getAmount();
            if (slot.getAmount() <= 1) {
                hopper.setItem(i, null);
            } else {
                slot.setAmount(slot.getAmount() - 1);
                hopper.setItem(i, slot);
            }
            log.info("[HOPPER-IN DEBUG] -> removed from hopperSlot[" + i + "] was=" + amtBefore
                    + " now=" + (hopper.getItem(i) == null ? 0 : hopper.getItem(i).getAmount()));

            main.dsuupdatemanager.updateItemsExact(dsu);
            return;
        }

        log.info("[HOPPER-IN DEBUG] -> no valid slot found");
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
