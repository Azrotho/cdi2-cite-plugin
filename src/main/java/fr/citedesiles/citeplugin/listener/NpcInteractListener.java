package fr.citedesiles.citeplugin.listener;

import fr.citedesiles.citeplugin.CitePlugin;
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
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NpcInteractListener implements Listener {
    private final CitePlugin plugin;
    private final CoreCDI api;
    private final NpcRegistry registry;

    private final Map<UUID, Long> clickCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> inventoryClickCooldowns = new ConcurrentHashMap<>();
    private final Map<String, List<Item>> cachedNpcItems = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 5000; // 5 secondes de cache

    public NpcInteractListener(CitePlugin plugin, CoreCDI api, NpcRegistry registry) {
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
            LegacyComponentSerializer.legacyAmpersand().deserialize(cNpc.getName())
        );

        int size = ((items.size() + 8) / 9) * 9;
        if (size == 0) size = 9;
        if (size > 54) size = 54;

        NpcInventoryHolder holder = new NpcInventoryHolder(cNpc);
        holder.setRawItems(items);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        // Générer le contenu initial du menu
        updateMarketInventory(player, inv, cNpc, items);

        player.openInventory(inv);
    }

    private void updateMarketInventory(Player player, Inventory inv, ConfiguredNpc cNpc, List<Item> items) {
        NpcInventoryHolder holder = (NpcInventoryHolder) inv.getHolder();
        if (holder == null) return;

        for (int i = 0; i < Math.min(items.size(), inv.getSize()); i++) {
            Item item = items.get(i);
            Material material = Material.matchMaterial(item.material());
            if (material == null) {
                material = Material.BARRIER;
            }

            // Enregistrer le prix dans le holder
            holder.setPrice(material, item.currentPrice());
            // Enregistrer le nom de material original de la BD dans le holder
            holder.setDbMaterialName(material, item.material());

            ItemStack itemStack = inv.getItem(i);
            if (itemStack == null || itemStack.getType() != material) {
                itemStack = new ItemStack(material);
            }

            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(formatMaterialName(item.material()), NamedTextColor.GREEN));

                int count = countItems(player, material);

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("§7Clic gauche : Vendre 1 pour §e" + formatStars(item.currentPrice()) + " §e⭐"));
                lore.add(Component.text("§7Clic droit : Vendre 64 pour §e" + formatStars(item.currentPrice() * 64) + " §e⭐"));
                lore.add(Component.text("§7Maj-Clic : Tout vendre (§a" + count + "§7) pour §e" + formatStars((double) item.currentPrice() * count) + " §e⭐"));
                meta.lore(lore);
                itemStack.setItemMeta(meta);
            }
            inv.setItem(i, itemStack);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof NpcInventoryHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        // Anti-spam clic inventaire (250 ms)
        long now = System.currentTimeMillis();
        long lastClick = inventoryClickCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastClick < 250) {
            return;
        }
        inventoryClickCooldowns.put(player.getUniqueId(), now);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR || clickedItem.getType() == Material.BARRIER) {
            return;
        }

        NpcInventoryHolder holder = (NpcInventoryHolder) event.getInventory().getHolder();
        ConfiguredNpc cNpc = holder.getNpc();
        Material material = clickedItem.getType();
        int price = holder.getPrice(material);

        if (price <= 0) {
            player.sendMessage(Component.text("§cImpossible de vendre cet objet pour le moment (prix invalide).", NamedTextColor.RED));
            return;
        }

        int playerHas = countItems(player, material);
        int quantityToSell = 0;

        if (event.isShiftClick()) {
            quantityToSell = playerHas;
        } else if (event.isLeftClick()) {
            quantityToSell = 1;
        } else if (event.isRightClick()) {
            quantityToSell = 64;
        }

        if (quantityToSell <= 0) {
            return;
        }

        // Si le joueur a moins de ressources que demandé (ex: clic droit 64 mais il en a 10), on vend tout ce qu'il a
        if (playerHas < quantityToSell) {
            if (event.isLeftClick() || event.isRightClick()) {
                quantityToSell = playerHas;
            }
        }

        if (quantityToSell <= 0) {
            player.sendMessage(Component.text("§cVous n'avez pas cet objet dans votre inventaire.", NamedTextColor.RED));
            return;
        }

        final int finalQuantity = quantityToSell;
        final double totalValue = (double) price * finalQuantity;
        final String dbMaterialName = holder.getDbMaterialName(material);

        // 1. Retirer les items de l'inventaire en synchrone (thread principal)
        removeItems(player, material, finalQuantity);

        // 2. Mettre à jour visuellement le menu en temps réel
        updateMarketInventory(player, event.getInventory(), cNpc, holder.getRawItems());

        player.sendMessage(Component.text("§eTraitement de la vente de " + finalQuantity + " x " + formatMaterialName(material.name()) + "...", NamedTextColor.YELLOW));

        // 3. API Transaction (Asynchrone)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Récupérer l'équipe du joueur
                fr.citedesiles.coreplugin.Player apiPlayer = api.getPlayer(player.getUniqueId().toString());
                int teamId = apiPlayer.team();
                if (teamId == -1) {
                    throw new IllegalStateException("Vous n'appartenez à aucune équipe. Les ventes requièrent une équipe.");
                }

                // Créer la transaction sur le serveur API (avec le nom exact de la base de données)
                api.createTransaction(teamId, player.getUniqueId().toString(), totalValue, dbMaterialName, finalQuantity);

                // Succès de la transaction
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("§aVendu " + finalQuantity + " x " + formatMaterialName(material.name()) + " pour " + formatStars(totalValue) + " ⭐ !", NamedTextColor.GREEN));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

                    // Rafraîchir de nouveau le menu pour synchroniser
                    updateMarketInventory(player, event.getInventory(), cNpc, holder.getRawItems());

                    // Rafraîchir instantanément le scoreboard de toute l'équipe en ligne
                    if (plugin.getSidebarManager() != null) {
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            fr.citedesiles.citeplugin.util.TeamDisplayManager.CachedTeam ct = fr.citedesiles.citeplugin.util.TeamDisplayManager.getCachedTeam(onlinePlayer.getUniqueId());
                            if (ct != null && ct.teamId == teamId) {
                                plugin.getSidebarManager().refreshPlayerData(onlinePlayer);
                            }
                        }
                    }

                    // Rafraîchir instantanément le classement principal géant (TextDisplay)
                    if (plugin.getLeaderboardManager() != null) {
                        plugin.getLeaderboardManager().runUpdate();
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().warning("Échec de la transaction pour " + player.getName() + ": " + e.getMessage());

                // Rollback : restituer les items au joueur sur le thread principal
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("§cÉchec de la transaction. Vos objets ont été restitués. Raison : " + e.getMessage(), NamedTextColor.RED));

                    ItemStack rollbackStack = new ItemStack(material, finalQuantity);
                    player.getInventory().addItem(rollbackStack);

                    // Rafraîchir le menu pour ré-afficher les items
                    updateMarketInventory(player, event.getInventory(), cNpc, holder.getRawItems());
                });
            }
        });
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeItems(Player player, Material material, int amountToRemove) {
        int remaining = amountToRemove;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int amount = item.getAmount();
                if (amount <= remaining) {
                    remaining -= amount;
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(amount - remaining);
                    remaining = 0;
                }
                if (remaining == 0) break;
            }
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
