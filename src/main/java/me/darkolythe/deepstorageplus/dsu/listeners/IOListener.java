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
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static me.darkolythe.deepstorageplus.dsu.StorageUtils.hasNoMeta;
import static me.darkolythe.deepstorageplus.dsu.managers.SettingsManager.addSpeedUpgrade;

public class IOListener implements Listener {

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
        Location dsuLoc = dsu.getLocation();
        if (dsuLoc == null) return;
        String key = dsuLoc.getWorld().getName() + "|" + dsuLoc.getBlockX() + "|" + dsuLoc.getBlockY() + "|" + dsuLoc.getBlockZ();

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

    private void absorb(Inventory dsu) {
        // Step 1: collect stray slots and merge by material
        List<Integer> straySlots = new ArrayList<>();
        Map<String, ItemStack> merged = new HashMap<>();

        for (int i = 0; i < dsu.getSize(); i++) {
            if (PERSISTENT_DSU_SLOTS.contains(i)) continue;
            ItemStack slot = dsu.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) continue;
            if (!hasNoMeta(slot)) continue;

            straySlots.add(i);
            String key = slot.getType().name();
            if (merged.containsKey(key)) {
                merged.get(key).setAmount(merged.get(key).getAmount() + slot.getAmount());
            } else {
                merged.put(key, slot.clone());
            }
        }

        if (straySlots.isEmpty()) return;

        // Step 2: clear stray slots before storing
        for (int i : straySlots) {
            dsu.setItem(i, null);
        }

        // Step 3: store merged stacks — one call per material
        boolean anyStored = false;
        for (ItemStack toStore : merged.values()) {
            int before = toStore.getAmount();
            DSUManager.addToDSUSilent(toStore, dsu);
            int stored = before - toStore.getAmount();
            log.info("[DSU-ABSORB] type=" + toStore.getType() + " attempted=" + before + " stored=" + stored);
            if (stored > 0) anyStored = true;
        }

        // Step 4: put remainders back into cleared slots
        int remainderSlot = 0;
        for (ItemStack remainder : merged.values()) {
            if (remainder.getAmount() <= 0) continue;
            while (remainderSlot < straySlots.size()) {
                int physSlot = straySlots.get(remainderSlot++);
                if (dsu.getItem(physSlot) == null) {
                    dsu.setItem(physSlot, remainder);
                    break;
                }
            }
        }

        if (!anyStored) return;

        // Step 5: update container lore
        main.dsuupdatemanager.updateItemsExact(dsu);

        // Step 6: force-refresh all players currently viewing this DSU
        // so the updated item counts are visible without requiring a click.
        for (HumanEntity viewer : new ArrayList<>(dsu.getViewers())) {
            if (viewer instanceof Player p) {
                p.updateInventory();
            }
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
            // Refresh viewers on output too
            for (HumanEntity viewer : new ArrayList<>(dsu.getViewers())) {
                if (viewer instanceof Player p) p.updateInventory();
            }
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
