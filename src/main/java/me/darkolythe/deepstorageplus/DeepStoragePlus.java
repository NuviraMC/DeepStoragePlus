package me.darkolythe.deepstorageplus;

import me.darkolythe.deepstorageplus.dsu.listeners.*;
import me.darkolythe.deepstorageplus.dsu.managers.DSUManager;
import me.darkolythe.deepstorageplus.dsu.managers.DSUUpdateManager;
import me.darkolythe.deepstorageplus.dsu.managers.SorterManager;
import me.darkolythe.deepstorageplus.dsu.managers.SorterUpdateManager;
import me.darkolythe.deepstorageplus.io.CommandHandler;
import me.darkolythe.deepstorageplus.io.RecipeMenuManager;
import me.darkolythe.deepstorageplus.utils.*;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;

public final class DeepStoragePlus extends JavaPlugin {

    private static DeepStoragePlus plugin;

    public static String prefix;
    public static int maxrange;
    public static String DSUname = ChatColor.BLUE + "" + ChatColor.BOLD + "Deep Storage Unit";
    public static String sortername = ChatColor.BLUE + "" + ChatColor.BOLD + "Deep Storage Sorter";

    public static final long minTimeSinceLastSortPlayer = 500L;
    public static final long minTimeSinceLastSortHopper = 30000L;

    /*Currently open DSU for each player*/
    public static Map<UUID, Container> openDSU = new ConcurrentHashMap<>();
    /*Currently or last open DSU for each player*/
    public static Map<UUID, Inventory> stashedDSU = new ConcurrentHashMap<>();
    /*Buffered IO inventory, used for getting chat when adding players to lock*/
    public static Map<UUID, Inventory> stashedIO = new ConcurrentHashMap<>();
    /*Boolean for if the user is getting check for user input*/
    public static Map<UUID, Boolean> gettingInput = new ConcurrentHashMap<>();
    /*Boolean for IO inventory to be opened for player*/
    public static Map<UUID, Boolean> openIOInv = new ConcurrentHashMap<>();
    /*Inventory that had bulk items put in that needs to be updated. Updating every item is inefficient and causes lag*/
    public static Map<Location, Long> recentDSUCalls = new ConcurrentHashMap<>();
    /*Inventory of a sorter that needs to be processed*/
    public static Map<Location, Long> recentSortCalls = new ConcurrentHashMap<>();
    /*Cache of DSUs stored per sorter per material. Updated whenever a sort fails.*/
    public static Map<Location, Map<Material, Set<Location>>> sorterLocationCache = new ConcurrentHashMap<>();
    /*Chunk loaded for players opening DSUs far away*/
    public static Map<UUID, Chunk> loadedChunks = new ConcurrentHashMap<>();

    public DSUUpdateManager dsuupdatemanager;
    public DSUManager dsumanager;
    public SorterUpdateManager sorterUpdateManager;
    public SorterManager sorterManager;
    private ItemList itemList;
    private RecipeManager recipeManager;
    private RecipeMenuManager recipeMenuManager;

    public static int maxTypes = 14;

    @Override
    public void onEnable() {
        plugin = this;

        saveDefaultConfig();
        prefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("prefix", "&f&l[&9&lDeepStoragePlus&f&l]")) + " ";
        maxrange = getConfig().getInt("range");
        maxTypes = Math.max(1, getConfig().getInt("container-max-types", 14));

        LanguageManager.setup(plugin);
        itemList = new ItemList(plugin);
        InventoryListener inventorylistener = new InventoryListener(plugin);
        WrenchListener wrenchlistener = new WrenchListener(plugin);
        WirelessListener wirelesslistener = new WirelessListener();
        IOListener iolistener = new IOListener(plugin);
        StorageBreakListener storagebreakslistener = new StorageBreakListener(plugin);
        recipeManager = new RecipeManager(plugin, itemList);
        recipeMenuManager = new RecipeMenuManager(plugin, recipeManager);
        dsuupdatemanager = new DSUUpdateManager(plugin);
        dsumanager = new DSUManager(plugin);
        sorterUpdateManager = new SorterUpdateManager(plugin);
        sorterManager = new SorterManager(plugin);

        inventorylistener.addText();

        getServer().getPluginManager().registerEvents(inventorylistener, plugin);
        getServer().getPluginManager().registerEvents(wrenchlistener, plugin);
        getServer().getPluginManager().registerEvents(wirelesslistener, plugin);
        getServer().getPluginManager().registerEvents(iolistener, plugin);
        getServer().getPluginManager().registerEvents(storagebreakslistener, plugin);
        getServer().getPluginManager().registerEvents(recipeMenuManager, plugin);

        CommandHandler commandHandler = new CommandHandler(itemList, recipeMenuManager);
        var deepStoragePlusCommand = getCommand("deepstorageplus");
        if (deepStoragePlusCommand != null) {
            deepStoragePlusCommand.setExecutor(commandHandler);
            deepStoragePlusCommand.setTabCompleter(commandHandler);
        }
        var dspCommand = getCommand("dsp");
        if (dspCommand != null) {
            dspCommand.setExecutor(commandHandler);
            dspCommand.setTabCompleter(commandHandler);
        }


        getLogger().log(Level.INFO, (prefix + ChatColor.GREEN + "DeepStoragePlus enabled!"));
    }

    public synchronized void reloadRuntimeState() {
        reloadConfig();
        prefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("prefix", "&f&l[&9&lDeepStoragePlus&f&l]")) + " ";
        maxrange = getConfig().getInt("range");
        maxTypes = Math.max(1, getConfig().getInt("container-max-types", 14));
        LanguageManager.setup(this);

        if (itemList != null) {
            itemList.reloadFromConfig();
        }
        if (recipeManager != null) {
            recipeManager.reloadRecipes();
        }
    }

    public synchronized void reloadRecipesOnly() {
        if (recipeManager != null) {
            recipeManager.reloadRecipes();
        }
    }

    @Override
    public void onDisable() {
        openDSU.clear();
        stashedDSU.clear();
        stashedIO.clear();
        gettingInput.clear();
        openIOInv.clear();
        recentDSUCalls.clear();
        recentSortCalls.clear();
        sorterLocationCache.clear();
        loadedChunks.clear();
    }

    public static DeepStoragePlus getInstance() {
        return plugin;
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }
}
