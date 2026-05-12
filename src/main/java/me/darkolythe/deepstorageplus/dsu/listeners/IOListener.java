package me.darkolythe.deepstorageplus.dsu.listeners;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import me.darkolythe.deepstorageplus.dsu.StorageUtils;
import me.darkolythe.deepstorageplus.dsu.managers.DSUManager;
import me.darkolythe.deepstorageplus.utils.ItemList;
import me.darkolythe.deepstorageplus.utils.LanguageManager;
import org.bukkit.ChatColor;
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

import static me.darkolythe.deepstorageplus.dsu.StorageUtils.hasNoMeta;
import static me.darkolythe.deepstorageplus.dsu.managers.SettingsManager.addSpeedUpgrade;

public class IOListener implements Listener {

    private final DeepStoragePlus main;

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

    /**
     * Hopper IO for DSU.
     *
     * INPUT  (hopper -> DSU):
     *   Always cancel vanilla. Take the item from the specific hopper slot,
     *   add it to the DSU storage containers via DSUManager, then update display.
     *
     * OUTPUT (DSU -> hopper):
     *   Always cancel vanilla (vanilla would pull display-items with DSU lore).
     *   Instead, find the first item stored in the DSU containers, take 1 from
     *   DSUManager, give a clean vanilla ItemStack to the hopper.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onHopperMove(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        Inventory dest   = event.getDestination();

        boolean dsuIsSource = source.getSize() == 54 && StorageUtils.isDSU(source);
        boolean dsuIsDest   = dest.getSize()   == 54 && StorageUtils.isDSU(dest);

        if (!dsuIsSource && !dsuIsDest) return;

        // Always cancel — we handle everything ourselves
        event.setCancelled(true);

        if (dsuIsDest) {
            handleInput(source, dest);
        } else {
            handleOutput(source, dest);
        }
    }

    // -----------------------------------------------------------------------
    // INPUT: hopper -> DSU
    // Take 1 item from the first non-empty hopper slot, add to DSU containers.
    // -----------------------------------------------------------------------
    private void handleInput(Inventory hopper, Inventory dsu) {
        for (int i = 0; i < hopper.getSize(); i++) {
            ItemStack slot = hopper.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) continue;
            // Never move plugin items
            if (!hasNoMeta(slot)) continue;

            // Take exactly 1 (vanilla hopper moves 1 per tick)
            ItemStack toAdd = slot.clone();
            toAdd.setAmount(1);

            // Try to add to DSU
            boolean stored = DSUManager.addToDSUSilent(toAdd, dsu);
            if (!stored) return; // No space in DSU

            // Remove 1 from hopper slot
            if (slot.getAmount() <= 1) {
                hopper.setItem(i, null);
            } else {
                slot.setAmount(slot.getAmount() - 1);
                hopper.setItem(i, slot);
            }

            main.dsuupdatemanager.updateItemsExact(dsu);
            return; // One item per hopper tick
        }
    }

    // -----------------------------------------------------------------------
    // OUTPUT: DSU -> hopper
    // Find the first item stored in DSU containers, give a clean vanilla
    // ItemStack (amount=1, no DSU lore) to the hopper.
    // -----------------------------------------------------------------------
    private void handleOutput(Inventory dsu, Inventory hopper) {
        // Find the first stored item template in any container
        for (ItemStack template : DSUManager.getTotalTemplates(dsu)) {
            if (template == null || template.getType() == Material.AIR) continue;

            // Build a clean vanilla item (template from DSUManager has no DSU lore)
            ItemStack give = template.clone();
            give.setAmount(1);

            // Check if hopper can accept it
            if (!canAddToInventory(hopper, give)) continue;

            // Take 1 from DSU storage
            int taken = DSUManager.takeItems(template, dsu, 1);
            if (taken <= 0) continue;

            // Give the clean item to the hopper
            hopper.addItem(give);
            main.dsuupdatemanager.updateItemsExact(dsu);
            return; // One item per hopper tick
        }
    }

    /** Returns true if the inventory has space for at least 1 of the given item. */
    private boolean canAddToInventory(Inventory inv, ItemStack item) {
        for (ItemStack slot : inv.getContents()) {
            if (slot == null || slot.getType() == Material.AIR) return true;
            if (slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize()) return true;
        }
        return false;
    }
}
