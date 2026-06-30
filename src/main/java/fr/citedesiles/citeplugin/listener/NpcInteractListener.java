package fr.citedesiles.citeplugin.listener;

import fr.citedesiles.coreplugin.CoreCDI;
import fr.citedesiles.coreplugin.Item;
import fr.citedesiles.coreplugin.NPCItem;
import fr.citedesiles.citeplugin.inventory.NpcInventoryHolder;
import fr.citedesiles.citeplugin.npc.ConfiguredNpc;
import fr.citedesiles.citeplugin.npc.NpcRegistry;
import de.eisi05.npc.api.events.NpcInteractEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NpcInteractListener implements Listener {
    private final JavaPlugin plugin;
    private final CoreCDI api;
    private final NpcRegistry registry;

    private final Map<UUID, Long> clickCooldowns = new ConcurrentHashMap<>();
    private final Map<String, List<Item>> cachedNpcItems = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 5000; // 5 secondes de cache

    public NpcInteractListener(JavaPlugin plugin, CoreCDI api, NpcRegistry registry) {
        this.plugin = plugin;
        this.api = api;
        this.registry = registry;
    }

    @EventHandler
    public void onNpcInteract(NpcInteractEvent event) {
        Player player = event.getPlayer();
        de.eisi05.npc.api.objects.NPC apiNpc = event.getNpc();
        ConfiguredNpc cNpc = registry.getConfiguredNpc(apiNpc.getUUID());
        if (cNpc == null) return;

        // Anti-spam 1 seconde par joueur
        long now = System.currentTimeMillis();
        long lastClick = clickCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastClick < 1000) {
            return;
        }
        clickCooldowns.put(player.getUniqueId(), now);

        if (cNpc.getType().equalsIgnoreCase("message")) {
            String message = cNpc.getMessage();
            if (message == null || message.isEmpty()) {
                message = "<white>Bonjour j'aime les </white><#ffb3c1>sushis</#ffb3c1>";
            }
            player.sendMessage(MiniMessage.miniMessage().deserialize(message));
        } else if (cNpc.getType().equalsIgnoreCase("economy")) {
            if (api == null) {
                player.sendMessage(Component.text("§cLe client API CDI2 n'est pas disponible.", NamedTextColor.RED));
                return;
            }

            String npcId = cNpc.getId();
            long cacheTime = cacheTimestamps.getOrDefault(npcId, 0L);
            List<Item> cachedItems = cachedNpcItems.get(npcId);

            if (cachedItems != null && (now - cacheTime < CACHE_DURATION_MS)) {
                openMarketInventory(player, cNpc, cachedItems);
                return;
            }

            player.sendMessage(Component.text("§eChargement des prix du marché...", NamedTextColor.YELLOW));

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    // Charger les items associés au PNJ
                    fr.citedesiles.coreplugin.NPC apiNpcData = api.getEconomyNPC(cNpc.getId());
                    // Charger tous les items du serveur CDI2
                    List<Item> allEconomyItems = api.getEconomyItems();

                    Map<String, Item> economyItemMap = new HashMap<>();
                    for (Item item : allEconomyItems) {
                        economyItemMap.put(item.material().toUpperCase(), item);
                    }

                    List<Item> resolvedItems = new ArrayList<>();
                    for (NPCItem npcItem : apiNpcData.items()) {
                        Item matchedItem = economyItemMap.get(npcItem.material().toUpperCase());
                        if (matchedItem != null) {
                            resolvedItems.add(matchedItem);
                        } else {
                            resolvedItems.add(new Item(npcItem.material(), 0, 0, 0, 0));
                        }
                    }

                    // Enregistrer dans le cache temporaire
                    cachedNpcItems.put(npcId, resolvedItems);
                    cacheTimestamps.put(npcId, System.currentTimeMillis());

                    // Ouvrir l'inventaire en synchrone
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        openMarketInventory(player, cNpc, resolvedItems);
                    });
                } catch (Exception e) {
                    plugin.getLogger().warning("Impossible de charger les prix pour " + cNpc.getId() + ": " + e.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(Component.text("§cImpossible de contacter le marché CDI2. Réessayez plus tard.", NamedTextColor.RED));
                    });
                }
            });
        }
    }

    private void openMarketInventory(Player player, ConfiguredNpc cNpc, List<Item> items) {
        Component title = Component.text("Marché : ").append(
            LegacyComponentSerializer.legacySection().deserialize(cNpc.getName())
        );

        int size = ((items.size() + 8) / 9) * 9;
        if (size == 0) size = 9;
        if (size > 54) size = 54;

        NpcInventoryHolder holder = new NpcInventoryHolder(cNpc);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        for (int i = 0; i < Math.min(items.size(), size); i++) {
            Item item = items.get(i);
            Material material = Material.matchMaterial(item.material());
            if (material == null) {
                material = Material.BARRIER;
            }

            ItemStack itemStack = new ItemStack(material);
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(formatMaterialName(item.material()), NamedTextColor.GREEN));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("§7Prix actuel : §e" + formatStars(item.currentPrice()) + " §e⭐"));
                meta.lore(lore);
                itemStack.setItemMeta(meta);
            }
            inv.setItem(i, itemStack);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof NpcInventoryHolder) {
            event.setCancelled(true);
        }
    }

    private String formatStars(double amount) {
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

    private String formatMaterialName(String materialName) {
        if (materialName == null) return "";
        String[] parts = materialName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.length() > 0) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1))
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }
}
