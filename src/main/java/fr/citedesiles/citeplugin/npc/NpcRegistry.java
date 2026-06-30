package fr.citedesiles.citeplugin.npc;

import fr.citedesiles.citeplugin.config.PluginConfig;
import de.eisi05.npc.api.objects.NPC;
import de.eisi05.npc.api.objects.NpcName;
import de.eisi05.npc.api.objects.NpcOption;
import de.eisi05.npc.api.objects.NpcSkin;
import de.eisi05.npc.api.objects.Skin;
import de.eisi05.npc.api.manager.NpcManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class NpcRegistry {
    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final Map<UUID, ConfiguredNpc> configuredNpcMap = new HashMap<>();

    public NpcRegistry(JavaPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void loadAndSpawnNpcs() {
        configuredNpcMap.clear();
        List<ConfiguredNpc> list = config.loadNpcs();

        for (ConfiguredNpc cNpc : list) {
            configuredNpcMap.put(cNpc.getUuid(), cNpc);

            if (cNpc.getLocation() == null) {
                plugin.getLogger().warning("Le PNJ " + cNpc.getId() + " n'a pas de localisation configurée.");
                continue;
            }

            Optional<NPC> optNpc = NpcManager.fromUUID(cNpc.getUuid());
            NPC npc;
            if (optNpc.isPresent()) {
                npc = optNpc.get();
                // Mettre à jour le PNJ existant
                npc.setLocation(cNpc.getLocation());
                npc.setName(NpcName.of(LegacyComponentSerializer.legacyAmpersand().deserialize(cNpc.getName())));
            } else {
                // Créer un nouveau PNJ
                npc = new NPC(cNpc.getLocation(), cNpc.getUuid(), NpcName.of(LegacyComponentSerializer.legacyAmpersand().deserialize(cNpc.getName())));
            }

            // Options de comportement
            npc.setOption(NpcOption.LOOK_AT_PLAYER, 6.0);
            npc.setOption(NpcOption.SHOW_TAB_LIST, false);

            // Appliquer le skin personnalisé si configuré
            if (cNpc.getSkinValue() != null && !cNpc.getSkinValue().isEmpty()) {
                Skin skin = new Skin(null, cNpc.getSkinValue(), cNpc.getSkinSignature() != null ? cNpc.getSkinSignature() : "");
                npc.setOption(NpcOption.SKIN, NpcSkin.of(skin));
            }

            npc.setEnabled(true);
            npc.showNpcToAllPlayers();
            try {
                npc.save();
            } catch (Exception e) {
                plugin.getLogger().warning("Impossible de sauvegarder le PNJ " + cNpc.getId() + ": " + e.getMessage());
            }
        }
    }

    public ConfiguredNpc getConfiguredNpc(UUID uuid) {
        return configuredNpcMap.get(uuid);
    }

    public Collection<ConfiguredNpc> getConfiguredNpcs() {
        return configuredNpcMap.values();
    }

    public void updateNpcLocation(String id, Location loc) {
        for (ConfiguredNpc cNpc : configuredNpcMap.values()) {
            if (cNpc.getId().equalsIgnoreCase(id)) {
                cNpc.setLocation(loc);

                Optional<NPC> optNpc = NpcManager.fromUUID(cNpc.getUuid());
                if (optNpc.isPresent()) {
                    NPC npc = optNpc.get();
                    npc.setLocation(loc);
                    npc.reload();
                    try {
                        npc.save();
                    } catch (Exception e) {
                        plugin.getLogger().warning("Impossible de sauvegarder le PNJ " + cNpc.getId() + ": " + e.getMessage());
                    }
                } else {
                    NPC npc = new NPC(loc, cNpc.getUuid(), NpcName.of(LegacyComponentSerializer.legacyAmpersand().deserialize(cNpc.getName())));
                    npc.setOption(NpcOption.LOOK_AT_PLAYER, 6.0);
                    npc.setOption(NpcOption.SHOW_TAB_LIST, false);
                    if (cNpc.getSkinValue() != null && !cNpc.getSkinValue().isEmpty()) {
                        Skin skin = new Skin(null, cNpc.getSkinValue(), cNpc.getSkinSignature() != null ? cNpc.getSkinSignature() : "");
                        npc.setOption(NpcOption.SKIN, NpcSkin.of(skin));
                    }
                    npc.setEnabled(true);
                    npc.showNpcToAllPlayers();
                    try {
                        npc.save();
                    } catch (Exception e) {
                        plugin.getLogger().warning("Impossible de sauvegarder le PNJ " + cNpc.getId() + ": " + e.getMessage());
                    }
                }

                // Sauvegarder la liste mise à jour dans config.yml
                config.saveNpcs(new ArrayList<>(configuredNpcMap.values()));
                break;
            }
        }
    }
}
