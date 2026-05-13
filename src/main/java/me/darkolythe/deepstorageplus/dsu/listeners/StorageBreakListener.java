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

    /**
     * DSU storage-container slots inside the full 54-slot double-chest inventory.
     * Column 8, rows 0-4. Contents are PDC data on the container ItemStack.
     */
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
        // Snapshot NOW before breakStorage() removes the block.
        // The delayed task may run after the block is gone, making
        // chest.getInventory().getItem(slot) throw ArrayIndexOutOfBounds
        // because the backing array is only 27 slots (single chest) or gone.
        ItemStack[] snapshot = chest.getInventory().getContents().clone();
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () -> emptyChest(chest, snapshot), 1);
        breakStorage(chest);
    }

    /**
     * Empties a DSU chest using a pre-captured contents snapshot.
     *
     * Root cause of the ArrayIndexOutOfBoundsException (Index 35 out of bounds
     * for length 27): after the block is set to AIR the underlying container
     * is only a 27-slot single chest. Slots 26-44 no longer exist.
     * By snapshotting getContents() BEFORE breakStorage() we avoid this entirely.
     *
     * Additionally we guard containerSlot >= contents.length so the code is
     * safe even if called on a half-chest for any reason.
     *
     * Container slots store item data as PDC on the container ItemStack.
     * We drain them via a 54-slot synthetic inventory + DSUManager.takeItems()
     * and drop the real stored items. The container block itself is also dropped.
     */
    private void emptyChest(Container chest, ItemStack[] contents) {
        for (int containerSlot : CONTAINER_SLOTS) {
            if (containerSlot >= contents.length) continue; // half-chest guard

            ItemStack containerItem = contents[containerSlot];
            if (containerItem == null || containerItem.getType() == Material.AIR) continue;
            if (!DSUManager.isStorageContainer(containerItem)) continue;

            // 54-slot synthetic inventory so takeItems can address slot 8
            Inventory snap = Bukkit.createInventory(null, 54);
            snap.setItem(8, containerItem.clone());

            for (ItemStack template : DSUManager.getTotalTemplates(snap)) {
                if (template == null || template.getType() == Material.AIR) continue;
                int remaining = DSUManager.getTotalItemAmount(snap, template);
                while (remaining > 0) {
                    int batch = Math.min(remaining, template.getMaxStackSize());
                    int taken = DSUManager.takeItems(template, snap, batch);
                    if (taken <= 0) break;
                    ItemStack drop = template.clone();
                    drop.setAmount(taken);
                    chest.getWorld().dropItemNaturally(chest.getLocation(), drop);
                    remaining -= taken;
                }
            }

            chest.getWorld().dropItemNaturally(chest.getLocation(), containerItem.clone());
        }

        for (int i = 0; i < contents.length; i++) {
            if (CONTAINER_SLOTS.contains(i)) continue;
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;
            if (!ItemList.isPluginItem(item)) {
                chest.getWorld().dropItemNaturally(chest.getLocation(), item);
            }
        }
    }
}
