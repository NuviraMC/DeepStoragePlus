package me.darkolythe.deepstorageplus.dsu;

import me.darkolythe.deepstorageplus.dsu.managers.DSUManager;
import me.darkolythe.deepstorageplus.dsu.managers.SorterManager;
import me.darkolythe.deepstorageplus.utils.ItemList;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Locale;

public class StorageUtils {

    /**
     * Permissiv: blockiert nur interne Plugin-Items.
     */
    public static boolean hasNoMeta(ItemStack item) {
        return item != null && !ItemList.isPluginItem(item);
    }

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

    public static Material stringToMat(String str, String remStr) {
        if (str == null) return Material.AIR;
        String cleaned = ChatColor.stripColor(str);
        String removeToken = ChatColor.stripColor(remStr == null ? "" : remStr);
        if (!removeToken.isEmpty()) cleaned = cleaned.replace(removeToken, "");
        int separatorIndex = cleaned.indexOf(':');
        if (separatorIndex >= 0 && separatorIndex + 1 < cleaned.length()) {
            cleaned = cleaned.substring(separatorIndex + 1);
        }
        cleaned = cleaned.trim();
        if (cleaned.isEmpty()) return Material.AIR;
        String normalized = cleaned.replace(' ', '_').replace('-', '_').toUpperCase(Locale.ROOT);
        if (normalized.equals("NONE") || normalized.equals("KEINE") || normalized.equals("ALL") || normalized.equals("ALLE")) {
            return Material.AIR;
        }
        Material material = Material.matchMaterial(normalized);
        return material != null ? material : Material.AIR;
    }

    /**
     * Erkennt ein DSU-Inventory zuverlässig — auch wenn die Kiste geschlossen ist
     * oder Paper intern einen anderen InventoryType zurückgibt (z.B. bei Hopper-Events).
     *
     * Der InventoryType-Check wurde entfernt, da Paper bei InventoryMoveItemEvent
     * für Block-Inventories nicht immer CHEST zurückgibt, was dazu führte dass
     * isDSU() false zurückgab und Vanilla Items direkt in die Truhe schrieb.
     *
     * Strategie (in Reihenfolge):
     *  1. Slot 53 enthält ein IO-Settings-Item (immer persistent in der Kiste)
     *  2. Mindestens ein Storage-Container in den Slots 8,17,26,35,44
     */
    public static boolean isDSU(Inventory inv) {
        if (inv == null || inv.getSize() != 54) return false;

        // Primary check: IO-Settings item in slot 53 (always persisted)
        ItemStack slot53 = inv.getItem(53);
        if (ItemList.isItem(slot53, ItemList.KEY_IO_SETTINGS)) return true;

        // Fallback: at least one storage container present
        int[] containerSlots = {8, 17, 26, 35, 44};
        for (int s : containerSlots) {
            if (ItemList.isGroup(inv.getItem(s), ItemList.GROUP_STORAGE_CONTAINER)) return true;
        }

        return false;
    }

    public static boolean isSorter(Inventory inv) {
        if (inv == null || inv.getSize() != 54) return false;
        int[] slots = {18, 19, 20, 21, 22, 23, 24, 25, 26};
        for (int i : slots) {
            if (java.util.Objects.equals(inv.getItem(i), SorterManager.getSorterWall())) return true;
        }
        return false;
    }

    public static Optional<String> getChestCustomName(Block block) {
        Chest chest = (Chest) block.getState();
        if (chest.getInventory().getHolder() instanceof DoubleChest doubleChest) {
            Chest leftChest = doubleChest.getLeftSide() instanceof Chest c ? c : null;
            Chest rightChest = doubleChest.getRightSide() instanceof Chest c ? c : null;
            String leftName = leftChest != null ? leftChest.getCustomName() : null;
            if (leftName != null) return Optional.of(leftName);
            String rightName = rightChest != null ? rightChest.getCustomName() : null;
            if (rightName != null) return Optional.of(rightName);
        }
        return Optional.ofNullable(chest.getCustomName());
    }
}
