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
                if (team != null) {
                    TextColor teamHexColor = TextColor.fromHexString(team.color);
                    prefix = Component.text("[" + team.tag + "] ", teamHexColor);
                    nameColor = teamHexColor;
                }

                Component displayName = Component.text(source.getName(), nameColor);
                Component separator = Component.text(" >> ", NamedTextColor.YELLOW);

                // Formater le message
                Component messageFormatted;
                if (source.isOp()) {
                    // Traduire les codes de couleur & si OP
                    String rawMsg = PlainTextComponentSerializer.plainText().serialize(message);
                    messageFormatted = LegacyComponentSerializer.legacyAmpersand().deserialize(rawMsg)
                            .color(NamedTextColor.WHITE);
                } else {
                    // Joueur normal : forcer en blanc
                    messageFormatted = message.color(NamedTextColor.WHITE);
                }

                return prefix.append(displayName).append(separator).append(messageFormatted);
            }
        });
    }
}
