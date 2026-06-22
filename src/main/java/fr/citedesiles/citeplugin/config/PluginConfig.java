package fr.citedesiles.citeplugin.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Wrapper pour la configuration config.yml du plugin cite.
 */
public class PluginConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    // --- API ---

    public String getApiUrl() {
        return config.getString("api.url", "http://localhost:3000");
    }

    public String getApiToken() {
        return config.getString("api.token", "");
    }

    // --- Messages ---

    public String getPrefix() {
        return col(config.getString("messages.prefix", "&8[&bCDI2&8]&r"));
    }

    public String getSidebarTitle() {
        return col(config.getString("messages.sidebar-title", "CDI2"));
    }

    // --- Interne ---

    private String msg(String path) {
        return col(config.getString(path, ""));
    }

    /**
     * Traduit les codes couleur & en vraies couleurs Minecraft.
     */
    private static String col(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
