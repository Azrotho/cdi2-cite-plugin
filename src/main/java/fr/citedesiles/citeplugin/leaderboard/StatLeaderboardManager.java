package fr.citedesiles.citeplugin.leaderboard;

import fr.citedesiles.coreplugin.CoreCDI;
import fr.citedesiles.coreplugin.Team;
import fr.citedesiles.citeplugin.CitePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Classement secondaire affiché par une {@link TextDisplay}, alimenté par une stat de l'API.
 * Suit le même patron que {@link HeadLeaderboardManager} : rafraîchissement toutes les 30 s,
 * fetch asynchrone + mise à jour synchrone de l'entité, déduplication par PersistentDataContainer.
 */
public class StatLeaderboardManager {
    private final CitePlugin plugin;
    private final CoreCDI api;
    private final String statName;
    private final String title;
    private final Location location;
    private final Function<Double, String> formatter;
    private final int limit;
    private BukkitTask task;
    private final NamespacedKey key;

    public StatLeaderboardManager(CitePlugin plugin, CoreCDI api, String statName, String title, Location location, Function<Double, String> formatter) {
        this(plugin, api, statName, title, location, formatter, 10);
    }

    public StatLeaderboardManager(CitePlugin plugin, CoreCDI api, String statName, String title, Location location, Function<Double, String> formatter, int limit) {
        this.plugin = plugin;
        this.api = api;
        this.statName = statName;
        this.title = title;
        this.location = location;
        this.formatter = formatter;
        this.limit = limit;
        this.key = new NamespacedKey(plugin, "stat_leaderboard_" + statName);
    }

    public void startUpdateTask() {
        // Exécuter l'update immédiatement puis toutes les 30 secondes (600 ticks)
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::runUpdate, 0L, 600L);
    }

    public void stopUpdateTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void runUpdate() {
        if (api == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Team> allTeams = api.getTeams();

                List<TeamStatScore> ranked = new ArrayList<>();
                for (Team team : allTeams) {
                    if (team.staff() == 1) continue;
                    double value = api.getTeamStat(team.id(), statName);
                    if (value > 0) {
                        ranked.add(new TeamStatScore(team, value));
                    }
                }
                ranked.sort((a, b) -> Double.compare(b.value, a.value));

                List<TeamStatScore> scores = ranked.size() > limit
                        ? new ArrayList<>(ranked.subList(0, limit))
                        : ranked;

                // Mettre à jour l'entité TextDisplay sur le thread principal
                Bukkit.getScheduler().runTask(plugin, () -> {
                    TextDisplay display = getOrCreateLeaderboardDisplay();
                    if (display != null) {
                        display.text(buildLeaderboardComponent(scores));
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors du chargement du classement " + title + " : " + e.getMessage());
            }
        });
    }

    private TextDisplay getOrCreateLeaderboardDisplay() {
        World world = location.getWorld();
        if (world == null) return null;

        List<TextDisplay> existingDisplays = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(location, 5.0, 5.0, 5.0)) {
            if (entity instanceof TextDisplay) {
                if (entity.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    existingDisplays.add((TextDisplay) entity);
                }
            }
        }

        TextDisplay display;
        if (!existingDisplays.isEmpty()) {
            display = existingDisplays.get(0);
            for (int i = 1; i < existingDisplays.size(); i++) {
                existingDisplays.get(i).remove();
            }
        } else {
            display = world.spawn(location, TextDisplay.class);
            display.getPersistentDataContainer().set(key, PersistentDataType.STRING, "true");
        }

        display.setBillboard(Display.Billboard.FIXED);
        display.setShadowed(true);
        display.setBackgroundColor(Color.fromARGB(100, 0, 0, 0));

        display.setTransformation(new Transformation(
            new Vector3f(0f, 0f, 0f),
            new AxisAngle4f(0f, 0f, 0f, 0f),
            new Vector3f(1.8f, 1.8f, 1.8f),
            new AxisAngle4f(0f, 0f, 0f, 0f)
        ));

        return display;
    }

    private Component buildLeaderboardComponent(List<TeamStatScore> scores) {
        Component titleComponent = Component.text(title, NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD);
        Component separator = Component.text("--------------------------", NamedTextColor.GRAY);

        Component builder = Component.text()
                .append(titleComponent)
                .append(Component.newline())
                .append(separator)
                .append(Component.newline())
                .build();

        int rank = 1;
        for (TeamStatScore score : scores) {
            TextColor teamHexColor = TextColor.fromHexString(score.team.color());
            if (teamHexColor == null) teamHexColor = NamedTextColor.WHITE;

            Component line = Component.text()
                    .append(Component.text(rank + ". ", NamedTextColor.GRAY))
                    .append(Component.text("[" + score.team.tag() + "] ", teamHexColor).decorate(TextDecoration.BOLD))
                    .append(Component.text(score.team.name() + " - ", NamedTextColor.WHITE))
                    .append(Component.text(formatter.apply(score.value), NamedTextColor.AQUA))
                    .append(Component.newline())
                    .build();

            builder = builder.append(line);
            rank++;
        }

        return builder;
    }

    private static class TeamStatScore {
        final Team team;
        final double value;

        TeamStatScore(Team team, double value) {
            this.team = team;
            this.value = value;
        }
    }
}
