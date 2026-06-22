package fr.citedesiles.citeplugin.listener;

import fr.citedesiles.coreplugin.CoreCDI;
import fr.citedesiles.citeplugin.CitePlugin;
import fr.citedesiles.citeplugin.config.PluginConfig;
import fr.citedesiles.citeplugin.scoreboard.SidebarManager;
import fr.citedesiles.citeplugin.util.TeamDisplayManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Gère les messages de join/quit formatés avec le tag et la couleur d'équipe,
 * ainsi que la mise à jour de l'affichage dans le tab.
 */
public class PlayerJoinListener implements Listener {

    private final CoreCDI api;
    private final PluginConfig config;

    public PlayerJoinListener(CoreCDI api, PluginConfig config) {
        this.api = api;
        this.config = config;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Annuler le message de join par défaut (pour le remplacer de manière asynchrone)
        event.joinMessage(null);

        CitePlugin plugin = JavaPlugin.getPlugin(CitePlugin.class);

        // Mettre à jour l'affichage en jeu (Tab & Nametag)
        TeamDisplayManager.updateDisplay(player, api);

        // Créer le scoreboard sidebar
        SidebarManager sidebarManager = plugin.getSidebarManager();
        if (sidebarManager != null) {
            sidebarManager.createScoreboard(player);
        }

        // Message de join asynchrone pour récupérer les données d'équipe
        if (api != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Component joinMsg = null;
                try {
                    fr.citedesiles.coreplugin.Player apiPlayer = api.getPlayer(player.getUniqueId().toString());

                    if (apiPlayer.team() != -1) {
                        try {
                            fr.citedesiles.coreplugin.Team team = api.getTeam(apiPlayer.team());
                            TextColor teamColor = TextColor.fromHexString(team.color());
                            joinMsg = Component.text("(+) ", NamedTextColor.GREEN)
                                    .append(Component.text("[" + team.tag() + "] ", teamColor))
                                    .append(Component.text(player.getName(), teamColor));
                        } catch (Exception e) {
                            // Si la team n'est pas trouvable, fallback sans tag
                        }
                    }

                    if (joinMsg == null) {
                        joinMsg = Component.text("(+) ", NamedTextColor.GREEN)
                                .append(Component.text(player.getName(), NamedTextColor.YELLOW));
                    }

                    final Component finalJoinMsg = joinMsg;
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(finalJoinMsg));
                } catch (Exception e) {
                    // Erreur API ou pas lié : fallback sans tag
                    joinMsg = Component.text("(+) ", NamedTextColor.GREEN)
                            .append(Component.text(player.getName(), NamedTextColor.YELLOW));
                    final Component finalJoinMsg = joinMsg;
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(finalJoinMsg));
                }
            });
        } else {
            Component joinMsg = Component.text("(+) ", NamedTextColor.GREEN)
                    .append(Component.text(player.getName(), NamedTextColor.YELLOW));
            Bukkit.broadcast(joinMsg);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Message de déconnexion personnalisé : (-) [TAG] Joueur
        TeamDisplayManager.CachedTeam team = TeamDisplayManager.getCachedTeam(player.getUniqueId());

        Component quitPrefix = Component.text("(-) ", NamedTextColor.RED);
        Component tagComp = Component.empty();
        Component nameComp = Component.text(player.getName(), NamedTextColor.YELLOW);

        if (team != null) {
            TextColor teamColor = TextColor.fromHexString(team.color);
            tagComp = Component.text("[" + team.tag + "] ", teamColor);
            nameComp = Component.text(player.getName(), teamColor);
        }

        Component quitMsg = quitPrefix.append(tagComp).append(nameComp);
        event.quitMessage(quitMsg);

        // Nettoyer du cache
        TeamDisplayManager.removeCachedTeam(player.getUniqueId());

        // Nettoyer le scoreboard
        CitePlugin plugin = JavaPlugin.getPlugin(CitePlugin.class);
        SidebarManager sidebarManager = plugin.getSidebarManager();
        if (sidebarManager != null) {
            sidebarManager.removePlayer(player);
        }
    }
}
