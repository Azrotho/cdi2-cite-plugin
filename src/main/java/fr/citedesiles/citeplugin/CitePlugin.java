package fr.citedesiles.citeplugin;

import org.bukkit.plugin.java.JavaPlugin;

public class CitePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("CitePlugin a été activé !");
    }

    @Override
    public void onDisable() {
        getLogger().info("CitePlugin a été désactivé !");
    }
}