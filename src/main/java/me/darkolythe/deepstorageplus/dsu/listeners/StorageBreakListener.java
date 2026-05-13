package me.darkolythe.deepstorageplus.dsu.listeners;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import me.darkolythe.deepstorageplus.dsu.managers.DSUManager;
import me.darkolythe.deepstorageplus.dsu.managers.SorterManager;
import me.darkolythe.deepstorageplus.utils.ItemList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

import static me.darkolythe.deepstorageplus.dsu.managers.SettingsManager.getLocked;
import static me.darkolythe.deepstorageplus.dsu.managers.SettingsManager.getLockedUsers;

public class StorageBreakListener implements Listener {

    /** DSU storage-container slots (column 8, rows 0-4). */
    private static final Set<Integer> CONTAINER_SLOTS = Set.of(8, 17, 26, 35, 44);

    DeepStoragePlus main;

    public StorageBreakListener(DeepStoragePlus plugin) {
        main = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onStorageBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (event.isCancelled()) return;

        if (!(event.getBlock().getState() instanceof Container chest)) return;

        if (!chest.getInventory().contains(DSUManager.getDSUWall())
                && !chest.getInventory().contains(SorterManager.getSorterWall())) {
            return;
        }

        ItemStack lock = chest.getInventory().getItem(53);
        boolean isOp = player.hasPermission("deepstorageplus.adminopen");
        boolean canOpen = getLocked(lock, player);

        if (!canOpen && !isOp && getLockedUsers(lock).size() != 0) {
            event.setCancelled(true);
            return;
        }

        DoubleChest doublechest = (DoubleChest) chest.getInventory().getHolder();
        event.setCancelled(true);
        event.setDropItems(false);

        Container chestLeft = (Container) doublechest.getLeftSide();
        Container chestRight = (Container) doublechest.getRightSide();

        removeItems(chestLeft);
        removeItems(chestRight);
    }

    private void breakStorage(Container chest) {
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () ->
                chest.getWorld().getBlockAt(chest.getLocation()).setType(Material.AIR), 1);
        chest.getWorld().dropItemNaturally(chest.getLocation(), new ItemStack(Material.CHEST, 1));
    }

    private void removeItems(Container chest) {
        chest.setCustomName(null);
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () -> emptyChest(chest), 1);
        breakStorage(chest);
    }

    /**
     * Empties a DSU chest on break.
     *
     * Container slots (8, 17, 26, 35, 44) hold DSU storage container ItemStacks
     * whose ACTUAL contents are stored as PDC data on the item — NOT as separate
     * inventory items. We must drain them via DSUManager and drop the real stored
     * items, then drop the physical container block itself.
     *
     * All other plugin-internal items (walls, IO-settings, empty-block
     * placeholders) are silently removed. Accidental user items are dropped.
     */
    private void emptyChest(Container chest) {
        Inventory inv = chest.getInventory();

        for (int containerSlot : CONTAINER_SLOTS) {
            ItemStack containerItem = inv.getItem(containerSlot);
            if (containerItem == null || containerItem.getType() == Material.AIR) continue;
            if (!DSUManager.isStorageContainer(containerItem)) continue;

            // Build a minimal snapshot inventory so takeItems can address slot 8
            Inventory snapshot = Bukkit.createInventory(null, 54);
            snapshot.setItem(8, containerItem.clone());

            for (ItemStack template : DSUManager.getTotalTemplates(snapshot)) {
                if (template == null || template.getType() == Material.AIR) continue;
                int remaining = DSUManager.getTotalItemAmount(snapshot, template);
                while (remaining > 0) {
                    int batch = Math.min(remaining, template.getMaxStackSize());
                    int taken = DSUManager.takeItems(template, snapshot, batch);
                    if (taken <= 0) break;
                    ItemStack drop = template.clone();
                    drop.setAmount(taken);
                    chest.getWorld().dropItemNaturally(chest.getLocation(), drop);
                    remaining -= taken;
                }
            }

            // Drop the physical storage container block
            chest.getWorld().dropItemNaturally(chest.getLocation(), containerItem.clone());
            inv.setItem(containerSlot, null);
        }

        // Clean up remaining slots
        for (int i = 0; i < inv.getSize(); i++) {
            if (CONTAINER_SLOTS.contains(i)) continue;
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (ItemList.isPluginItem(item)) {
                inv.setItem(i, null);
            } else {
                chest.getWorld().dropItemNaturally(chest.getLocation(), item);
                inv.setItem(i, null);
            }
        }
    }
}
