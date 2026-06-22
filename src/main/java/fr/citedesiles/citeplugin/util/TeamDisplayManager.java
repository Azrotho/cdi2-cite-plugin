package fr.citedesiles.citeplugin.util;

import fr.citedesiles.coreplugin.CoreCDI;
import fr.citedesiles.coreplugin.Player;
import fr.citedesiles.coreplugin.Team;
import fr.citedesiles.citeplugin.CitePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TeamDisplayManager {

    public static class CachedTeam {
        public final String tag;
        public final String color;

        public CachedTeam(String tag, String color) {
            this.tag = tag;
            this.color = color;
        }
    }

    private static final Map<java.util.UUID, CachedTeam> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private TeamDisplayManager() {}

    public static CachedTeam getCachedTeam(java.util.UUID uuid) {
        return CACHE.get(uuid);
    }

    public static void removeCachedTeam(java.util.UUID uuid) {
        CACHE.remove(uuid);
    }

    public static void updateDisplay(org.bukkit.entity.Player player, CoreCDI api) {
        if (api == null) return;

        CitePlugin plugin = JavaPlugin.getPlugin(CitePlugin.class);

        // Run async to avoid blocking main thread with API HTTP calls
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Player apiPlayer = api.getPlayer(player.getUniqueId().toString());
                if (apiPlayer.team() != -1) {
                    Team team = api.getTeam(apiPlayer.team());

                    // Put in local cache
                    CACHE.put(player.getUniqueId(), new CachedTeam(team.tag(), team.color()));

                    TextColor teamHexColor = TextColor.fromHexString(team.color());
                    NamedTextColor closestNamed = getClosestNamedColor(team.color());

                    // Run sync to update Scoreboard and Tab
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                        String teamName = "team_" + team.id();
                        org.bukkit.scoreboard.Team boardTeam = scoreboard.getTeam(teamName);
                        if (boardTeam == null) {
                            boardTeam = scoreboard.registerNewTeam(teamName);
                        }

                        // Prefix in scoreboard team
                        boardTeam.prefix(Component.text("[" + team.tag() + "] ", teamHexColor));
                        boardTeam.color(closestNamed);
                        boardTeam.addEntry(player.getName());

                        // Tab name
                        Component tabName = Component.text("[" + team.tag() + "] ", teamHexColor)
                                .append(Component.text(player.getName(), teamHexColor));
                        player.playerListName(tabName);
                    });
                } else {
                    // No team: remove from cache and scoreboard
                    CACHE.remove(player.getUniqueId());

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                        org.bukkit.scoreboard.Team boardTeam = scoreboard.getEntryTeam(player.getName());
                        if (boardTeam != null) {
                            boardTeam.removeEntry(player.getName());
                        }
                        player.playerListName(null); // Reset tab name
                    });
                }
            } catch (Exception e) {
                // Player not linked or other API error: clean up cache and display
                CACHE.remove(player.getUniqueId());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                    org.bukkit.scoreboard.Team boardTeam = scoreboard.getEntryTeam(player.getName());
                    if (boardTeam != null) {
                        boardTeam.removeEntry(player.getName());
                    }
                    player.playerListName(null);
                });
            }
        });
    }

    private static NamedTextColor getClosestNamedColor(String hexStr) {
        try {
            if (hexStr == null || !hexStr.startsWith("#") || hexStr.length() != 7) {
                return NamedTextColor.WHITE;
            }
            int r = Integer.parseInt(hexStr.substring(1, 3), 16);
            int g = Integer.parseInt(hexStr.substring(3, 5), 16);
            int b = Integer.parseInt(hexStr.substring(5, 7), 16);

            NamedTextColor closest = NamedTextColor.WHITE;
            double minDistance = Double.MAX_VALUE;

            // Standard 16 Minecraft colors with their approximate RGB values
            Map<NamedTextColor, int[]> colorMap = Map.ofEntries(
                Map.entry(NamedTextColor.BLACK, new int[]{0, 0, 0}),
                Map.entry(NamedTextColor.DARK_BLUE, new int[]{0, 0, 170}),
                Map.entry(NamedTextColor.DARK_GREEN, new int[]{0, 170, 0}),
                Map.entry(NamedTextColor.DARK_AQUA, new int[]{0, 170, 170}),
                Map.entry(NamedTextColor.DARK_RED, new int[]{170, 0, 0}),
                Map.entry(NamedTextColor.DARK_PURPLE, new int[]{170, 0, 170}),
                Map.entry(NamedTextColor.GOLD, new int[]{255, 170, 0}),
                Map.entry(NamedTextColor.GRAY, new int[]{170, 170, 170}),
                Map.entry(NamedTextColor.DARK_GRAY, new int[]{85, 85, 85}),
                Map.entry(NamedTextColor.BLUE, new int[]{85, 85, 255}),
                Map.entry(NamedTextColor.GREEN, new int[]{85, 255, 85}),
                Map.entry(NamedTextColor.AQUA, new int[]{85, 255, 255}),
                Map.entry(NamedTextColor.RED, new int[]{255, 85, 85}),
                Map.entry(NamedTextColor.LIGHT_PURPLE, new int[]{255, 85, 255}),
                Map.entry(NamedTextColor.YELLOW, new int[]{255, 255, 85}),
                Map.entry(NamedTextColor.WHITE, new int[]{255, 255, 255})
            );

            for (Map.Entry<NamedTextColor, int[]> entry : colorMap.entrySet()) {
                int[] rgb = entry.getValue();
                double dist = Math.pow(r - rgb[0], 2) + Math.pow(g - rgb[1], 2) + Math.pow(b - rgb[2], 2);
                if (dist < minDistance) {
                    minDistance = dist;
                    closest = entry.getKey();
                }
            }
            return closest;
        } catch (Exception e) {
            return NamedTextColor.WHITE;
        }
    }

    /**
     * Récupère toutes les équipes de l'API, les trie (staff d'abord, puis
     * ordre alphabétique du tag), et ré-enregistre les scoreboard teams
     * dans cet ordre pour un affichage groupé dans le tab.
     */
    public static void orderTeamsInScoreboard(CoreCDI api, JavaPlugin plugin) {
        if (api == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Team> allTeams = api.getTeams();
                if (allTeams == null || allTeams.isEmpty()) return;

                // Trier : staff (staff=1) d'abord, puis ordre alpha du tag
                List<Team> sorted = new ArrayList<>(allTeams);
                sorted.sort((a, b) -> {
                    if (a.staff() != b.staff()) {
                        return a.staff() == 1 ? -1 : 1;
                    }
                    return a.tag().compareToIgnoreCase(b.tag());
                });

                // Sauvegarder les équipes des joueurs en ligne
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

                    // Capturer les CachedTeam des joueurs en ligne avant de supprimer les teams
                    java.util.Map<java.util.UUID, CachedTeam> onlineTeams = new HashMap<>();
                    for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                        CachedTeam ct = CACHE.get(online.getUniqueId());
                        if (ct != null) {
                            onlineTeams.put(online.getUniqueId(), ct);
                        }
                    }

                    // Supprimer toutes les scoreboard teams CDI2 existantes
                    for (org.bukkit.scoreboard.Team bt : scoreboard.getTeams()) {
                        if (bt.getName().startsWith("team_")) {
                            bt.unregister();
                        }
                    }

                    // Recréer dans l'ordre trié
                    for (Team team : sorted) {
                        String teamName = "team_" + team.id();
                        org.bukkit.scoreboard.Team boardTeam = scoreboard.registerNewTeam(teamName);
                        TextColor teamHexColor = TextColor.fromHexString(team.color());
                        NamedTextColor closestNamed = getClosestNamedColor(team.color());
                        boardTeam.prefix(Component.text("[" + team.tag() + "] ", teamHexColor));
                        boardTeam.color(closestNamed);
                    }

                    // Ré-ajouter les joueurs en ligne à leurs équipes et màj tab
                    for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                        CachedTeam ct = onlineTeams.get(online.getUniqueId());
                        if (ct != null) {
                            for (Team team : sorted) {
                                if (team.tag().equals(ct.tag) && team.color().equals(ct.color)) {
                                    String teamName = "team_" + team.id();
                                    org.bukkit.scoreboard.Team boardTeam = scoreboard.getTeam(teamName);
                                    if (boardTeam != null) {
                                        boardTeam.addEntry(online.getName());
                                    }
                                    TextColor hex = TextColor.fromHexString(team.color());
                                    Component tabName = Component.text("[" + team.tag() + "] ", hex)
                                            .append(Component.text(online.getName(), hex));
                                    online.playerListName(tabName);
                                    break;
                                }
                            }
                        }
                    }
                });
            } catch (Exception e) {
                // API inaccessible, on ignore
            }
        });
    }
}
