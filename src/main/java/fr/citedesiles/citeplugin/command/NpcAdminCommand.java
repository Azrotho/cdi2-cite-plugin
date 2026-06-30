package fr.citedesiles.citeplugin.command;

import fr.citedesiles.citeplugin.npc.ConfiguredNpc;
import fr.citedesiles.citeplugin.npc.NpcRegistry;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NpcAdminCommand implements CommandExecutor, TabCompleter {
    private final NpcRegistry registry;

    public NpcAdminCommand(NpcRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("cite.npc.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'exécuter cette commande.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /cite-npc [setlocation|reload]");
            return true;
        }

        String subCommand = args[0];
        if (subCommand.equalsIgnoreCase("reload")) {
            registry.loadAndSpawnNpcs();
            sender.sendMessage(ChatColor.GREEN + "La configuration des PNJs a été rechargée et appliquée.");
            return true;
        }

        if (subCommand.equalsIgnoreCase("setlocation")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent définir une localisation.");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Veuillez spécifier l'ID du PNJ. Usage: /cite-npc setlocation <id>");
                return true;
            }

            Player player = (Player) sender;
            String npcId = args[1];

            boolean found = false;
            for (ConfiguredNpc cNpc : registry.getConfiguredNpcs()) {
                if (cNpc.getId().equalsIgnoreCase(npcId)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                sender.sendMessage(ChatColor.RED + "PNJ '" + npcId + "' introuvable dans la configuration actuelle.");
                return true;
            }

            Location loc = player.getLocation();
            registry.updateNpcLocation(npcId, loc);
            player.sendMessage(ChatColor.GREEN + "La position du PNJ '" + npcId + "' a été mise à jour à vos coordonnées actuelles.");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Sous-commande inconnue. Usage: /cite-npc [setlocation|reload]");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("cite.npc.admin")) {
            return completions;
        }

        if (args.length == 1) {
            completions.add("setlocation");
            completions.add("reload");
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("setlocation")) {
            for (ConfiguredNpc cNpc : registry.getConfiguredNpcs()) {
                completions.add(cNpc.getId());
            }
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}
