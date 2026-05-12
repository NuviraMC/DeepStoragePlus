package me.darkolythe.deepstorageplus.dsu.listeners;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import me.darkolythe.deepstorageplus.dsu.StorageUtils;
import me.darkolythe.deepstorageplus.dsu.managers.DSUManager;
import me.darkolythe.deepstorageplus.utils.ItemList;
import me.darkolythe.deepstorageplus.utils.LanguageManager;
import org.bukkit.Bukkit;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static me.darkolythe.deepstorageplus.dsu.StorageUtils.hasNoMeta;
import static me.darkolythe.deepstorageplus.dsu.StorageUtils.stringToMat;
import static me.darkolythe.deepstorageplus.dsu.managers.SettingsManager.addSpeedUpgrade;
import static me.darkolythe.deepstorageplus.dsu.managers.SettingsManager.getSpeedUpgrade;

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
    private void onHopperInput(InventoryMoveItemEvent event) {
        Inventory initial = event.getSource();
        Inventory dest   = event.getDestination();

        boolean dsuIsSource = initial.getSize() == 54 && StorageUtils.isDSU(initial);
        boolean dsuIsDest   = dest.getSize()    == 54 && StorageUtils.isDSU(dest);

        if (!dsuIsSource && !dsuIsDest) {
            // Sorter check
            Inventory inv54 = initial.getSize() == 54 ? initial : (dest.getSize() == 54 ? dest : null);
            if (inv54 != null && StorageUtils.isSorter(inv54)) {
                if (dsuIsDest) {
                    main.sorterUpdateManager.sortItems(dest, DeepStoragePlus.minTimeSinceLastSortHopper);
                } else {
                    event.setCancelled(true);
                }
            }
            return;
        }

        // Always cancel vanilla transfer — we handle everything ourselves
        event.setCancelled(true);

        Inventory dsuInv = dsuIsSource ? initial : dest;
        ItemStack IOSettings = dsuInv.getItem(53);

        if (IOSettings == null || !ItemList.isItem(IOSettings, ItemList.KEY_IO_SETTINGS)) {
            return; // No IO item → do nothing (vanilla already cancelled)
        }
        if (!hasNoMeta(event.getItem())) {
            return; // Never move plugin items via hopper
        }

        int amt = getSpeedUpgrade(IOSettings) + 1;

        if (dsuIsDest) {
            // ---- Hopper → DSU (INPUT) — synchronous to avoid race conditions ----
            ItemStack filter = getInput(IOSettings);
            hopperToDSU(initial, dest, filter, amt);
        } else {
            // ---- DSU → Hopper (OUTPUT) — delayed by 1 tick (Bukkit requirement) ----
            ItemStack output = getOutput(IOSettings);
            if (output != null && output.getType() != Material.AIR) {
                final ItemStack outputFinal = output;
                Bukkit.getScheduler().scheduleSyncDelayedTask(main,
                    () -> dsutoHopper(initial, dest, outputFinal, amt), 1L);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Hopper → DSU  (synchronous — called directly in the event handler)
    //
    // Why synchronous?
    // setCancelled(true) already stops the vanilla move. We can immediately
    // read + write the hopper slot in the same tick with zero race conditions.
    // Using a delayed task caused multiple parallel tasks to read the same
    // slot amount and double/triple-consume items.
    // -------------------------------------------------------------------------
    private void hopperToDSU(Inventory hopper, Inventory dsu, ItemStack filter, int amt) {
        for (int i = 0; i < hopper.getSize(); i++) {
            ItemStack slot = hopper.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) continue;
            if (filter != null && !filter.isSimilar(slot)) continue;
            if (!hasNoMeta(slot)) continue;

            int toMove = Math.min(amt, slot.getAmount());

            ItemStack moving = slot.clone();
            moving.setAmount(toMove);

            // addToDSUSilent reduces moving.getAmount() to what could NOT be stored
            DSUManager.addToDSUSilent(moving, dsu);
            int actuallyStored = toMove - moving.getAmount();

            if (actuallyStored <= 0) return; // DSU full or no matching container

            // Write the exact remainder back into the hopper slot atomically
            int newAmt = slot.getAmount() - actuallyStored;
            if (newAmt <= 0) {
                hopper.setItem(i, null);
            } else {
                ItemStack remainder = slot.clone();
                remainder.setAmount(newAmt);
                hopper.setItem(i, remainder);
            }

            main.dsuupdatemanager.updateItemsExact(dsu);
            return; // One slot per hopper tick — vanilla behaviour
        }
    }

    // -------------------------------------------------------------------------
    // DSU → Hopper (delayed by 1 tick — Bukkit requires inventory writes
    // to the destination to happen outside the event handler)
    // -------------------------------------------------------------------------
    private void dsutoHopper(Inventory dsu, Inventory hopper, ItemStack output, int amt) {
        int available = DSUManager.getTotalItemAmount(dsu, output);
        if (available <= 0) return;

        int wantToMove = Math.min(amt, available);
        ItemStack toGive = output.clone();
        toGive.setAmount(wantToMove);

        HashMap<Integer, ItemStack> overflow = hopper.addItem(toGive);
        int overflowAmt = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
        int actuallyMoved = wantToMove - overflowAmt;

        if (actuallyMoved <= 0) return; // hopper full

        DSUManager.takeItems(output, dsu, actuallyMoved);
        main.dsuupdatemanager.updateItemsExact(dsu);
    }

    // -------------------------------------------------------------------------
    // IO Settings helpers
    // -------------------------------------------------------------------------

    private static ItemStack getInput(ItemStack ioItem) {
        if (ioItem == null || !ioItem.hasItemMeta()) return null;
        ItemStack exact = DSUManager.getIoTemplate(ioItem, DSUManager.IO_INPUT_TEMPLATE_TAG);
        if (exact != null) return exact;
        List<String> lore = ioItem.getItemMeta().getLore();
        if (lore == null) return null;
        String line = findIOLine(lore, LanguageManager.getValue("input"), "input", "eingang");
        if (line == null) return null;
        String value = extractIOValue(line);
        if (isAllValue(value)) return null; // null = accept all
        Material mat = stringToMat(line, "");
        return mat == Material.AIR ? null : new ItemStack(mat);
    }

    private static ItemStack getOutput(ItemStack ioItem) {
        if (ioItem == null || !ioItem.hasItemMeta()) return null;
        ItemStack exact = DSUManager.getIoTemplate(ioItem, DSUManager.IO_OUTPUT_TEMPLATE_TAG);
        if (exact != null) return exact;
        List<String> lore = ioItem.getItemMeta().getLore();
        if (lore == null || lore.size() < 2) return null;
        String line = findIOLine(lore, LanguageManager.getValue("output"), "output", "ausgang");
        if (line == null) return null;
        String value = extractIOValue(line);
        if (isNoneValue(value)) return null; // null = output disabled
        Material mat = stringToMat(line, "");
        return mat == Material.AIR ? null : new ItemStack(mat);
    }

    private static String findIOLine(List<String> lore, String configuredKey, String... aliases) {
        String normKey = normalizeToken(configuredKey);
        for (String line : lore) {
            String normLine = normalizeToken(ChatColor.stripColor(line));
            if (normLine.isEmpty()) continue;
            if (!normKey.isEmpty() && normLine.startsWith(normKey + ":")) return line;
            for (String alias : aliases) {
                String normAlias = normalizeToken(alias);
                if (!normAlias.isEmpty() && normLine.startsWith(normAlias + ":")) return line;
            }
        }
        return null;
    }

    private static String extractIOValue(String line) {
        String s = ChatColor.stripColor(line);
        if (s == null || s.isEmpty()) return "";
        int idx = s.indexOf(':');
        return (idx < 0 || idx + 1 >= s.length()) ? s.trim() : s.substring(idx + 1).trim();
    }

    private static boolean isAllValue(String v) {
        String n = normalizeToken(v), c = normalizeToken(LanguageManager.getValue("all"));
        return n.equals("all") || n.equals("alle") || (!c.isEmpty() && n.equals(c));
    }

    private static boolean isNoneValue(String v) {
        String n = normalizeToken(v), c = normalizeToken(LanguageManager.getValue("none"));
        return n.equals("none") || n.equals("keine") || (!c.isEmpty() && n.equals(c));
    }

    private static String normalizeToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
