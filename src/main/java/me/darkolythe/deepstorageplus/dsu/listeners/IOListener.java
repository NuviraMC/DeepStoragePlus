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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import static me.darkolythe.deepstorageplus.dsu.StorageUtils.hasNoMeta;
import static me.darkolythe.deepstorageplus.dsu.StorageUtils.stringToMat;
import static me.darkolythe.deepstorageplus.dsu.managers.DSUManager.addDataToContainer;
import static me.darkolythe.deepstorageplus.dsu.managers.SettingsManager.addSpeedUpgrade;
import static me.darkolythe.deepstorageplus.dsu.managers.SettingsManager.getSpeedUpgrade;

public class IOListener implements Listener {

    private final DeepStoragePlus main;
    public IOListener(DeepStoragePlus plugin) {
        this.main = plugin; // set it equal to an instance of main
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onDSUClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
            Player player = event.getPlayer();
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.CHEST) {
                if (!event.isCancelled()) {
                    Chest chest = (Chest) block.getState();
                    Inventory inv = chest.getInventory();
                    if (chest.getInventory().contains(DSUManager.getDSUWall())) {
                        ItemStack item = player.getInventory().getItemInMainHand();
                        if (ItemList.isItem(item, ItemList.KEY_SPEED_UPGRADE)) {
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
        Inventory dest = event.getDestination();

        if (initial.getSize() == 54 || dest.getSize() == 54) {

            ItemStack moveItem = event.getItem();

            ItemStack IOSettings;
            Inventory IOInv;
            ItemStack input;
            ItemStack output;
            String IOStatus = "input";

            if (initial.getSize() == 54) {
                IOSettings = initial.getItem(53);
                IOInv = initial;
                IOStatus = "output";
            } else {
                IOSettings = dest.getItem(53);
                IOInv = dest;
            }

            if (StorageUtils.isDSU(IOInv)) {
                if (IOSettings == null || !ItemList.isItem(IOSettings, ItemList.KEY_IO_SETTINGS)) {
                    return; // kein IO-Setup -> normale Hopper-Logik zulassen
                }
                if (!hasNoMeta(moveItem)) {
                    return; // Plugin-Items nie via Hopper verschieben
                }

                input = getInput(IOSettings);
                output = getOutput(IOSettings);

                int amt = getSpeedUpgrade(IOSettings);

                event.setCancelled(true);

                if (IOStatus.equals("input")) {
                    lookForItemInHopper(initial, dest, input, amt + 1);
                    return;
                } else {
                    lookForItemInChest(output, initial, dest, moveItem, amt + 1);
                    return;
                }
            } else if (StorageUtils.isSorter(IOInv)) {
                if (IOStatus.equals("input")) {
                    main.sorterUpdateManager.sortItems(IOInv, DeepStoragePlus.minTimeSinceLastSortHopper);
                } else {
                    event.setCancelled(true);
                }
            }
        }
    }

    private static ItemStack getInput(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        ItemStack exact = DSUManager.getIoTemplate(item, DSUManager.IO_INPUT_TEMPLATE_TAG);
        if (exact != null) {
            return exact;
        }
        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            return null;
        }

        String line = findIOLine(lore, LanguageManager.getValue("input"), "input", "eingang");
        if (line == null) {
            return null;
        }

        String value = extractIOValue(line);
        if (isAllValue(value)) {
            return null;
        }

        Material parsed = stringToMat(line, "");
        return parsed == Material.AIR ? null : new ItemStack(parsed);
    }

    private static ItemStack getOutput(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        ItemStack exact = DSUManager.getIoTemplate(item, DSUManager.IO_OUTPUT_TEMPLATE_TAG);
        if (exact != null) {
            return exact;
        }
        List<String> lore = meta.getLore();
        if (lore == null || lore.size() < 2) {
            return null;
        }

        String line = findIOLine(lore, LanguageManager.getValue("output"), "output", "ausgang");
        if (line == null) {
            return null;
        }

        String value = extractIOValue(line);
        if (isNoneValue(value)) {
            return null;
        }

        Material parsed = stringToMat(line, "");
        return parsed == Material.AIR ? null : new ItemStack(parsed);
    }

    private void lookForItemInHopper(Inventory initial, Inventory dest, ItemStack input, int amt) {
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () -> {
            boolean moved_stack = false;
            for (int i = 0; i < 5; i++) {
                if (!moved_stack) {
                    ItemStack toMove = initial.getItem(i);
                    if (toMove != null && (input == null || input.isSimilar(toMove))) {
                        ItemStack moving = toMove.clone();
                        moving.setAmount(Math.min(amt, toMove.getAmount()));
                        if (hasNoMeta(moving)) { //items being stored cannot have any special features. ie: damage, enchants, name, lore.
                            for (int j = 0; j < 5; j++) {
                                if (moving.getAmount() > 0) { //if the item amount is greater than 0, it means there are still items to put in the containers
                                    ItemStack container = dest.getItem(8 + (9 * j));
                                    if (container == null) {
                                        continue;
                                    }
                                    addDataToContainer(container, moving); //add the item to the current loop container
                                    toMove.setAmount(toMove.getAmount() - (amt - moving.getAmount()));

                                    main.dsuupdatemanager.updateItemsExact(dest);
                                    moved_stack = true;
                                } else {
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }, 1);
    }

    private void lookForItemInChest(ItemStack output, Inventory initial, Inventory dest, ItemStack moveItem, int amt) {
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () -> {

            if (moveItem == null || output == null || output.getType() == Material.AIR) {
                return;
            }

            int totalAmount = DSUManager.getTotalItemAmount(initial, output);
            int allowed_amt = Math.min(amt, totalAmount);
            if (allowed_amt <= 0) {
                return;
            }

            for (int i = 0; i < 5; i++) {
                ItemStack container = initial.getItem(8 + (9 * i));
                if (container == null || container.getType() == Material.WHITE_STAINED_GLASS_PANE || !container.hasItemMeta()) {
                    continue;
                }
                ItemMeta containerMeta = container.getItemMeta();
                List<String> containerLore = containerMeta != null ? containerMeta.getLore() : null;
                if (containerLore == null) {
                    continue;
                }
                if (DSUManager.dsuContainsItem(initial, output)) {
                    ItemStack toGive = output.clone();
                    toGive.setAmount(allowed_amt);
                    HashMap<Integer, ItemStack> items = dest.addItem(toGive);
                    // subtract any item counts that cant fit into the output hopper
                    int sub = 0;
                    for (ItemStack overflow : items.values()) {
                        sub += overflow.getAmount();
                    }
                    DSUManager.takeItems(output, initial, allowed_amt - sub);

                    main.dsuupdatemanager.updateItemsExact(initial);
                    return;
                }
            }
        }, 1);
    }

    private static String findIOLine(List<String> lore, String configuredKey, String... aliases) {
        String normalizedConfigured = normalizeToken(configuredKey);
        for (String line : lore) {
            String normalizedLine = normalizeToken(ChatColor.stripColor(line));
            if (normalizedLine.isEmpty()) {
                continue;
            }
            if (!normalizedConfigured.isEmpty() && normalizedLine.startsWith(normalizedConfigured + ":")) {
                return line;
            }
            for (String alias : aliases) {
                String normalizedAlias = normalizeToken(alias);
                if (!normalizedAlias.isEmpty() && normalizedLine.startsWith(normalizedAlias + ":")) {
                    return line;
                }
            }
        }
        return null;
    }

    private static String extractIOValue(String line) {
        String stripped = ChatColor.stripColor(line);
        if (stripped == null || stripped.isEmpty()) {
            return "";
        }
        int idx = stripped.indexOf(':');
        if (idx < 0 || idx + 1 >= stripped.length()) {
            return stripped.trim();
        }
        return stripped.substring(idx + 1).trim();
    }

    private static boolean isAllValue(String value) {
        String normalized = normalizeToken(value);
        String config = normalizeToken(LanguageManager.getValue("all"));
        return normalized.equals("all") || normalized.equals("alle") || (!config.isEmpty() && normalized.equals(config));
    }

    private static boolean isNoneValue(String value) {
        String normalized = normalizeToken(value);
        String config = normalizeToken(LanguageManager.getValue("none"));
        return normalized.equals("none") || normalized.equals("keine") || (!config.isEmpty() && normalized.equals(config));
    }

    private static String normalizeToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
