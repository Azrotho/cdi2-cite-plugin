package fr.citedesiles.citeplugin.util;

import fr.citedesiles.coreplugin.CoreCDI;
import fr.citedesiles.coreplugin.Player;
import fr.citedesiles.coreplugin.Team;
import fr.citedesiles.citeplugin.CitePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeamDisplayManager {

    public static class CachedTeam {
        public final String tag;
        public final String color;
        public final boolean isStaff;
        public final int teamId;

        public CachedTeam(String tag, String color, boolean isStaff, int teamId) {
            this.tag = tag;
            this.color = color;
            this.isStaff = isStaff;
            this.teamId = teamId;
        }
    }

    private static final Map<UUID, CachedTeam> CACHE = new ConcurrentHashMap<>();

    private TeamDisplayManager() {}

    public static CachedTeam getCachedTeam(UUID uuid) {
        return CACHE.get(uuid);
    }

    public static void removeCachedTeam(UUID uuid) {
        CACHE.remove(uuid);
    }

    public static String getScoreboardTeamName(fr.citedesiles.coreplugin.Team team) {
        String prefix = (team.staff() == 1) ? "a_" : "b_";
        return prefix + team.tag().toLowerCase() + "_" + team.id();
    }

    public static String getScoreboardTeamName(String tag, boolean isStaff, int teamId) {
        String prefix = isStaff ? "a_" : "b_";
        return prefix + tag.toLowerCase() + "_" + teamId;
    }

    public static void setupTeamsOnScoreboard(Scoreboard newScoreboard) {
        Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (org.bukkit.scoreboard.Team mainTeam : mainScoreboard.getTeams()) {
            String name = mainTeam.getName();
            if (name.startsWith("a_") || name.startsWith("b_") || name.startsWith("team_")) {
                org.bukkit.scoreboard.Team newTeam = newScoreboard.getTeam(name);
                if (newTeam == null) {
                    newTeam = newScoreboard.registerNewTeam(name);
                }
                newTeam.prefix(mainTeam.prefix());
                TextColor tc = mainTeam.color();
                if (tc instanceof NamedTextColor) {
                    newTeam.color((NamedTextColor) tc);
                }
                newTeam.suffix(mainTeam.suffix());
                newTeam.displayName(mainTeam.displayName());
                newTeam.setAllowFriendlyFire(mainTeam.allowFriendlyFire());
                newTeam.setCanSeeFriendlyInvisibles(mainTeam.canSeeFriendlyInvisibles());
                newTeam.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, mainTeam.getOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE));
                newTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, mainTeam.getOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY));
                
                for (String entry : mainTeam.getEntries()) {
                    newTeam.addEntry(entry);
                }
            }
        }
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
                    CACHE.put(player.getUniqueId(), new CachedTeam(team.tag(), team.color(), team.staff() == 1, team.id()));

                    TextColor teamHexColor = TextColor.fromHexString(team.color());
                    NamedTextColor closestNamed = getClosestNamedColor(team.color());

                    Component scoreboardPrefix = Component.text("[" + team.tag() + "] ", teamHexColor);
                    if (team.staff() == 1) {
                        scoreboardPrefix = scoreboardPrefix.decorate(TextDecoration.BOLD);
                    }
                    Component prefixComp = Component.text("[" + team.tag() + "] ", teamHexColor);
                    Component playerComp = Component.text(player.getName(), teamHexColor);
                    if (team.staff() == 1) {
                        prefixComp = prefixComp.decorate(TextDecoration.BOLD);
                        playerComp = playerComp.decorate(TextDecoration.BOLD);
                    }
                    Component tabName = prefixComp.append(playerComp);
                    final Component finalScoreboardPrefix = scoreboardPrefix;

                    // Run sync to update Scoreboard and Tab
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Set<Scoreboard> scoreboards = new HashSet<>();
                        scoreboards.add(Bukkit.getScoreboardManager().getMainScoreboard());
                        for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                            scoreboards.add(online.getScoreboard());
                        }

                        String teamName = getScoreboardTeamName(team);

                        for (Scoreboard scoreboard : scoreboards) {
                            org.bukkit.scoreboard.Team boardTeam = scoreboard.getTeam(teamName);
                            if (boardTeam == null) {
                                boardTeam = scoreboard.registerNewTeam(teamName);
                            }

                            // Prefix in scoreboard team
                            boardTeam.prefix(finalScoreboardPrefix);
                            boardTeam.color(closestNamed);
                            boardTeam.addEntry(player.getName());
                        }

                        // Tab name
                        player.playerListName(tabName);
                    });
                } else {
                    // No team: remove from cache and scoreboard
                    CACHE.remove(player.getUniqueId());

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Set<Scoreboard> scoreboards = new HashSet<>();
                        scoreboards.add(Bukkit.getScoreboardManager().getMainScoreboard());
                        for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                            scoreboards.add(online.getScoreboard());
                        }

                        for (Scoreboard scoreboard : scoreboards) {
                            org.bukkit.scoreboard.Team boardTeam = scoreboard.getEntryTeam(player.getName());
                            if (boardTeam != null) {
                                boardTeam.removeEntry(player.getName());
                            }
                        }
                        player.playerListName(null); // Reset tab name
                    });
                }
            } catch (Exception e) {
                // Player not linked or other API error: clean up cache and display
                CACHE.remove(player.getUniqueId());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Set<Scoreboard> scoreboards = new HashSet<>();
                    scoreboards.add(Bukkit.getScoreboardManager().getMainScoreboard());
                    for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                        scoreboards.add(online.getScoreboard());
                    }

                    for (Scoreboard scoreboard : scoreboards) {
                        org.bukkit.scoreboard.Team boardTeam = scoreboard.getEntryTeam(player.getName());
                        if (boardTeam != null) {
                            boardTeam.removeEntry(player.getName());
                        }
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
                    Set<Scoreboard> scoreboards = new HashSet<>();
                    scoreboards.add(Bukkit.getScoreboardManager().getMainScoreboard());
                    for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                        scoreboards.add(online.getScoreboard());
                    }

                    // Capturer les CachedTeam des joueurs en ligne avant de supprimer les teams
                    Map<UUID, CachedTeam> onlineTeams = new HashMap<>();
                    for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                        CachedTeam ct = CACHE.get(online.getUniqueId());
                        if (ct != null) {
                            onlineTeams.put(online.getUniqueId(), ct);
                        }
                    }

                    for (Scoreboard sb : scoreboards) {
                        // Supprimer toutes les scoreboard teams CDI2 existantes
                        for (org.bukkit.scoreboard.Team bt : new ArrayList<>(sb.getTeams())) {
                            if (bt.getName().startsWith("a_") || bt.getName().startsWith("b_") || bt.getName().startsWith("team_")) {
                                bt.unregister();
                            }
                        }

                        // Recréer dans l'ordre trié
                        for (Team team : sorted) {
                            String teamName = getScoreboardTeamName(team);
                            org.bukkit.scoreboard.Team boardTeam = sb.registerNewTeam(teamName);
                            TextColor teamHexColor = TextColor.fromHexString(team.color());
                            NamedTextColor closestNamed = getClosestNamedColor(team.color());
                            Component scoreboardPrefix = Component.text("[" + team.tag() + "] ", teamHexColor);
                            if (team.staff() == 1) {
                                scoreboardPrefix = scoreboardPrefix.decorate(TextDecoration.BOLD);
                            }
                            boardTeam.prefix(scoreboardPrefix);
                            boardTeam.color(closestNamed);
                        }
                    }

                    // Ré-ajouter les joueurs en ligne à leurs équipes et màj tab
                    for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                        CachedTeam ct = onlineTeams.get(online.getUniqueId());
                        if (ct != null) {
                            String teamName = getScoreboardTeamName(ct.tag, ct.isStaff, ct.teamId);
                            for (Scoreboard sb : scoreboards) {
                                org.bukkit.scoreboard.Team boardTeam = sb.getTeam(teamName);
                                if (boardTeam != null) {
                                    boardTeam.addEntry(online.getName());
                                }
                            }
                            TextColor hex = TextColor.fromHexString(ct.color);
                            Component prefixComp = Component.text("[" + ct.tag + "] ", hex);
                            Component playerComp = Component.text(online.getName(), hex);
                            if (ct.isStaff) {
                                prefixComp = prefixComp.decorate(TextDecoration.BOLD);
                                playerComp = playerComp.decorate(TextDecoration.BOLD);
                            }
                            Component tabName = prefixComp.append(playerComp);
                            online.playerListName(tabName);
                        }
                    }
                });
            } catch (Exception e) {
                // API inaccessible, on ignore
            }
        });
    }
}
