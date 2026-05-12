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

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onHopperMove(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        Inventory dest   = event.getDestination();

        boolean dsuIsSource = source.getSize() == 54 && StorageUtils.isDSU(source);
        boolean dsuIsDest   = dest.getSize()   == 54 && StorageUtils.isDSU(dest);

        if (!dsuIsSource && !dsuIsDest) return;

        ItemStack moving = event.getItem();

        // Never move plugin/meta items in either direction
        if (!hasNoMeta(moving)) {
            event.setCancelled(true);
            return;
        }

        if (dsuIsDest) {
            handleInput(event, source, dest, moving);
        } else {
            handleOutput(event, source, dest);
        }
    }

    // -----------------------------------------------------------------------
    // INPUT: hopper -> DSU
    //
    // Bukkit fires InventoryMoveItemEvent with the item it wants to move.
    // We let vanilla believe the transfer happened (don't cancel) but redirect
    // the item into DSU storage instead of a real inventory slot.
    //
    // Strategy:
    //  1. Try to store 1x the item into DSU containers.
    //  2. If successful: let vanilla remove it from the hopper by NOT cancelling,
    //     but we redirect where it goes by cancelling the event and manually
    //     removing from source — this gives vanilla its cooldown tick so it
    //     won't fire again immediately.
    //  3. If DSU is full: cancel so hopper keeps its cooldown (stops retry storm).
    // -----------------------------------------------------------------------
    private void handleInput(InventoryMoveItemEvent event, Inventory hopper, Inventory dsu, ItemStack moving) {
        // Clone with amount=1 (hopper moves 1 per tick)
        ItemStack toStore = moving.clone();
        toStore.setAmount(1);

        boolean stored = DSUManager.addToDSUSilent(toStore, dsu);

        if (!stored) {
            // DSU full — cancel so hopper gets its transfer cooldown
            // and doesn't spam the event
            event.setCancelled(true);
            return;
        }

        // Successfully stored: cancel vanilla (so item doesn't go into a real slot)
        // and manually remove 1 from the hopper source slot
        event.setCancelled(true);
        removeOneFromSource(hopper, moving);
        main.dsuupdatemanager.updateItemsExact(dsu);
    }

    /**
     * Removes exactly 1 of the matching item from the hopper.
     * Matches by type + amount-independent equality (isSimilar),
     * but hopper items are plain vanilla so isSimilar is safe here.
     */
    private void removeOneFromSource(Inventory hopper, ItemStack template) {
        for (int i = 0; i < hopper.getSize(); i++) {
            ItemStack slot = hopper.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) continue;
            // Match by material only for plain items; isSimilar for items with meta
            boolean matches = slot.getType() == template.getType() &&
                    ((slot.getItemMeta() == null && template.getItemMeta() == null) ||
                            slot.isSimilar(template));
            if (!matches) continue;

            if (slot.getAmount() <= 1) {
                hopper.setItem(i, null);
            } else {
                slot.setAmount(slot.getAmount() - 1);
                hopper.setItem(i, slot);
            }
            return;
        }
    }

    // -----------------------------------------------------------------------
    // OUTPUT: DSU -> hopper
    // Cancel vanilla (it would pull display items with DSU lore).
    // Take 1 from DSU via DSUManager and give a clean vanilla item to hopper.
    // -----------------------------------------------------------------------
    private void handleOutput(InventoryMoveItemEvent event, Inventory dsu, Inventory hopper) {
        event.setCancelled(true);

        for (ItemStack template : DSUManager.getTotalTemplates(dsu)) {
            if (template == null || template.getType() == Material.AIR) continue;

            ItemStack give = template.clone();
            give.setAmount(1);

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
}
