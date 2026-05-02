package me.darkolythe.deepstorageplus.dsu.listeners;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import me.darkolythe.deepstorageplus.dsu.managers.DSUManager;
import me.darkolythe.deepstorageplus.utils.LanguageManager;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

import static me.darkolythe.deepstorageplus.dsu.managers.WirelessManager.*;


public class WirelessListener implements Listener {

    public WirelessListener() {
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onDSUClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
            Player player = event.getPlayer();
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.CHEST) {
                Chest chest = (Chest) block.getState();
                if (chest.getInventory().contains(DSUManager.getDSUWall())) {
                    ItemStack item = player.getInventory().getItemInMainHand();
                    ItemMeta meta = item != null ? item.getItemMeta() : null;
                    List<String> lore = meta != null ? meta.getLore() : null;
                    if (isWirelessTerminal(item) && lore != null && lore.contains(ChatColor.RED + "" + ChatColor.BOLD + LanguageManager.getValue("unlinked"))) {
                        event.setCancelled(true);
                        updateTerminal(item, block.getX(), block.getY(), block.getZ(), block.getWorld());
                        return;
                    }
                }
            }
            if (block == null || !(block.getState() instanceof InventoryHolder)) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                ItemMeta meta = hand != null ? hand.getItemMeta() : null;
                List<String> lore = meta != null ? meta.getLore() : null;
                if (isWirelessTerminal(hand) && lore != null && lore.contains(ChatColor.GREEN + "" + ChatColor.BOLD + LanguageManager.getValue("linked"))) {
                        Inventory dsu = getWirelessDSU(hand, player);
                        if (dsu != null) {
                            if (player.hasPermission("deepstorageplus.wireless")) {
                                event.setCancelled(true);
                                player.openInventory(dsu);
                                var location = dsu.getLocation();
                                if (location != null) {
                                    Chunk c = location.getChunk();
                                    c.setForceLoaded(true);
                                    DeepStoragePlus.loadedChunks.put(player.getUniqueId(), c);
                                }
                            } else {
                                player.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + LanguageManager.getValue("nowirelesspermission"));
                            }
                        }
                    }
            }
        }
    }

    @EventHandler
    private void onTerminalSwap(PlayerSwapHandItemsEvent event) {
        ItemStack item = event.getOffHandItem();
        if (event.getPlayer().isSneaking()) {
            if (isWirelessTerminal(item)) {
                ItemMeta meta = item.getItemMeta();
                List<String> lore = meta != null ? meta.getLore() : null;
                if (lore != null && !lore.isEmpty() && lore.get(0).equals(ChatColor.GREEN + "" + ChatColor.BOLD + LanguageManager.getValue("linked"))) {
                    String name = item.getItemMeta().getDisplayName();
                    ItemStack newitem = createTerminal();
                    ItemMeta newMeta = newitem.getItemMeta();
                    newMeta.setDisplayName(name);
                    newitem.setItemMeta(newMeta);
                    event.getPlayer().getInventory().setItemInMainHand(newitem);

                    event.setCancelled(true);
                }
            }
        }
    }
}
