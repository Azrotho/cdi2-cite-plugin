package fr.citedesiles.citeplugin;

import fr.citedesiles.coreplugin.CoreCDI;
import fr.citedesiles.citeplugin.config.PluginConfig;
import fr.citedesiles.citeplugin.listener.ChatListener;
import fr.citedesiles.citeplugin.listener.PlayerJoinListener;
import fr.citedesiles.citeplugin.listener.NpcInteractListener;
import fr.citedesiles.citeplugin.scoreboard.SidebarManager;
import fr.citedesiles.citeplugin.util.TeamDisplayManager;
import fr.citedesiles.citeplugin.npc.NpcRegistry;
import fr.citedesiles.citeplugin.command.NpcAdminCommand;
import fr.citedesiles.citeplugin.leaderboard.LeaderboardManager;
import fr.citedesiles.citeplugin.leaderboard.HeadLeaderboardManager;
import fr.citedesiles.citeplugin.leaderboard.StatFormatters;
import fr.citedesiles.citeplugin.leaderboard.StatLeaderboardManager;
import fr.citedesiles.citeplugin.listener.HeadInteractListener;
import fr.citedesiles.citeplugin.stats.StatBuffer;
import fr.citedesiles.citeplugin.stats.StatsListener;

import de.eisi05.npc.api.NpcApi;
import de.eisi05.npc.api.objects.NpcConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class CitePlugin extends JavaPlugin {

    private PluginConfig config;
    private CoreCDI api;
    private SidebarManager sidebarManager;
    private NpcRegistry npcRegistry;
    private LeaderboardManager leaderboardManager;
    private HeadLeaderboardManager headLeaderboardManager;
    private StatBuffer statBuffer;
    private final List<StatLeaderboardManager> statLeaderboards = new ArrayList<>();

    @Override
    public void onEnable() {
        // Charger la configuration
        config = new PluginConfig(this);

        String apiUrl = config.getApiUrl();
        String apiToken = config.getApiToken();

        if (!apiToken.isEmpty()) {
            // Initialiser le client API
            api = new CoreCDI(apiUrl, apiToken);

            // Tester la connexion à l'API
            try {
                if (api.ping()) {
                    getLogger().info("Connecté à l'API CDI2 : " + apiUrl);
                    TeamDisplayManager.orderTeamsInScoreboard(api, this);
                    sidebarManager = new SidebarManager(api, this, config.getSidebarTitle());
                    sidebarManager.startFooterRotation();
                    sidebarManager.startDataRefresh();

                    // Initialiser et démarrer le classement géant
                    leaderboardManager = new LeaderboardManager(this, api);
                    leaderboardManager.startUpdateTask();

                    // Initialiser et démarrer le classement des têtes secrètes
                    headLeaderboardManager = new HeadLeaderboardManager(this, api);
                    headLeaderboardManager.startUpdateTask();

                    // Initialiser le tracking de stats
                    statBuffer = new StatBuffer(this, api);
                    statBuffer.start();
                    getServer().getPluginManager().registerEvents(new StatsListener(statBuffer), this);

                    // Initialiser les classements secondaires par stats (axe 0, à la suite des PNJ)
                    World world = Bukkit.getWorld("world");
                    if (world != null) {
                        statLeaderboards.add(new StatLeaderboardManager(this, api, "playtime", "TEMPS DE JEU", new Location(world, 90.0, 90.0, 0.0), StatFormatters::playtime));
                        statLeaderboards.add(new StatLeaderboardManager(this, api, "blocks_broken", "BLOCS CASSES", new Location(world, 100.0, 90.0, 0.0), StatFormatters::blocks));
                        statLeaderboards.add(new StatLeaderboardManager(this, api, "distance", "DISTANCE PARCOURUE", new Location(world, 110.0, 90.0, 0.0), StatFormatters::distance));
                        statLeaderboards.add(new StatLeaderboardManager(this, api, "fish", "PECHE", new Location(world, 120.0, 90.0, 0.0), StatFormatters::count));
                        statLeaderboards.add(new StatLeaderboardManager(this, api, "mob_kills", "MOB KILLS", new Location(world, 130.0, 90.0, 0.0), StatFormatters::count));
                        statLeaderboards.add(new StatLeaderboardManager(this, api, "npc_trades", "TRADES PNJ", new Location(world, 140.0, 90.0, 0.0), StatFormatters::count));
                        statLeaderboards.add(new StatLeaderboardManager(this, api, "warden_kills", "WARDEN KILLS", new Location(world, 150.0, 90.0, 0.0), StatFormatters::count));
                    }
                    for (StatLeaderboardManager statLeaderboard : statLeaderboards) {
                        statLeaderboard.startUpdateTask();
                    }
                }
            } catch (CoreCDI.ApiException e) {
                getLogger().warning("Impossible de contacter l'API CDI2 : " + e.getMessage());
            }
        } else {
            getLogger().warning("Le token API est vide ! Configure 'api.token' dans config.yml");
        }

        // Initialiser l'API NPC (ombrée)
        NpcApi.createInstance(this, new NpcConfig().debug(false).autoUpdate(false));

        // Initialiser le registre des PNJs
        npcRegistry = new NpcRegistry(this, config);
        npcRegistry.loadAndSpawnNpcs();

        // Enregistrer les listeners
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(api, config), this);
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
        getServer().getPluginManager().registerEvents(new NpcInteractListener(this, api, npcRegistry), this);
        getServer().getPluginManager().registerEvents(new HeadInteractListener(this, api), this);

        // Enregistrer les commandes
        NpcAdminCommand npcAdminCommand = new NpcAdminCommand(npcRegistry);
        org.bukkit.command.PluginCommand command = getCommand("cite-npc");
        if (command != null) {
            command.setExecutor(npcAdminCommand);
            command.setTabCompleter(npcAdminCommand);
        }

        getLogger().info("CitePlugin activé !");
    }

    @Override
    public void onDisable() {
        // Arrêter proprement le leaderboard
        if (leaderboardManager != null) {
            leaderboardManager.stopUpdateTask();
        }
        if (headLeaderboardManager != null) {
            headLeaderboardManager.stopUpdateTask();
        }
        if (statBuffer != null) {
            statBuffer.stop();
        }
        for (StatLeaderboardManager statLeaderboard : statLeaderboards) {
            statLeaderboard.stopUpdateTask();
        }

        // Désactiver proprement l'API NPC
        NpcApi.disable();
        getLogger().info("CitePlugin désactivé !");
    }

    public PluginConfig getPluginConfig() {
        return config;
    }

    public CoreCDI getApi() {
        return api;
    }

    public SidebarManager getSidebarManager() {
        return sidebarManager;
    }

    public NpcRegistry getNpcRegistry() {
        return npcRegistry;
    }

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public HeadLeaderboardManager getHeadLeaderboardManager() {
        return headLeaderboardManager;
    }

    public StatBuffer getStatBuffer() {
        return statBuffer;
    }
}