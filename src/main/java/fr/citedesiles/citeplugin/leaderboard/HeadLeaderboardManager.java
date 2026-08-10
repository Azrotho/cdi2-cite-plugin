package fr.citedesiles.citeplugin.leaderboard;

import fr.citedesiles.coreplugin.CoreCDI;
import fr.citedesiles.coreplugin.Team;
import fr.citedesiles.citeplugin.CitePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
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
import java.util.Map;

public class HeadLeaderboardManager {
    private final CitePlugin plugin;
    private final CoreCDI api;
    private BukkitTask task;
    private final NamespacedKey key;

    public HeadLeaderboardManager(CitePlugin plugin, CoreCDI api) {
        this.plugin = plugin;
        this.api = api;
        this.key = new NamespacedKey(plugin, "head_leaderboard_display");
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
                Map<Integer, Integer> headCounts = api.getHeadCounts();

                List<TeamHeadScore> scores = new ArrayList<>();
                for (Team team : allTeams) {
                    if (team.staff() != 1) {
                        int count = headCounts.getOrDefault(team.id(), 0);
                        scores.add(new TeamHeadScore(team, count));
                    }
                }

                // Trier par nombre de têtes décroissant
                scores.sort((a, b) -> Integer.compare(b.count, a.count));

                // Mettre à jour l'entité TextDisplay sur le thread principal
                Bukkit.getScheduler().runTask(plugin, () -> {
                    TextDisplay display = getOrCreateLeaderboardDisplay();
                    if (display != null) {
                        display.text(buildLeaderboardComponent(scores));
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors du chargement du classement des têtes : " + e.getMessage());
            }
        });
    }

    private TextDisplay getOrCreateLeaderboardDisplay() {
        World world = Bukkit.getWorld("world");
        if (world == null) return null;

        Location loc = new Location(world, 150.0, 90.0, 0.0);

        List<TextDisplay> existingDisplays = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(loc, 5.0, 5.0, 5.0)) {
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
            display = world.spawn(loc, TextDisplay.class);
            display.getPersistentDataContainer().set(key, PersistentDataType.STRING, "true");
        }

        display.setBillboard(Display.Billboard.FIXED);
        display.setShadowed(true);
        display.setBackgroundColor(org.bukkit.Color.fromARGB(100, 0, 0, 0));

        display.setTransformation(new Transformation(
            new Vector3f(0f, 0f, 0f),
            new AxisAngle4f(0f, 0f, 0f, 0f),
            new Vector3f(1.8f, 1.8f, 1.8f),
            new AxisAngle4f(0f, 0f, 0f, 0f)
        ));

        return display;
    }

    private Component buildLeaderboardComponent(List<TeamHeadScore> scores) {
        Component title = Component.text("CLASSEMENT DES TETES", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD);
        Component separator = Component.text("--------------------------", NamedTextColor.GRAY);

        Component builder = Component.text()
                .append(title)
                .append(Component.newline())
                .append(separator)
                .append(Component.newline())
                .build();

        int rank = 1;
        for (TeamHeadScore score : scores) {
            TextColor teamHexColor = TextColor.fromHexString(score.team.color());
            if (teamHexColor == null) teamHexColor = NamedTextColor.WHITE;

            Component line = Component.text()
                    .append(Component.text(rank + ". ", NamedTextColor.GRAY))
                    .append(Component.text("[" + score.team.tag() + "] ", teamHexColor).decorate(TextDecoration.BOLD))
                    .append(Component.text(score.team.name() + " - ", NamedTextColor.WHITE))
                    .append(Component.text(score.count + " tête" + (score.count > 1 ? "s" : ""), NamedTextColor.AQUA))
                    .append(Component.newline())
                    .build();

            builder = builder.append(line);
            rank++;
        }

        return builder;
    }

    private static class TeamHeadScore {
        final Team team;
        final int count;

        TeamHeadScore(Team team, int count) {
            this.team = team;
            this.count = count;
        }
    }
}
