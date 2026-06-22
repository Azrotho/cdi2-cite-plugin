package fr.citedesiles.citeplugin.scoreboard;

import fr.citedesiles.coreplugin.CoreCDI;
import fr.citedesiles.coreplugin.Team;
import fr.citedesiles.citeplugin.util.TeamDisplayManager;
import fr.citedesiles.citeplugin.util.TeamDisplayManager.CachedTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère le scoreboard sidebar par joueur pour le plugin cite.
 * Affiche : pseudo, équipe, étoiles (solde équipe), et un footer rotatif.
 */
public class SidebarManager {

    private final CoreCDI api;
    private final JavaPlugin plugin;
    private final String title;

    private final Map<UUID, Scoreboard> playerScoreboards = new ConcurrentHashMap<>();

    // Footer rotation
    private int footerIndex = 0;
    private static final String[] FOOTER_TEXTS = {"polycube.fr", "cripieclub.fr", "azrotho.fr"};
    private static final String[] FOOTER_HEX = {"#646fd4", "#00ff00", "#ff5555"};

    public SidebarManager(CoreCDI api, JavaPlugin plugin, String title) {
        this.api = api;
        this.plugin = plugin;
        this.title = title;
    }

    /**
     * Démarre la rotation périodique du footer (toutes les 30 secondes).
     */
    public void startFooterRotation() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::rotateFooter, 0L, 600L); // 30s = 600 ticks
    }

    /**
     * Démarre le rafraîchissement périodique des données (toutes les 60 secondes).
     */
    public void startDataRefresh() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                refreshPlayerData(player);
            }
        }, 1200L, 1200L); // 60s = 1200 ticks
    }

    /**
     * Crée le scoreboard d'un joueur et lance la récupération async de ses données.
     */
    public void createScoreboard(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Component titleComp = title.contains("§") || title.contains("&")
                ? LegacyComponentSerializer.legacySection().deserialize(title)
                : Component.text(title, NamedTextColor.GOLD);
        Objective objective = scoreboard.registerNewObjective(
                "cdi2_sidebar",
                "dummy",
                titleComp
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        player.setScoreboard(scoreboard);
        playerScoreboards.put(player.getUniqueId(), scoreboard);

        // Lignes initiales (placeholders)
        setLine(player, Component.text("Joueur", NamedTextColor.GRAY), 14);
        setLine(player, Component.text(player.getName(), NamedTextColor.WHITE), 13);
        setLine(player, Component.empty(), 12);
        setLine(player, Component.text("Équipe", NamedTextColor.GRAY), 11);
        setLine(player, Component.text("...", NamedTextColor.GRAY), 10);
        setLine(player, Component.empty(), 9);
        setLine(player, Component.text("Étoiles", NamedTextColor.GRAY), 8);
        setLine(player, Component.text("...", NamedTextColor.YELLOW), 7);
        setLine(player, Component.empty(), 6);
        setLine(player, getFooterEntry(), 5);

        // Lancer la récupération async des données réelles
        refreshPlayerData(player);
    }

    /**
     * Rafraîchit les données équipe/étoiles d'un joueur (async API + sync update).
     */
    public void refreshPlayerData(Player player) {
        if (api == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                fr.citedesiles.coreplugin.Player apiPlayer = api.getPlayer(player.getUniqueId().toString());
                if (apiPlayer.team() != -1) {
                    Team team = api.getTeam(apiPlayer.team());
                    double money = api.getTeamMoney(apiPlayer.team());

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        updatePlayerLines(player, team, money);
                    });
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        updatePlayerLines(player, null, 0);
                    });
                }
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    updatePlayerLines(player, null, 0);
                });
            }
        });
    }

    /**
     * Met à jour les lignes équipe et étoiles du scoreboard d'un joueur.
     */
    private void updatePlayerLines(Player player, Team team, double money) {
        if (!playerScoreboards.containsKey(player.getUniqueId())) return;

        if (team != null) {
            TextColor teamColor = TextColor.fromHexString(team.color());
            if (teamColor == null) teamColor = NamedTextColor.YELLOW;

            setLine(player, Component.text(player.getName(), NamedTextColor.WHITE), 13);
            Component teamNameComp = Component.text(team.name(), teamColor);
            if (team.staff() == 1) {
                teamNameComp = teamNameComp.decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
            }
            setLine(player, teamNameComp, 10);
        } else {
            setLine(player, Component.text(player.getName(), NamedTextColor.WHITE), 13);
            setLine(player, Component.text("Aucune", NamedTextColor.YELLOW), 10);
        }

        setLine(player, Component.text(formatStars(money), NamedTextColor.YELLOW), 7);
        setLine(player, getFooterEntry(), 5);
    }

    /**
     * Fait tourner le footer pour tous les joueurs en ligne.
     */
    private void rotateFooter() {
        footerIndex = (footerIndex + 1) % FOOTER_TEXTS.length;
        Component footerEntry = getFooterEntry();

        for (Player player : Bukkit.getOnlinePlayers()) {
            setLine(player, footerEntry, 5);
        }
    }

    private Component getFooterEntry() {
        TextColor color = TextColor.fromHexString(FOOTER_HEX[footerIndex]);
        if (color == null) color = NamedTextColor.WHITE;
        return Component.text(FOOTER_TEXTS[footerIndex], color);
    }

    /**
     * Formate un nombre avec des points comme séparateur de milliers (style français).
     */
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

    /**
     * Définit un score pour un joueur à une ligne spécifique en utilisant un Component de texte personnalisé.
     */
    private void setLine(Player player, Component textComponent, int score) {
        Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
        if (scoreboard == null) return;
        Objective objective = scoreboard.getObjective("cdi2_sidebar");
        if (objective == null) return;

        // Utilise une entrée invisible et constante par score pour éviter tout conflit ou doublon.
        String entry = "§" + Integer.toHexString(score);
        org.bukkit.scoreboard.Score scoreObj = objective.getScore(entry);
        scoreObj.setScore(score);
        scoreObj.customName(textComponent);
    }

    /**
     * Nettoie le scoreboard d'un joueur qui se déconnecte.
     */
    public void removePlayer(Player player) {
        playerScoreboards.remove(player.getUniqueId());
    }
}
