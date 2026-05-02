package me.darkolythe.deepstorageplus.dsu.managers;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages DSU access invites between players.
 * <p>
 * Usage: /dsp invite <player> — sends an invite to the target player.
 * The invited player can type "accept" or "deny" in chat to respond.
 * On acceptance, the inviting player's UUID is stored so the invited
 * player can open that player's DSUs (access controlled in DSUManager).
 */
public class InviteManager implements Listener {

    /**
     * Maps invited player UUID -> inviting player UUID (pending invites).
     */
    public static final Map<UUID, UUID> pendingInvites = new ConcurrentHashMap<>();

    /**
     * Maps invited player UUID -> Set of player UUIDs whose DSUs they may access.
     */
    public static final Map<UUID, java.util.Set<UUID>> dsuAccess = new ConcurrentHashMap<>();

    private final DeepStoragePlus plugin;

    public InviteManager(DeepStoragePlus plugin) {
        this.plugin = plugin;
    }

    /**
     * Send a DSU invite from {@code sender} to {@code target}.
     */
    public static void sendInvite(Player sender, Player target) {
        if (sender.equals(target)) {
            sender.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "You cannot invite yourself.");
            return;
        }

        pendingInvites.put(target.getUniqueId(), sender.getUniqueId());

        target.sendMessage(DeepStoragePlus.prefix + ChatColor.YELLOW + sender.getName()
                + ChatColor.WHITE + " has invited you to access their DSUs. "
                + ChatColor.GREEN + "Type " + ChatColor.BOLD + "accept"
                + ChatColor.GREEN + " or " + ChatColor.RED + ChatColor.BOLD + "deny"
                + ChatColor.RED + " in chat.");

        sender.sendMessage(DeepStoragePlus.prefix + ChatColor.GREEN + "Invite sent to "
                + ChatColor.YELLOW + target.getName() + ChatColor.GREEN + ".");

        // Auto-expire the invite after 60 seconds
        Bukkit.getScheduler().runTaskLater(DeepStoragePlus.getInstance(), () -> {
            if (pendingInvites.remove(target.getUniqueId(), sender.getUniqueId())) {
                target.sendMessage(DeepStoragePlus.prefix + ChatColor.GRAY + "The DSU invite from "
                        + ChatColor.YELLOW + sender.getName() + ChatColor.GRAY + " has expired.");
                sender.sendMessage(DeepStoragePlus.prefix + ChatColor.GRAY + "Your invite to "
                        + ChatColor.YELLOW + target.getName() + ChatColor.GRAY + " has expired.");
            }
        }, 20L * 60);
    }

    /**
     * Check whether {@code viewer} has access to {@code owner}'s DSUs.
     */
    public static boolean hasAccess(UUID owner, UUID viewer) {
        if (owner.equals(viewer)) {
            return true;
        }
        Set<UUID> accessors = dsuAccess.get(owner);
        return accessors != null && accessors.contains(viewer);
    }

    /**
     * Grant {@code viewer} access to {@code owner}'s DSUs.
     */
    public static void grantAccess(UUID owner, UUID viewer) {
        dsuAccess.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(viewer);
    }

    /**
     * Revoke {@code viewer}'s access to {@code owner}'s DSUs.
     */
    public static void revokeAccess(UUID owner, UUID viewer) {
        Set<UUID> accessors = dsuAccess.get(owner);
        if (accessors != null) {
            accessors.remove(viewer);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID pendingInviter = pendingInvites.get(player.getUniqueId());
        if (pendingInviter == null) {
            return;
        }

        String message = event.getMessage().trim().toLowerCase(java.util.Locale.ROOT);
        if (!message.equals("accept") && !message.equals("deny")) {
            return;
        }

        event.setCancelled(true);
        pendingInvites.remove(player.getUniqueId());

        Player inviter = Bukkit.getPlayer(pendingInviter);

        if (message.equals("accept")) {
            grantAccess(pendingInviter, player.getUniqueId());
            player.sendMessage(DeepStoragePlus.prefix + ChatColor.GREEN + "You now have access to "
                    + ChatColor.YELLOW + (inviter != null ? inviter.getName() : "that player")
                    + ChatColor.GREEN + "'s DSUs.");
            if (inviter != null) {
                inviter.sendMessage(DeepStoragePlus.prefix + ChatColor.YELLOW + player.getName()
                        + ChatColor.GREEN + " accepted your DSU invite.");
            }
        } else {
            player.sendMessage(DeepStoragePlus.prefix + ChatColor.RED + "You denied the DSU invite.");
            if (inviter != null) {
                inviter.sendMessage(DeepStoragePlus.prefix + ChatColor.YELLOW + player.getName()
                        + ChatColor.RED + " denied your DSU invite.");
            }
        }
    }

    // Needed for inner-class-free static usage of Set
    private static java.util.Set<UUID> newKeySet() {
        return ConcurrentHashMap.newKeySet();
    }
}
