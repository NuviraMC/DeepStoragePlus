package me.darkolythe.deepstorageplus.io;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import me.darkolythe.deepstorageplus.dsu.StorageUtils;
import me.darkolythe.deepstorageplus.dsu.managers.InviteManager;
import me.darkolythe.deepstorageplus.dsu.managers.SettingsManager;
import me.darkolythe.deepstorageplus.utils.ItemList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.List;
import java.util.Locale;

public class CommandHandler implements CommandExecutor, TabCompleter {

    private final ItemList itemList;
    private final RecipeMenuManager recipeMenuManager;

    public CommandHandler(ItemList itemList, RecipeMenuManager recipeMenuManager) {
        super();
        this.itemList = itemList;
        this.recipeMenuManager = recipeMenuManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandlabel, String[] args) {
        if (sender instanceof Player || sender instanceof BlockCommandSender || sender instanceof ConsoleCommandSender) {
            // Example give commands
            // give storagecell16k 6
            // give joe storagecontainer1M 2
            // give wrench
            if (args.length == 1 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("deepstorageplus.admin")) {
                DeepStoragePlus plugin = DeepStoragePlus.getInstance();
                plugin.reloadRuntimeState();
                sender.sendMessage(DeepStoragePlus.prefix + ChatColor.GREEN + "Config reloaded successfully!");
            } else if (args.length >= 2 && args[0].equalsIgnoreCase("reload") && args[1].equalsIgnoreCase("recipes") && sender.hasPermission("deepstorageplus.admin")) {
                DeepStoragePlus plugin = DeepStoragePlus.getInstance();
                plugin.reloadRecipesOnly();
                sender.sendMessage(DeepStoragePlus.prefix + ChatColor.GREEN + "Recipes reloaded successfully!");
            } else if ((args.length == 1 || args.length == 2) && args[0].equalsIgnoreCase("debugmaterial") && sender.hasPermission("deepstorageplus.admin")) {
                sendMaterialDebug(sender, args.length == 2 ? args[1] : null);
            } else if ((args.length == 1 || args.length == 2) && args[0].equalsIgnoreCase("debugmodel") && sender.hasPermission("deepstorageplus.admin")) {
                sendModelDebug(sender, args.length == 2 ? args[1] : null);
            } else if (args.length == 1 && args[0].equalsIgnoreCase("debugio") && sender.hasPermission("deepstorageplus.admin")) {
                sendIODebug(sender);
            } else if (args.length == 1 && args[0].equalsIgnoreCase("recipes")) {
                if (sender instanceof Player player) {
                    recipeMenuManager.openMainMenu(player);
                } else {
                    sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "This menu can only be opened in-game.");
                }
            } else if (args.length == 1 && args[0].equalsIgnoreCase("items") && sender.hasPermission("deepstorageplus.give")) {
                StringBuilder items = new StringBuilder();
                for (String s : itemList.itemListMap.keySet()) {
                    items.append(ChatColor.GREEN).append(s).append(ChatColor.BLUE).append(", ");
                }
                sender.sendMessage(DeepStoragePlus.prefix + items);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
                // /dsp invite <player>
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "Only players can use /dsp invite.");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "Player '" + args[1] + "' is not online.");
                    return true;
                }
                InviteManager.sendInvite(player, target);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("revoke")) {
                // /dsp revoke <player> — revoke access for a previously invited player
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "Only players can use /dsp revoke.");
                    return true;
                }
                // Try to find by online player first, then by name in access map
                Player target = Bukkit.getPlayerExact(args[1]);
                java.util.UUID targetUUID = null;
                if (target != null) {
                    targetUUID = target.getUniqueId();
                } else {
                    // Search stored access map for a UUID matching the name
                    for (java.util.UUID uuid : InviteManager.dsuAccess.getOrDefault(player.getUniqueId(), java.util.Collections.emptySet())) {
                        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                        if (args[1].equalsIgnoreCase(op.getName())) {
                            targetUUID = uuid;
                            break;
                        }
                    }
                }
                if (targetUUID == null) {
                    sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "Could not find '" + args[1] + "' in your invite list.");
                    return true;
                }
                InviteManager.revokeAccess(player.getUniqueId(), targetUUID);
                sender.sendMessage(DeepStoragePlus.prefix + ChatColor.GREEN + "Access revoked for " + ChatColor.YELLOW + args[1] + ChatColor.GREEN + ".");
                if (target != null) {
                    target.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + player.getName() + " has revoked your DSU access.");
                }
            } else if (args.length >= 2 && args[0].equalsIgnoreCase("give") && (sender instanceof BlockCommandSender || sender.hasPermission("deepstorageplus.give"))) {
                Optional<Player> player = Bukkit.getServer().getOnlinePlayers().stream().map(x -> (Player) x).filter(x -> x.getName().equalsIgnoreCase(args[1])).findAny();
                String itemName = null;
                int quantity = 1;
                if (player.isPresent()) { // A recipient player was specified
                    if (args.length >= 4) {
                        itemName = args[2];
                        quantity = parseQuantity(args[3]);
                    }
                    else if (args.length >= 3) {
                        itemName = args[2];
                    }
                } else {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "Console/CommandBlock must specify a target player: /dsp give <player> <item> [amount]");
                        return false;
                    }

                    if (args.length >= 3) {
                        itemName = args[1];
                        quantity = parseQuantity(args[2]);
                    }
                    else {
                        itemName = args[1];
                    }
                }
                Optional<ItemStack> item = itemList.getItem(itemName);
                if (item.isPresent()) {
                    for (int i = 0; i < quantity; i++) {
                        sender.sendMessage(DeepStoragePlus.prefix + ChatColor.GREEN + "given " + itemName + " to " + player.orElseGet(() -> (Player) sender).getName());
                        player.orElseGet(() -> (Player) sender).getInventory().addItem(item.get());
                    }
                } else {
                    sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "Invalid Arguments: /dsp give <user> item <amt>");
                    return true;
                }
            } else {
                if (sender.hasPermission("deepstorageplus.admin")) {
                    sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "Invalid Arguments: /dsp [reload [recipes] | recipes | invite <player> | revoke <player> | debugmaterial [item] | debugmodel [item] | debugio | (give <user> item <amt>) | items]");
                } else if (sender.hasPermission("deepstorageplus.give")) {
                    sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "Invalid Arguments: /dsp [recipes | invite <player> | revoke <player> | (give <user> item <amt>) | items]");
                } else {
                    sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "No permissions");
                }
            }
        }
        return true;
    }

    private static int parseQuantity(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void sendMaterialDebug(CommandSender sender, String specificItem) {
        DeepStoragePlus plugin = DeepStoragePlus.getInstance();
        List<String> keys = new ArrayList<>(itemList.itemListMap.keySet());
        keys.sort(String.CASE_INSENSITIVE_ORDER);

        if (specificItem != null && !specificItem.isBlank()) {
            keys.removeIf(key -> !key.equalsIgnoreCase(specificItem));
            if (keys.isEmpty()) {
                sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "Unknown item key: " + specificItem);
                return;
            }
        }

        sender.sendMessage(DeepStoragePlus.prefix + ChatColor.AQUA + "Material debug (config -> parsed -> active):");
        for (String key : keys) {
            String path = "items." + key + ".material";
            String configured = plugin.getConfig().getString(path);
            Material parsed = ItemList.parseConfiguredMaterial(configured);
            Material active = itemList.getItem(key).map(ItemStack::getType).orElse(null);

            String configuredText = configured == null ? "<missing>" : configured;
            String parsedText = parsed == null ? "INVALID" : parsed.name();
            String activeText = active == null ? "<none>" : active.name();
            ChatColor status = parsed == null ? ChatColor.RED : ChatColor.GREEN;

            sender.sendMessage(ChatColor.DARK_GRAY + "- " + ChatColor.YELLOW + key
                    + ChatColor.GRAY + " | cfg=" + ChatColor.WHITE + configuredText
                    + ChatColor.GRAY + " -> parsed=" + status + parsedText
                    + ChatColor.GRAY + " -> active=" + ChatColor.AQUA + activeText);
        }
    }

    private void sendModelDebug(CommandSender sender, String specificItem) {
        DeepStoragePlus plugin = DeepStoragePlus.getInstance();
        List<String> keys = new ArrayList<>(itemList.itemListMap.keySet());
        keys.sort(String.CASE_INSENSITIVE_ORDER);

        if (specificItem != null && !specificItem.isBlank()) {
            keys.removeIf(key -> !key.equalsIgnoreCase(specificItem));
            if (keys.isEmpty()) {
                sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "Unknown item key: " + specificItem);
                return;
            }
        }

        sender.sendMessage(DeepStoragePlus.prefix + ChatColor.AQUA + "Model debug (config -> parsed -> active):");
        for (String key : keys) {
            String path = "items." + key + ".item-model";
            String configured = plugin.getConfig().getString(path);
            String trimmed = configured == null ? null : configured.trim();
            boolean unset = trimmed == null || trimmed.isEmpty() || trimmed.equalsIgnoreCase("none");
            NamespacedKey parsed = unset ? null : NamespacedKey.fromString(trimmed, plugin);
            NamespacedKey active = itemList.getItem(key)
                    .map(ItemStack::getItemMeta)
                    .map(ItemMeta::getItemModel)
                    .orElse(null);

            String configuredText = unset ? "<none>" : configured;
            String parsedText = unset ? "NONE" : (parsed == null ? "INVALID" : parsed.toString());
            String activeText = active == null ? "<none>" : active.toString();
            ChatColor status = unset || parsed != null ? ChatColor.GREEN : ChatColor.RED;

            sender.sendMessage(ChatColor.DARK_GRAY + "- " + ChatColor.YELLOW + key
                    + ChatColor.GRAY + " | cfg=" + ChatColor.WHITE + configuredText
                    + ChatColor.GRAY + " -> parsed=" + status + parsedText
                    + ChatColor.GRAY + " -> active=" + ChatColor.AQUA + activeText);
        }
    }

    private void sendIODebug(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "debugio can only be used in-game.");
            return;
        }

        Inventory dsu = DeepStoragePlus.openDSU.get(player.getUniqueId()) != null
                ? DeepStoragePlus.openDSU.get(player.getUniqueId()).getInventory()
                : DeepStoragePlus.stashedDSU.get(player.getUniqueId());
        if (dsu == null || dsu.getSize() != 54) {
            sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "Open a DSU first, then run /dsp debugio.");
            return;
        }

        ItemStack ioSettings = dsu.getItem(53);
        if (ioSettings == null || !ioSettings.hasItemMeta()) {
            sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "No IO settings item found in slot 53.");
            return;
        }

        ItemMeta meta = ioSettings.getItemMeta();
        List<String> lore = meta != null ? meta.getLore() : null;
        if (lore == null || lore.isEmpty()) {
            sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "IO lore missing.");
            return;
        }

        String inputLine = findIOLine(lore, pluginToken("input"), "input", "eingang");
        String outputLine = findIOLine(lore, pluginToken("output"), "output", "ausgang");

        String inputValue = extractIOLineValue(inputLine);
        String outputValue = extractIOLineValue(outputLine);

        Material inputMat = inputLine == null ? Material.AIR : StorageUtils.stringToMat(inputLine, "");
        Material outputMat = outputLine == null ? Material.AIR : StorageUtils.stringToMat(outputLine, "");

        boolean inputAll = isToken(inputValue, pluginToken("all"), "all", "alle");
        boolean outputNone = isToken(outputValue, pluginToken("none"), "none", "keine");

        int speed = SettingsManager.getSpeedUpgrade(ioSettings);

        sender.sendMessage(DeepStoragePlus.prefix + ChatColor.AQUA + "IO Debug:");
        sender.sendMessage(ChatColor.DARK_GRAY + "- " + ChatColor.YELLOW + "Input line" + ChatColor.GRAY + ": " + ChatColor.WHITE + (inputLine == null ? "<missing>" : ChatColor.stripColor(inputLine)));
        sender.sendMessage(ChatColor.DARK_GRAY + "- " + ChatColor.YELLOW + "Input parsed" + ChatColor.GRAY + ": " + ChatColor.AQUA + (inputAll || inputMat == Material.AIR ? "ALL" : inputMat.name()));
        sender.sendMessage(ChatColor.DARK_GRAY + "- " + ChatColor.YELLOW + "Output line" + ChatColor.GRAY + ": " + ChatColor.WHITE + (outputLine == null ? "<missing>" : ChatColor.stripColor(outputLine)));
        sender.sendMessage(ChatColor.DARK_GRAY + "- " + ChatColor.YELLOW + "Output parsed" + ChatColor.GRAY + ": " + ChatColor.AQUA + (outputNone || outputMat == Material.AIR ? "NONE" : outputMat.name()));
        sender.sendMessage(ChatColor.DARK_GRAY + "- " + ChatColor.YELLOW + "IO speed" + ChatColor.GRAY + ": " + ChatColor.AQUA + speed + ChatColor.GRAY + " (per transfer up to " + ChatColor.AQUA + (speed + 1) + ChatColor.GRAY + ")");
    }

    private static String pluginToken(String key) {
        String value = me.darkolythe.deepstorageplus.utils.LanguageManager.getValue(key);
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String findIOLine(List<String> lore, String configuredKey, String... aliases) {
        for (String line : lore) {
            String stripped = ChatColor.stripColor(line);
            String normalizedLine = stripped == null ? "" : stripped.trim().toLowerCase(Locale.ROOT);
            if (!configuredKey.isEmpty() && normalizedLine.startsWith(configuredKey + ":")) {
                return line;
            }
            for (String alias : aliases) {
                String normalizedAlias = alias == null ? "" : alias.trim().toLowerCase(Locale.ROOT);
                if (!normalizedAlias.isEmpty() && normalizedLine.startsWith(normalizedAlias + ":")) {
                    return line;
                }
            }
        }
        return null;
    }

    private static String extractIOLineValue(String line) {
        if (line == null) {
            return "";
        }
        String stripped = ChatColor.stripColor(line);
        int idx = stripped.indexOf(':');
        if (idx < 0 || idx + 1 >= stripped.length()) {
            return stripped.trim().toLowerCase(Locale.ROOT);
        }
        return stripped.substring(idx + 1).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isToken(String value, String configured, String... aliases) {
        if (!configured.isEmpty() && value.equals(configured)) {
            return true;
        }
        for (String alias : aliases) {
            if (value.equals(alias)) {
                return true;
            }
        }
        return false;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            if (sender.hasPermission("deepstorageplus.admin")) {
                suggestions.add("reload");
                suggestions.add("debugmaterial");
                suggestions.add("debugmodel");
                suggestions.add("debugio");
            }
            suggestions.add("recipes");
            if (sender instanceof Player) {
                suggestions.add("invite");
                suggestions.add("revoke");
            }
            if (sender.hasPermission("deepstorageplus.give")) {
                suggestions.add("items");
                suggestions.add("give");
            }
            return filterPrefix(suggestions, args[0]);
        }

        if ((args[0].equalsIgnoreCase("debugmaterial") || args[0].equalsIgnoreCase("debugmodel"))
                && sender.hasPermission("deepstorageplus.admin") && args.length == 2) {
            return filterPrefix(itemList.itemListMap.keySet().stream().toList(), args[1]);
        }

        if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("deepstorageplus.admin") && args.length == 2) {
            return filterPrefix(List.of("recipes"), args[1]);
        }

        // Tab-complete online player names for invite/revoke
        if ((args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("revoke"))
                && sender instanceof Player player && args.length == 2) {
            List<String> names = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> !name.equals(player.getName()))
                    .toList();
            return filterPrefix(names, args[1]);
        }

        if (!sender.hasPermission("deepstorageplus.give")) {
            return Collections.emptyList();
        }


        if (!args[0].equalsIgnoreCase("give")) {
            return Collections.emptyList();
        }

        if (args.length == 2) {
            List<String> suggestions = new ArrayList<>();
            suggestions.addAll(filterPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]));
            suggestions.addAll(filterPrefix(itemList.itemListMap.keySet().stream().toList(), args[1]));
            return uniqueSorted(suggestions);
        }

        boolean secondArgIsPlayer = Bukkit.getOnlinePlayers().stream().anyMatch(player -> player.getName().equalsIgnoreCase(args[1]));
        if (args.length == 3) {
            if (secondArgIsPlayer) {
                return filterPrefix(itemList.itemListMap.keySet().stream().toList(), args[2]);
            }
            return filterPrefix(List.of("1", "16", "64", "128", "256"), args[2]);
        }

        if (args.length == 4 && secondArgIsPlayer) {
            return filterPrefix(List.of("1", "16", "64", "128", "256"), args[3]);
        }

        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> options, String current) {
        String lower = current == null ? "" : current.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    private static List<String> uniqueSorted(List<String> options) {
        return options.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
