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

public class LeaderboardManager {
    private final CitePlugin plugin;
    private final CoreCDI api;
    private BukkitTask task;
    private final NamespacedKey key;

    public LeaderboardManager(CitePlugin plugin, CoreCDI api) {
        this.plugin = plugin;
        this.api = api;
        this.key = new NamespacedKey(plugin, "leaderboard_display");
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

        // Récupérer la liste des équipes et leurs scores de manière asynchrone
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Team> allTeams = api.getTeams();
                List<TeamScore> scores = new ArrayList<>();
                for (Team team : allTeams) {
                    if (team.staff() != 1) {
                        double money = api.getTeamMoney(team.id());
                        scores.add(new TeamScore(team, money));
                    }
                }

                // Trier par montant décroissant
                scores.sort((a, b) -> Double.compare(b.money, a.money));

                // Mettre à jour l'entité TextDisplay sur le thread principal
                Bukkit.getScheduler().runTask(plugin, () -> {
                    TextDisplay display = getOrCreateLeaderboardDisplay();
                    if (display != null) {
                        display.text(buildLeaderboardComponent(scores));
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors du chargement du classement : " + e.getMessage());
            }
        });
    }

    private TextDisplay getOrCreateLeaderboardDisplay() {
        World world = Bukkit.getWorld("world");
        if (world == null) return null;

        Location loc = new Location(world, 30.0, 90.0, 0.0);
        
        List<TextDisplay> existingDisplays = new ArrayList<>();
        // Rechercher si le TextDisplay existe déjà via les métadonnées persistantes (rayon de 5 blocs pour attraper les doublons)
        for (Entity entity : world.getNearbyEntities(loc, 5.0, 5.0, 5.0)) {
            if (entity instanceof TextDisplay) {
                if (entity.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    existingDisplays.add((TextDisplay) entity);
                }
            }
        }

        TextDisplay display;
        if (!existingDisplays.isEmpty()) {
            // Conserver la première entité
            display = existingDisplays.get(0);
            
            // Tuer/supprimer toutes les entités en double
            for (int i = 1; i < existingDisplays.size(); i++) {
                existingDisplays.get(i).remove();
            }
        } else {
            // Faire apparaître un nouveau TextDisplay si aucun n'existe
            display = world.spawn(loc, TextDisplay.class);
            display.getPersistentDataContainer().set(key, PersistentDataType.STRING, "true");
        }
        
        // Enforcer les propriétés visuelles (pour mise à jour et nouveau spawn)
        display.setBillboard(Display.Billboard.FIXED);
        display.setShadowed(true);
        display.setBackgroundColor(org.bukkit.Color.fromARGB(100, 0, 0, 0));
        
        // Augmenter l'échelle à 1.8x pour la visibilité
        display.setTransformation(new Transformation(
            new Vector3f(0f, 0f, 0f),
            new AxisAngle4f(0f, 0f, 0f, 0f),
            new Vector3f(1.8f, 1.8f, 1.8f),
            new AxisAngle4f(0f, 0f, 0f, 0f)
        ));

        return display;
    }

    private Component buildLeaderboardComponent(List<TeamScore> scores) {
        Component title = Component.text("CLASSEMENT DE LA CITE", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD);
        Component separator = Component.text("--------------------------", NamedTextColor.GRAY);

        Component builder = Component.text()
                .append(title)
                .append(Component.newline())
                .append(separator)
                .append(Component.newline())
                .build();

        int rank = 1;
        for (TeamScore score : scores) {
            TextColor teamHexColor = TextColor.fromHexString(score.team.color());
            if (teamHexColor == null) teamHexColor = NamedTextColor.WHITE;

            Component line = Component.text()
                    .append(Component.text(rank + ". ", NamedTextColor.GRAY))
                    .append(Component.text("[" + score.team.tag() + "] ", teamHexColor).decorate(TextDecoration.BOLD))
                    .append(Component.text(score.team.name() + " - ", NamedTextColor.WHITE))
                    .append(Component.text(formatStars(score.money) + " ⭐", NamedTextColor.YELLOW))
                    .append(Component.newline())
                    .build();
            
            builder = builder.append(line);
            rank++;
        }

        return builder;
    }

    private static String formatStars(double amount) {
        long rounded = Math.round(amount);
        String num = String.valueOf(rounded);
        StringBuilder sb = new StringBuilder();
        int len = num.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                sb.append('.');
            }
            sb.append(num.charAt(i));
        }
        return sb.toString();
    }

    private static class TeamScore {
        final Team team;
        final double money;

        TeamScore(Team team, double money) {
            this.team = team;
            this.money = money;
        }
    }
}
