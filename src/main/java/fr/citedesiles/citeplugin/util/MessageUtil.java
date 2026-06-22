package fr.citedesiles.citeplugin.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Helper pour envoyer des messages formatés aux joueurs.
 */
public final class MessageUtil {

    private MessageUtil() {}

    /**
     * Envoie un message formaté (déjà colorisé) au destinataire.
     */
    public static void send(CommandSender target, String message) {
        if (message != null && !message.isEmpty()) {
            target.sendMessage(message);
        }
    }

    /**
     * Envoie un message avec le préfixe du plugin.
     */
    public static void sendPrefixed(CommandSender target, String prefix, String message) {
        if (message != null && !message.isEmpty()) {
            target.sendMessage(prefix + " " + message);
        }
    }

    /**
     * Traduit les codes couleur & en vraies couleurs Minecraft.
     */
    public static String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
