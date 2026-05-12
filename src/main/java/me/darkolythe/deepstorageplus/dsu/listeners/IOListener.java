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

    // Slots that hold persistent DSU data. Wall items (7,16,25,34,43,52) are
    // GUI-only and are NOT physically stored in the chest when it is closed.
    private static final Set<Integer> PERSISTENT_DSU_SLOTS = Set.of(8, 17, 26, 35, 44, 53);

    private final DeepStoragePlus main;
    private final Logger log;
    private final Set<String> pendingAbsorb = new HashSet<>();

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
            handleInput(event, dest);
        } else {
            handleOutput(event, source, dest);
        }
    }

    private void handleInput(InventoryMoveItemEvent event, Inventory dsu) {
        // Do NOT cancel the event - cancelling stops the hopper cooldown reset
        // and the hopper never fires again, losing all remaining items.
        // Instead: let Vanilla put the item into the chest, then absorb it immediately.

        Location dsuLoc = dsu.getLocation();
        if (dsuLoc == null) return;
        String key = dsuLoc.getWorld().getName() + "|" + dsuLoc.getBlockX() + "|" + dsuLoc.getBlockY() + "|" + dsuLoc.getBlockZ();

        // Schedule absorb for next tick (after Vanilla wrote item into chest)
        // De-duplicate: only one absorb task per DSU per tick
        if (pendingAbsorb.contains(key)) return;
        pendingAbsorb.add(key);

        main.getServer().getScheduler().runTask(main, () -> {
            pendingAbsorb.remove(key);
            if (dsuLoc.getWorld() == null) return;
            Block block = dsuLoc.getBlock();
            if (!(block.getState() instanceof Chest chest)) return;
            Inventory freshDsu = chest.getInventory();
            if (!StorageUtils.isDSU(freshDsu)) return;

            absorb(freshDsu);
        });
    }

    /**
     * Absorbs all items from non-persistent DSU slots into DSU storage.
     * Items are first merged by type so that multiple stacks of the same
     * material (placed into different slots by Vanilla) are stored as a
     * single DSU entry rather than creating duplicate template entries.
     */
    private void absorb(Inventory dsu) {
        // Pass 1: collect all stray items by slot, merge identical types
        // into one representative ItemStack per type so addToDSUSilent only
        // sees each type once and cannot create split template entries.
        Map<Integer, ItemStack> slotMap = new HashMap<>();   // slot -> original slot ref for cleanup
        Map<String, ItemStack>  merged  = new HashMap<>();   // typeKey -> merged stack to store

        for (int i = 0; i < dsu.getSize(); i++) {
            if (PERSISTENT_DSU_SLOTS.contains(i)) continue;
            ItemStack slot = dsu.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) continue;
            if (!hasNoMeta(slot)) continue;

            slotMap.put(i, slot);
            String key = slot.getType().name();
            if (merged.containsKey(key)) {
                merged.get(key).setAmount(merged.get(key).getAmount() + slot.getAmount());
            } else {
                merged.put(key, slot.clone());
            }
        }

        if (merged.isEmpty()) return;

        // Pass 2: store merged stacks into DSU containers
        boolean anyStored = false;
        for (ItemStack toStore : merged.values()) {
            int before = toStore.getAmount();
            DSUManager.addToDSUSilent(toStore, dsu);
            int stored = before - toStore.getAmount();
            log.info("[DSU-ABSORB] type=" + toStore.getType() + " attempted=" + before + " stored=" + stored);
            if (stored > 0) anyStored = true;
        }

        // Pass 3: remove exactly what was stored from the physical slots
        for (Map.Entry<Integer, ItemStack> entry : slotMap.entrySet()) {
            int slot = entry.getKey();
            ItemStack original = entry.getValue();
            String key = original.getType().name();
            ItemStack mergedStack = merged.get(key);
            if (mergedStack == null) continue;

            // mergedStack.getAmount() is whatever was NOT stored (remainder).
            // Distribute remainder back proportionally: clear all slots first,
            // then put the leftover into the first slot of that type.
            dsu.setItem(slot, null);
        }

        // Put any unstored remainder back into the first available free slot
        for (ItemStack mergedStack : merged.values()) {
            if (mergedStack.getAmount() <= 0) continue;
            // DSU is full for this type — put remainder back
            HashMap<Integer, ItemStack> leftover = dsu.addItem(mergedStack);
            if (!leftover.isEmpty()) {
                // Chest is completely full — drop or leave (should be extremely rare)
                log.warning("[DSU-ABSORB] Could not return remainder for " + mergedStack.getType() + " x" + mergedStack.getAmount());
            }
        }

        if (anyStored) {
            main.dsuupdatemanager.updateItemsExact(dsu);
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
}
