package me.darkolythe.deepstorageplus.io;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ConfigManager implements Listener {

    private DeepStoragePlus main;
    public ConfigManager(DeepStoragePlus plugin) {
        main = plugin;
    }
}
