package me.darkolythe.deepstorageplus.dsu.managers;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import me.darkolythe.deepstorageplus.utils.ItemList;
import me.darkolythe.deepstorageplus.utils.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static me.darkolythe.deepstorageplus.dsu.StorageUtils.matToString;
import static me.darkolythe.deepstorageplus.dsu.StorageUtils.stringToMat;

public class SettingsManager {

    private static final String SPEED_PDC_KEY = "dsu_io_speed";
    private static final int DEFAULT_MAX_SPEED_LEVEL = 63;
    private static final Pattern SPEED_DIGITS_PATTERN = Pattern.compile("(\\d+)");

    /*
    Takes the dsu's inventory and lets the player choose an item to export from the list. (Also allow for other things? we shall see. maybe amount of item? Upgrades?)
     */
    public static Inventory createIOInventory(Inventory DSUInv) {
        Inventory IOInv = Bukkit.createInventory(null, 54, ChatColor.BLUE + "" + ChatColor.BOLD + LanguageManager.getValue("dsuioconfig"));

        for (int x = 0; x < 53; x++) {
            if (x % 9 != 8) {
                ItemStack source = DSUInv.getItem(x);
                if (source != null) {
                    IOInv.setItem(x, source.clone());
                }
            }
        }

        ItemStack IOItem = DSUInv.getItem(53);
        ItemMeta IOMeta = IOItem != null ? IOItem.getItemMeta() : null;
        List<String> lore = IOMeta != null && IOMeta.getLore() != null ? IOMeta.getLore() : List.of();

        /*
         * Create the IO settings item with use of the DSUinv to check for existing IO items
         */
        ItemStack exactInput = DSUManager.getIoTemplate(IOItem, DSUManager.IO_INPUT_TEMPLATE_TAG);
        String inputLine = lore.isEmpty() ? "" : lore.getFirst();
        Material inputMaterial = stringToMat(inputLine, ChatColor.GRAY + LanguageManager.getValue("input") + ": " + ChatColor.GREEN);
        if (exactInput != null) {
            IOInv.setItem(8, exactInput.clone());
        } else if (inputMaterial == Material.AIR || isAllToken(inputLine)) {
            IOInv.setItem(8, getEmptyInputSlot());
        } else {
            ItemStack newInput = getEmptyInputSlot();
            newInput.setType(inputMaterial);
            ItemMeta inputMeta = newInput.getItemMeta();
            if (inputMeta != null) {
                inputMeta.setDisplayName(ChatColor.GRAY + LanguageManager.getValue("input") + ": " + ChatColor.GREEN + matToString(inputMaterial));
                inputMeta.setLore(List.of(ChatColor.GRAY + LanguageManager.getValue("clicktoclear")));
                newInput.setItemMeta(inputMeta);
            }
            IOInv.setItem(8, newInput);
        }

        ItemStack exactOutput = DSUManager.getIoTemplate(IOItem, DSUManager.IO_OUTPUT_TEMPLATE_TAG);
        String outputLine = lore.size() > 1 ? lore.get(1) : "";
        Material outputMaterial = stringToMat(outputLine, ChatColor.GRAY + LanguageManager.getValue("output") + ": " + ChatColor.GREEN);
        if (exactOutput != null) {
            IOInv.setItem(17, exactOutput.clone());
        } else if (outputMaterial == Material.AIR || isNoneToken(outputLine)) {
            IOInv.setItem(17, getEmptyOutputSlot());
        } else {
            ItemStack newOutput = getEmptyOutputSlot();
            newOutput.setType(outputMaterial);
            ItemMeta outputMeta = newOutput.getItemMeta();
            if (outputMeta != null) {
                outputMeta.setDisplayName(ChatColor.GRAY + LanguageManager.getValue("output") + ": " + ChatColor.GREEN + matToString(outputMaterial));
                outputMeta.setLore(List.of(ChatColor.GRAY + LanguageManager.getValue("clicktoclear")));
                newOutput.setItemMeta(outputMeta);
            }
            IOInv.setItem(17, newOutput);
        }

        ItemStack sortSlot = new ItemStack(Material.COMPASS);
        ItemMeta sortMeta = sortSlot.getItemMeta();
        if (sortMeta != null) {
            String sortLine = lore.size() > 2 ? lore.get(2) : "";
            sortMeta.setDisplayName(ChatColor.GRAY + LanguageManager.getValue("sortingby") + ": " + sortLine.replace(ChatColor.GRAY + LanguageManager.getValue("sortingby") + ": ", ""));
            sortMeta.setLore(List.of(ChatColor.GRAY + LanguageManager.getValue("changesorting"),
                    ChatColor.BLUE + LanguageManager.getValue("container") + ": " + ChatColor.GRAY + LanguageManager.getValue("sortscontainer"),
                    ChatColor.BLUE + LanguageManager.getValue("alpha") + ": " + ChatColor.GRAY + LanguageManager.getValue("sortsalpha"),
                    ChatColor.BLUE + LanguageManager.getValue("amount") + ": " + ChatColor.GRAY + LanguageManager.getValue("sortsamount"),
                    ChatColor.BLUE + "ID: " + ChatColor.GRAY + LanguageManager.getValue("sortsid")));
            sortSlot.setItemMeta(sortMeta);
        }
        IOInv.setItem(26, sortSlot);

        IOInv.setItem(53, createDSULock(DSUInv));

        return IOInv;
    }

    /*
     * Add lock to IO settings menu
     */
    public static ItemStack createDSULock(Inventory LockInv) {
        ItemStack lock = new ItemStack(ItemList.resolveItemMaterial(ItemList.KEY_DSU_LOCK, Material.TRIPWIRE_HOOK));
        ItemMeta lockmeta = lock.getItemMeta();
        if (lockmeta != null) {
            lockmeta.setDisplayName(ChatColor.BLUE + "Lock DSU");
        }
        List<String> locklore = new ArrayList<>();
        locklore.add(ChatColor.GRAY + LanguageManager.getValue("leftclicktoadd"));
        locklore.add(ChatColor.GRAY + LanguageManager.getValue("rightclicktoremove"));
        locklore.add("");
        locklore.add(ChatColor.GRAY + LanguageManager.getValue("owner") + ": " + ChatColor.BLUE + getOwner(LockInv.getItem(53))[0]);

        if (isLocked(LockInv.getItem(53))) {
            locklore.add(ChatColor.RED + LanguageManager.getValue("locked"));
            for (String s : getLockedUsers(LockInv.getItem(53))) {
                locklore.add(ChatColor.WHITE + s);
            }
        } else {
            locklore.add(ChatColor.GREEN + LanguageManager.getValue("unlocked"));
        }
        if (lockmeta != null) {
            lockmeta.setLore(locklore);
            lockmeta.getPersistentDataContainer().set(new NamespacedKey(DeepStoragePlus.getInstance(), "item_id"), PersistentDataType.STRING, ItemList.KEY_DSU_LOCK);
            lockmeta.getPersistentDataContainer().set(new NamespacedKey(DeepStoragePlus.getInstance(), "item_group"), PersistentDataType.STRING, ItemList.GROUP_SUPPORT);
            lock.setItemMeta(lockmeta);
        }

        return lock;
    }

    /*
    Create an empty input slot item
     */
    private static ItemStack getEmptyInputSlot() {
        ItemStack inputSlot = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta inputMeta = inputSlot.getItemMeta();
        if (inputMeta != null) {
            inputMeta.setDisplayName(ChatColor.GRAY + LanguageManager.getValue("input") + ": " + ChatColor.BLUE + LanguageManager.getValue("all"));
            inputMeta.setLore(List.of(ChatColor.GRAY + LanguageManager.getValue("clicktostart"),
                    ChatColor.GRAY + LanguageManager.getValue("clickinput"),
                    ChatColor.GRAY + LanguageManager.getValue("leaveasall")));
            inputSlot.setItemMeta(inputMeta);
        }

        return inputSlot;
    }

    /*
    Create an empty output slot item
     */
    private static ItemStack getEmptyOutputSlot() {
        ItemStack outputSlot = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta outputMeta = outputSlot.getItemMeta();
        if (outputMeta != null) {
            outputMeta.setDisplayName(ChatColor.GRAY + LanguageManager.getValue("output") + ": " + ChatColor.BLUE + LanguageManager.getValue("none"));
            outputMeta.setLore(List.of(ChatColor.GRAY + LanguageManager.getValue("clicktostart"),
                    ChatColor.GRAY + LanguageManager.getValue("clickoutput")));
            outputSlot.setItemMeta(outputMeta);
        }

        return outputSlot;
    }

    /*
    Initialize the selection tool by cancelling all other current selections and enchanting the current slot
     */
    public static void startSelection(int slot, Inventory inv) {
        for (int i = 0; i < inv.getContents().length; i++) {
            ItemStack selected = inv.getItem(i);
            if (selected != null && selected.hasItemMeta()) {
                ItemMeta selectedMeta = selected.getItemMeta();
                if (selectedMeta != null && !selectedMeta.getEnchants().isEmpty()) {
                    ItemStack newItem = selected.clone();
                    ItemMeta newMeta = newItem.getItemMeta();
                    if (newMeta != null) {
                        newMeta.removeEnchant(Enchantment.UNBREAKING);
                        newItem.setItemMeta(newMeta);
                    }
                    inv.setItem(i, newItem);
                }
            }
        }
        ItemStack item;
        if (slot == 8) {
            item = getEmptyInputSlot();
        } else {
            item = getEmptyOutputSlot();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        inv.setItem(slot, item);
    }

    public static String[] getOwner(ItemStack IOSettings) {
        if (IOSettings != null) {
            ItemMeta meta = IOSettings.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore();
                if (lore != null) {
                    for (String s : lore) {
                        String ownstr = ChatColor.GRAY + LanguageManager.getValue("owner");
                        if (s.contains(ownstr + ": ")) {
                            String owner = s.replaceAll(ownstr + ": " + ChatColor.BLUE, "");
                            String uuid = meta.getPersistentDataContainer().get(new NamespacedKey(DeepStoragePlus.getInstance(), "dsu_owner_uuid"), PersistentDataType.STRING);
                            if (uuid == null) {
                                uuid = "";
                            }

                            return new String[]{owner, uuid};
                        }
                    }
                }
            }
        }
        return new String[]{"", ""};
    }

    public static List<String> getLockedUsers(ItemStack IOSettings) {
        List<String> users = new ArrayList<>();
        if (IOSettings != null) {
            ItemMeta meta = IOSettings.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore();
                boolean pastLocked = false;
                if (lore != null) {
                    for (String s : lore) {
                        if (pastLocked) {
                            if (s.contains(ChatColor.WHITE.toString())) {
                                users.add(s.replaceAll(ChatColor.WHITE.toString(), ""));
                            } else {
                                break;
                            }
                        }
                        if (s.equals(ChatColor.RED + LanguageManager.getValue("locked"))) {
                            pastLocked = true;
                        }
                    }
                }
            }
        }
        return users;
    }

    public static boolean isLocked(ItemStack IOSettings) {
        if (IOSettings != null) {
            ItemMeta meta = IOSettings.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore();
                if (lore != null) {
                    for (String s : lore) {
                        if (s.equals(ChatColor.RED + LanguageManager.getValue("locked"))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean getLocked(ItemStack IOSettings, Player player) {
        List<String> users = getLockedUsers(IOSettings);
        String owner = getOwner(IOSettings)[0];
        if (owner != null && !owner.isEmpty()) {
            users.add(owner);
        }
        for (String s : users) {
            if (player.getName().equals(s)) {
                return true;
            }
        }
        return (player.getUniqueId().toString().equals(getOwner(IOSettings)[1]));
    }

    public static int getSpeedUpgrade(ItemStack item) {
        if (item == null) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }

        Integer pdcSpeed = meta.getPersistentDataContainer().get(getSpeedUpgradeKey(), PersistentDataType.INTEGER);
        if (pdcSpeed != null) {
            return Math.max(0, pdcSpeed);
        }

        List<String> lore = meta.getLore();
        if (lore == null) {
            return 0;
        }

        for (String line : lore) {
            if (isSpeedLoreLine(line)) {
                int parsed = parseFirstPositiveNumber(line);
                meta.getPersistentDataContainer().set(getSpeedUpgradeKey(), PersistentDataType.INTEGER, parsed);
                item.setItemMeta(meta);
                return parsed;
            }
        }
        return 0;
    }

    public static ItemStack addSpeedUpgrade(ItemStack item) {
        if (item == null) {
            return null;
        }

        int i = 0;
        boolean found = false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        }

        int amt = getSpeedUpgrade(item);
        int maxSpeedLevel = getMaxSpeedLevel();
        if (amt >= maxSpeedLevel) {
            return null;
        }

        for (String l : lore) {
            if (isSpeedLoreLine(l)) {
                found = true;
                break;
            }
            i++;
        }

        int newSpeedLevel = amt + 1;
        String speedLoreLine = ChatColor.GRAY + LanguageManager.getValue("iospeed") + ": " + ChatColor.GREEN + "+" + newSpeedLevel;
        if (found) {
            lore.set(i, speedLoreLine);
        } else {
            lore.add(speedLoreLine);
        }

        meta.getPersistentDataContainer().set(getSpeedUpgradeKey(), PersistentDataType.INTEGER, newSpeedLevel);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static NamespacedKey getSpeedUpgradeKey() {
        return new NamespacedKey(DeepStoragePlus.getInstance(), SPEED_PDC_KEY);
    }

    private static int getMaxSpeedLevel() {
        int configured = DeepStoragePlus.getInstance().getConfig().getInt("io-speed.max-level", DEFAULT_MAX_SPEED_LEVEL);
        return Math.max(1, configured);
    }

    private static int parseFirstPositiveNumber(String line) {
        if (line == null) {
            return 0;
        }
        String stripped = ChatColor.stripColor(line);

        Matcher matcher = SPEED_DIGITS_PATTERN.matcher(stripped);
        if (!matcher.find()) {
            return 0;
        }

        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean isSpeedLoreLine(String line) {
        if (line == null) {
            return false;
        }
        String stripped = ChatColor.stripColor(line);

        String trimmed = stripped.trim();
        String configuredPrefix = LanguageManager.getValue("iospeed") + ":";
        if (trimmed.startsWith(configuredPrefix)) {
            return true;
        }

        String normalized = trimmed.toLowerCase().replace(" ", "");
        return normalized.startsWith("iospeed:") || normalized.startsWith("io-geschwindigkeit:");
    }

    private static boolean isAllToken(String line) {
        String value = normalizeIOValue(line);
        String configured = normalizeConfigToken(LanguageManager.getValue("all"));
        return value.equals("all") || value.equals("alle") || (!configured.isEmpty() && value.equals(configured));
    }

    private static boolean isNoneToken(String line) {
        String value = normalizeIOValue(line);
        String configured = normalizeConfigToken(LanguageManager.getValue("none"));
        return value.equals("none") || value.equals("keine") || (!configured.isEmpty() && value.equals(configured));
    }

    private static String normalizeIOValue(String line) {
        if (line == null) {
            return "";
        }
        String stripped = ChatColor.stripColor(line);
        int colon = stripped.indexOf(':');
        String value = colon >= 0 && colon + 1 < stripped.length() ? stripped.substring(colon + 1) : stripped;
        return value.trim().toLowerCase();
    }

    private static String normalizeConfigToken(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
