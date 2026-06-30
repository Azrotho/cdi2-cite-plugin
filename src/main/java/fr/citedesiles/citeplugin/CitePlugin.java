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

import de.eisi05.npc.api.NpcApi;
import de.eisi05.npc.api.objects.NpcConfig;
import org.bukkit.plugin.java.JavaPlugin;

public class CitePlugin extends JavaPlugin {

    private PluginConfig config;
    private CoreCDI api;
    private SidebarManager sidebarManager;
    private NpcRegistry npcRegistry;

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
}