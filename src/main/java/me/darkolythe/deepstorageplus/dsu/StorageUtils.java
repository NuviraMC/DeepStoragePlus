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

import java.util.Objects;
import java.util.Optional;
import java.util.Locale;

public class StorageUtils {

    /**
     * Voll permissiv: DSU/Sorter/Hopper sollen alle normalen Items akzeptieren.
     * Blockiert werden nur interne Plugin-Items, damit die GUI-Items nicht
     * versehentlich eingespeichert werden.
     */
    public static boolean hasNoMeta(ItemStack item) {
        return item != null && !ItemList.isPluginItem(item);
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
        String removeToken = ChatColor.stripColor(remStr == null ? "" : remStr);
        if (!removeToken.isEmpty()) {
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
            Chest leftChest = doubleChest.getLeftSide() instanceof Chest c ? c : null;
            Chest rightChest = doubleChest.getRightSide() instanceof Chest c ? c : null;
            String leftName = leftChest != null ? leftChest.getCustomName() : null;
            if (leftName != null) {
                return Optional.of(leftName);
            }
            String rightName = rightChest != null ? rightChest.getCustomName() : null;
            if (rightName != null) {
                return Optional.of(rightName);
            }
        }
        return Optional.ofNullable(chest.getCustomName());
    }
}
