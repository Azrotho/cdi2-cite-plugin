package fr.citedesiles.citeplugin.config;

import fr.citedesiles.citeplugin.npc.ConfiguredNpc;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public List<ConfiguredNpc> loadNpcs() {
        List<ConfiguredNpc> list = new ArrayList<>();
        List<Map<?, ?>> maps = config.getMapList("npcs");
        if (maps == null) return list;

        for (Map<?, ?> map : maps) {
            String id = (String) map.get("id");
            String type = (String) map.get("type");
            String name = (String) map.get("name");
            String uuidStr = (String) map.get("uuid");
            if (id == null || type == null || uuidStr == null) continue;

            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }

            String skinValue = "";
            String skinSignature = "";
            Object skinObj = map.get("skin");
            if (skinObj instanceof Map) {
                Map<?, ?> skinMap = (Map<?, ?>) skinObj;
                skinValue = (String) skinMap.get("value");
                skinSignature = (String) skinMap.get("signature");
            }

            String message = (String) map.get("message");

            // Location
            Location loc = null;
            Object locObj = map.get("location");
            if (locObj instanceof Map) {
                Map<?, ?> locMap = (Map<?, ?>) locObj;
                String worldName = (String) locMap.get("world");
                Number xNum = (Number) locMap.get("x");
                Number yNum = (Number) locMap.get("y");
                Number zNum = (Number) locMap.get("z");
                Number yawNum = (Number) locMap.get("yaw");
                Number pitchNum = (Number) locMap.get("pitch");

                if (worldName != null && xNum != null && yNum != null && zNum != null) {
                    org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                    if (world == null && !org.bukkit.Bukkit.getWorlds().isEmpty()) {
                        world = org.bukkit.Bukkit.getWorlds().get(0);
                    }
                    float yaw = yawNum != null ? yawNum.floatValue() : 0.0f;
                    float pitch = pitchNum != null ? pitchNum.floatValue() : 0.0f;
                    if (world != null) {
                        loc = new Location(world, xNum.doubleValue(), yNum.doubleValue(), zNum.doubleValue(), yaw, pitch);
                    }
                }
            }

            list.add(new ConfiguredNpc(id, type, name, uuid, skinValue, skinSignature, message, loc));
        }
        return list;
    }

    public void saveNpcs(List<ConfiguredNpc> npcs) {
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (ConfiguredNpc npc : npcs) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", npc.getId());
            map.put("type", npc.getType());
            map.put("name", npc.getName());
            map.put("uuid", npc.getUuid().toString());

            Map<String, String> skinMap = new LinkedHashMap<>();
            skinMap.put("value", npc.getSkinValue() != null ? npc.getSkinValue() : "");
            skinMap.put("signature", npc.getSkinSignature() != null ? npc.getSkinSignature() : "");
            map.put("skin", skinMap);

            if (npc.getMessage() != null) {
                map.put("message", npc.getMessage());
            }

            if (npc.getLocation() != null) {
                Map<String, Object> locMap = new LinkedHashMap<>();
                locMap.put("world", npc.getLocation().getWorld() != null ? npc.getLocation().getWorld().getName() : "world");
                locMap.put("x", npc.getLocation().getX());
                locMap.put("y", npc.getLocation().getY());
                locMap.put("z", npc.getLocation().getZ());
                locMap.put("yaw", (double) npc.getLocation().getYaw());
                locMap.put("pitch", (double) npc.getLocation().getPitch());
                map.put("location", locMap);
            }
            mapList.add(map);
        }
        config.set("npcs", mapList);
        plugin.saveConfig();
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
