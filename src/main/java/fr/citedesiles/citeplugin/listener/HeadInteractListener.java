package fr.citedesiles.citeplugin.listener;

import fr.citedesiles.coreplugin.CoreCDI;
import fr.citedesiles.coreplugin.Head;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HeadInteractListener implements Listener {

    private final CoreCDI api;
    private final Plugin plugin;
    // Cache des têtes trouvées par équipe: Map<teamId, Set<"x,y,z">>
    private final Map<Integer, Set<String>> teamHeadsCache = new ConcurrentHashMap<>();
    // Set des requêtes en cours de traitement pour éviter le spam de clics simultanés: Set<"uuid_x,y,z">
    private final Set<String> pendingClicks = ConcurrentHashMap.newKeySet();

    public HeadInteractListener(Plugin plugin, CoreCDI api) {
        this.plugin = plugin;
        this.api = api;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Ignorer l'évènement de la main secondaire pour éviter le déclenchement en double par Bukkit
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        if (block.getType() == Material.PLAYER_HEAD || block.getType() == Material.PLAYER_WALL_HEAD) {
            event.setCancelled(true);
            Player player = event.getPlayer();

            if (api == null) {
                return;
            }

            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            String coordKey = x + "," + y + "," + z;
            String pendingKey = player.getUniqueId() + "_" + coordKey;

            // Si un clic sur cette tête par ce joueur est déjà en cours de traitement asynchrone, ignorer
            if (!pendingClicks.add(pendingKey)) {
                return;
            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    fr.citedesiles.coreplugin.Player apiPlayer = api.getPlayer(player.getUniqueId().toString());
                    if (apiPlayer == null || apiPlayer.team() == -1) {
                        player.sendMessage("§c§lVous devez faire partie d'une équipe pour trouver des têtes.");
                        return;
                    }

                    int teamId = apiPlayer.team();
                    Set<String> foundHeads = teamHeadsCache.computeIfAbsent(teamId, k -> {
                        Set<String> set = new HashSet<>();
                        try {
                            for (Head head : api.getTeamHeads(teamId)) {
                                set.add(head.x() + "," + head.y() + "," + head.z());
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Erreur lors du chargement des têtes pour l'équipe " + teamId + ": " + e.getMessage());
                        }
                        return set;
                    });

                    synchronized (foundHeads) {
                        if (foundHeads.contains(coordKey)) {
                            player.sendMessage("§c§lVous avez déjà trouvé cette tête.");
                            return;
                        }
                    }

                    boolean added = api.addHead(teamId, x, y, z);
                    if (added) {
                        synchronized (foundHeads) {
                            foundHeads.add(coordKey);
                        }
                        player.sendMessage("§a§lVous avez trouvé une tête secrète !");
                    } else {
                        synchronized (foundHeads) {
                            foundHeads.add(coordKey);
                        }
                        player.sendMessage("§c§lVous avez déjà trouvé cette tête.");
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur lors de la vérification de la tête secrète: " + e.getMessage());
                } finally {
                    pendingClicks.remove(pendingKey);
                }
            });
        }
    }
}
