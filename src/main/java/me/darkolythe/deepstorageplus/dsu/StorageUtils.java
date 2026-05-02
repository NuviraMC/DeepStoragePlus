package me.darkolythe.deepstorageplus.dsu;

import me.darkolythe.deepstorageplus.dsu.managers.DSUManager;
import me.darkolythe.deepstorageplus.dsu.managers.SorterManager;
import me.darkolythe.deepstorageplus.utils.ItemList;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;
import java.util.Optional;
import java.util.Locale;

public class StorageUtils {

    /**
     * Returns true if the item has no "meaningful" meta — i.e. it is a plain
     * vanilla item that the DSU can store.
     *
     * Explicitly ignored (NOT considered meaningful meta):
     *   - item-model / custom-model-data  (resource-pack texture overrides)
     *   - the internal "Item Count: x" lore added by DSUUpdateManager.createItem()
     *
     * Everything else (display name, lore, enchants, damage, potion type, …)
     * still blocks the item from being stored.
     */
    public static boolean hasNoMeta(ItemStack item) {
        if (ItemList.isPluginItem(item)) {
            return false;
        }
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable && damageable.getDamage() != 0) {
            return false;
        }
        if (item.getType().toString().contains("SHULKER_BOX")) {
            return false;
        }
        var meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasEnchants()) {
                return false;
            }
            if (meta.hasDisplayName()) {
                return false;
            }
            if (item.getType() == Material.ENCHANTED_BOOK) {
                return false;
            }
            if (item.getType() == Material.FIREWORK_ROCKET) {
                return false;
            }
            if (item.getType().toString().contains("POTION")) {
                return false;
            }
            if (item.getType() == Material.TIPPED_ARROW) {
                return false;
            }
            if (meta.hasLore()) {
                var lore = meta.getLore();
                // Allow items whose only lore is the internal DSU display lore
                // ("Item Count: x") — these are display items that were taken out
                // of the DSU grid and should be storable again.
                if (lore != null && !lore.isEmpty() && lore.getFirst().contains("Item Count: ")) {
                    // Only allowed if no enchants either (already checked above).
                    // item-model presence is irrelevant here.
                    return true;
                }
                return false;
            }
            // item-model alone (e.g. a resource-pack texture set via setItemModel)
            // is intentionally NOT treated as meaningful meta. An item that only
            // has an item-model set is functionally a plain vanilla item.
        }
        return true;
    }

    /*
    Turns a Material into a String. ex: EMERALD_ORE -> Emerald Ore
     */
    public static String matToString(Material mat) {
        String raw = mat.toString().toLowerCase().replace('_', ' ');
        StringBuilder result = new StringBuilder(raw.length());
        boolean capitalizeNext = true;
        for (char c : raw.toCharArray()) {
            if (capitalizeNext && Character.isLetter(c)) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
                capitalizeNext = c == ' ';
            }
        }
        return result.toString();
    }

    /*
    Turns a String into a Material. ex: Emerald Ore -> EMERALD_ORE
     */
    public static Material stringToMat(String str, String remStr) {
        if (str == null) {
            return Material.AIR;
        }

        String cleaned = ChatColor.stripColor(str);
        if (cleaned == null) {
            return Material.AIR;
        }

        String removeToken = ChatColor.stripColor(remStr == null ? "" : remStr);
        if (removeToken != null && !removeToken.isEmpty()) {
            cleaned = cleaned.replace(removeToken, "");
        }

        int separatorIndex = cleaned.indexOf(':');
        if (separatorIndex >= 0 && separatorIndex + 1 < cleaned.length()) {
            cleaned = cleaned.substring(separatorIndex + 1);
        }

        cleaned = cleaned.trim();
        if (cleaned.isEmpty()) {
            return Material.AIR;
        }

        String normalized = cleaned
                .replace(' ', '_')
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);

        if (normalized.equals("NONE") || normalized.equals("KEINE") || normalized.equals("ALL") || normalized.equals("ALLE")) {
            return Material.AIR;
        }

        Material material = Material.matchMaterial(normalized);
        return material != null ? material : Material.AIR;
    }

    public static boolean isDSU(Inventory inv) {
        if (inv.getSize() != 54)
            return false;

        if (inv.getType() != InventoryType.CHEST)
            return false;

        int[] slots = {7, 16, 25, 34, 43, 52};
        boolean isDSU = false;

        for (int i : slots) {
            if (Objects.equals(inv.getItem(i), DSUManager.getDSUWall()))
                isDSU = true;
        }

        return isDSU;
    }

    public static boolean isSorter(Inventory inv) {
        if (inv.getSize() != 54)
            return false;

        if (inv.getType() != InventoryType.CHEST)
            return false;

        int[] slots = {18, 19, 20, 21, 22, 23, 24, 25, 26};
        boolean isSorter = false;

        for (int i : slots) {
            if (Objects.equals(inv.getItem(i), SorterManager.getSorterWall()))
                isSorter = true;
        }

        return isSorter;
    }

    /**
     * Returns the custom name of a chest or double chest, if either side has one.
     */
    public static Optional<String> getChestCustomName(Block block) {
        Chest chest = (Chest) block.getState();
        if (chest.getInventory().getHolder() instanceof DoubleChest doubleChest) {
            Chest leftChest = (Chest) doubleChest.getLeftSide();
            Chest rightChest = (Chest) doubleChest.getRightSide();
            String leftName = leftChest.getCustomName();
            if (leftName != null) {
                return Optional.of(leftName);
            }
            String rightName = rightChest.getCustomName();
            if (rightName != null) {
                return Optional.of(rightName);
            }
        }
        return Optional.ofNullable(chest.getCustomName());
    }
}
