package fr.citedesiles.citeplugin.inventory;

import fr.citedesiles.citeplugin.npc.ConfiguredNpc;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class NpcInventoryHolder implements InventoryHolder {
    private final ConfiguredNpc npc;
    private Inventory inventory;

    public NpcInventoryHolder(ConfiguredNpc npc) {
        this.npc = npc;
    }

    public ConfiguredNpc getNpc() {
        return npc;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
