package fr.citedesiles.citeplugin.npc;

import org.bukkit.Location;
import java.util.UUID;

public class ConfiguredNpc {
    private final String id;
    private final String type;
    private final String name;
    private final UUID uuid;
    private final String skinValue;
    private final String skinSignature;
    private final String message;
    private Location location;

    public ConfiguredNpc(String id, String type, String name, UUID uuid, String skinValue, String skinSignature, String message, Location location) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.uuid = uuid;
        this.skinValue = skinValue;
        this.skinSignature = skinSignature;
        this.message = message;
        this.location = location;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getSkinValue() {
        return skinValue;
    }

    public String getSkinSignature() {
        return skinSignature;
    }

    public String getMessage() {
        return message;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }
}
