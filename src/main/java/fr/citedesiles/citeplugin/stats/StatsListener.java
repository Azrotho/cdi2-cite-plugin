package fr.citedesiles.citeplugin.stats;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Écoute les événements de jeu pour alimenter le {@link StatBuffer} :
 * playtime, blocks_broken, distance (en centièmes de bloc), fish, mob_kills, warden_kills.
 */
public class StatsListener implements Listener {
    private static final double MAX_MOVE_DISTANCE = 10.0;

    private final StatBuffer buffer;

    public StatsListener(StatBuffer buffer) {
        this.buffer = buffer;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        buffer.onPlayerJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        buffer.onPlayerQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() != to.getWorld()) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > MAX_MOVE_DISTANCE) return; // téléportation, pas un déplacement

        // Stocké en centièmes de bloc pour éviter la dérive d'arrondi (affichage divisé par 100).
        buffer.add(event.getPlayer().getUniqueId(), "distance", Math.round(dist * 100));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        buffer.add(player.getUniqueId(), "blocks_broken", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            buffer.add(event.getPlayer().getUniqueId(), "fish", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;
        Player killer = entity.getKiller();
        if (killer == null) return;
        buffer.add(killer.getUniqueId(), "mob_kills", 1);
        if (entity.getType() == EntityType.WARDEN) {
            buffer.add(killer.getUniqueId(), "warden_kills", 1);
        }
    }
}
