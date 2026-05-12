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
     * Vanilla hopper system for DSU:
     * - Let Minecraft handle item movement normally.
     * - Only block movement into/from protected slots (col 7 & 8 = right two columns)
     *   and prevent plugin items (with custom meta) from being siphoned out.
     * - After a successful vanilla move into DSU, just refresh the display.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onHopperMove(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        Inventory dest   = event.getDestination();

        boolean dsuIsSource = source.getSize() == 54 && StorageUtils.isDSU(source);
        boolean dsuIsDest   = dest.getSize()   == 54 && StorageUtils.isDSU(dest);

        if (!dsuIsSource && !dsuIsDest) return;

        ItemStack item = event.getItem();

        // Never move plugin/meta items (storage containers, IO settings, etc.)
        if (!hasNoMeta(item)) {
            event.setCancelled(true);
            return;
        }

        // Block anything being pulled OUT of a DSU via hopper (Minecraft would
        // pull from any reachable slot including col 7/8 display slots).
        // We cancel output entirely — hopper output is not supported without IO config.
        if (dsuIsSource) {
            event.setCancelled(true);
            return;
        }

        // dsuIsDest: vanilla will push the item into the first available slot.
        // Vanilla only picks slots 0-53 in order, but our col 7 & 8 slots must
        // stay protected. Cancel and do it ourselves into only the safe slots.
        event.setCancelled(true);

        // Find the first non-protected slot that can accept this item
        for (int i = 0; i < dest.getSize(); i++) {
            // Skip col 7 and col 8 (display/container/settings columns)
            if (i % 9 == 7 || i % 9 == 8) continue;

            ItemStack slot = dest.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) {
                dest.setItem(i, item.clone());
                removeOneFrom(source, item);
                main.dsuupdatemanager.updateItemsExact(dest);
                return;
            }
            if (slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize()) {
                int space = slot.getMaxStackSize() - slot.getAmount();
                int toAdd = Math.min(space, item.getAmount());
                slot.setAmount(slot.getAmount() + toAdd);
                dest.setItem(i, slot);
                removeAmountFrom(source, item, toAdd);
                main.dsuupdatemanager.updateItemsExact(dest);
                return;
            }
        }
    }

    private void removeOneFrom(Inventory inv, ItemStack template) {
        removeAmountFrom(inv, template, 1);
    }

    private void removeAmountFrom(Inventory inv, ItemStack template, int amount) {
        int remaining = amount;
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot == null || !slot.isSimilar(template)) continue;
            int take = Math.min(remaining, slot.getAmount());
            slot.setAmount(slot.getAmount() - take);
            if (slot.getAmount() <= 0) inv.setItem(i, null);
            else inv.setItem(i, slot);
            remaining -= take;
        }
    }
}
