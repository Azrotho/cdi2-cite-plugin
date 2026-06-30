package fr.citedesiles.citeplugin.listener;

import fr.citedesiles.citeplugin.util.TeamDisplayManager;
import fr.citedesiles.citeplugin.util.TeamDisplayManager.CachedTeam;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        event.renderer(new ChatRenderer() {
            @Override
            public Component render(Player source, Component sourceDisplayName, Component message, Audience viewer) {
                // Récupérer les informations de l'équipe depuis le cache
                CachedTeam team = TeamDisplayManager.getCachedTeam(source.getUniqueId());

                Component prefix = Component.empty();
                TextColor nameColor = NamedTextColor.YELLOW;
                boolean isStaff = false;
                if (team != null) {
                    TextColor teamHexColor = TextColor.fromHexString(team.color);
                    prefix = Component.text("[" + team.tag + "] ", teamHexColor);
                    nameColor = teamHexColor;
                    isStaff = team.isStaff;
                }

                Component displayName = Component.text(source.getName(), nameColor);
                if (isStaff) {
                    prefix = prefix.decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
                    displayName = displayName.decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
                }
                Component separator = Component.text(" >> ", NamedTextColor.YELLOW);

                // Formater le message
                Component messageFormatted;
                if (source.isOp()) {
                    String rawMsg = PlainTextComponentSerializer.plainText().serialize(message);
                    Component deserialized;
                    if (rawMsg.contains("<") && rawMsg.contains(">")) {
                        deserialized = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(rawMsg);
                    } else {
                        deserialized = LegacyComponentSerializer.legacyAmpersand().deserialize(rawMsg);
                    }
                    if (isStaff && !rawMsg.matches(".*&[0-9a-fA-Fk-oK-OrR].*") && !rawMsg.contains("<")) {
                        deserialized = deserialized.decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
                    }
                    messageFormatted = Component.text()
                            .color(NamedTextColor.WHITE)
                            .append(deserialized)
                            .build();
                } else {
                    // Joueur normal : forcer en blanc
                    messageFormatted = message.color(NamedTextColor.WHITE);
                    if (isStaff) {
                        messageFormatted = messageFormatted.decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
                    }
                }

                return Component.text()
                        .append(prefix)
                        .append(displayName)
                        .append(separator)
                        .append(messageFormatted)
                        .build();
            }
        });
    }
}
