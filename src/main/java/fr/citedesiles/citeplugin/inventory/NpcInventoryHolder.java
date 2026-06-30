package fr.citedesiles.citeplugin.inventory;

import fr.citedesiles.citeplugin.npc.ConfiguredNpc;
import fr.citedesiles.coreplugin.Item;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NpcInventoryHolder implements InventoryHolder {
    private final ConfiguredNpc npc;
    private final Map<Material, Integer> itemPrices = new HashMap<>();
    private final Map<Material, String> dbMaterialNames = new HashMap<>();
    private List<Item> rawItems;
    private Inventory inventory;

    public NpcInventoryHolder(ConfiguredNpc npc) {
        this.npc = npc;
    }

    public ConfiguredNpc getNpc() {
        return npc;
    }

    public void setPrice(Material material, int price) {
        itemPrices.put(material, price);
    }

    public int getPrice(Material material) {
        return itemPrices.getOrDefault(material, 0);
    }

    public void setDbMaterialName(Material material, String dbName) {
        dbMaterialNames.put(material, dbName);
    }

    public String getDbMaterialName(Material material) {
        return dbMaterialNames.getOrDefault(material, material.name().toLowerCase());
    }

    public List<Item> getRawItems() {
        return rawItems;
    }

    public void setRawItems(List<Item> rawItems) {
        this.rawItems = rawItems;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
