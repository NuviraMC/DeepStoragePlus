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

import java.util.Set;
import java.util.logging.Logger;

import static me.darkolythe.deepstorageplus.dsu.StorageUtils.hasNoMeta;
import static me.darkolythe.deepstorageplus.dsu.managers.SettingsManager.addSpeedUpgrade;

public class IOListener implements Listener {

    // Slots that hold persistent DSU data (containers + IO item).
    // Wall items (7,16,25,34,43,52) are GUI-only and NOT stored in the physical chest.
    private static final Set<Integer> PERSISTENT_DSU_SLOTS = Set.of(8, 17, 26, 35, 44, 53);

    private final DeepStoragePlus main;
    private final Logger log;

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
        // Cancel vanilla transfer — we handle it ourselves
        event.setCancelled(true);

        // The event item is the 1 item Vanilla *would* transfer.
        // Store it directly into the DSU.
        ItemStack toStore = event.getItem().clone();
        DSUManager.addToDSUSilent(toStore, dsu);
        int stored = event.getItem().getAmount() - toStore.getAmount();

        log.info("[HOPPER-IN] item=" + event.getItem().getType()
                + " amt=" + event.getItem().getAmount()
                + " stored=" + stored);

        if (stored <= 0) {
            // DSU full / no container for this item type
            return;
        }

        // Remove the transferred item from the source hopper
        ItemStack[] hopperContents = hopper.getContents();
        int remaining = stored;
        for (int i = 0; i < hopperContents.length && remaining > 0; i++) {
            ItemStack slot = hopperContents[i];
            if (slot == null || slot.getType() == Material.AIR) continue;
            if (!slot.isSimilar(event.getItem()) && slot.getType() != event.getItem().getType()) continue;

            int take = Math.min(slot.getAmount(), remaining);
            remaining -= take;
            slot.setAmount(slot.getAmount() - take);
            hopper.setItem(i, slot.getAmount() <= 0 ? null : slot);
        }

        // Synchronously clean up any stray items in non-DSU slots
        // (Vanilla may have already written to the chest before we could cancel)
        cleanStraySlots(dsu);

        main.dsuupdatemanager.updateItemsExact(dsu);
    }

    /**
     * Scans every chest slot that is NOT a persistent DSU slot and rescues
     * any items Vanilla may have written there before our cancel took effect.
     */
    private void cleanStraySlots(Inventory dsu) {
        for (int i = 0; i < dsu.getSize(); i++) {
            if (PERSISTENT_DSU_SLOTS.contains(i)) continue;
            ItemStack slot = dsu.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) continue;
            if (!hasNoMeta(slot)) continue;

            ItemStack toStore = slot.clone();
            DSUManager.addToDSUSilent(toStore, dsu);
            int stored = slot.getAmount() - toStore.getAmount();
            if (stored <= 0) continue;

            log.info("[STRAY-CLEAN] slot=" + i + " type=" + slot.getType()
                    + " amt=" + slot.getAmount() + " stored=" + stored);

            dsu.setItem(i, toStore.getAmount() <= 0 ? null : toStore);
        }
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
