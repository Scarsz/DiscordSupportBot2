package github.scarsz.discordsupportbot.prompt;

import github.scarsz.discordsupportbot.SupportBot;
import github.scarsz.discordsupportbot.support.Ticket;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;

import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class FreeResponsePrompt extends Prompt {

    public FreeResponsePrompt(UUID helpdesk, UUID uuid, String name, String message) {
        super(helpdesk, uuid, name, message);

        // add to database
        if (!exists()) {
            try {
                PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("INSERT INTO `prompts` (uuid, name, message, helpdesk, type) VALUES (?, ?, ?, ?, ?)");
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.setString(3, message);
                statement.setString(4, helpdesk.toString());
                statement.setInt(5, Type.FREE_RESPONSE.ordinal());
                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Message send(Ticket ticket) {
        return ticket.getChannel().sendMessage(
                new EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setTitle("A little more information is required...")
                        .setDescription(getMessage())
                .build()
        ).complete();
    }

    @Override
    public void setQuery(Ticket ticket, int answerIndex) {
        SupportBot.get().getWaiter().waitForEvent(
                GuildMessageReceivedEvent.class,
                event -> {
                    if (event.getAuthor() == null || event.getAuthor().isBot() || event.getAuthor().isFake()) return false;
                    if (event.getMessage().getEmbeds().size() > 0) return false;
                    if (!event.getChannel().equals(ticket.getChannel())) return false;
                    if (!event.getAuthor().equals(ticket.getAuthor())) return false;
                    if (event.getMessage().getContentRaw().length() > 1024) {
                        ticket.getChannel().sendMessage(new EmbedBuilder()
                                .setColor(Color.YELLOW)
                                .setTitle("Response too long")
                                .setDescription("The maximum length of responses is 1024 characters- your response has been truncated.")
                                .build()
                        ).complete().delete().queueAfter(10, TimeUnit.SECONDS);
                    }
                    return true;
                },
                event -> ticket.answer(answerIndex,
                        event.getMessage().getContentRaw().length() >= 1024 - 3
                                ? event.getMessage().getContentRaw().substring(0, 1024 - 3) + "..."
                                : event.getMessage().getContentRaw()
                ),
                5, TimeUnit.MINUTES,
                ticket::destroy
        );
    }

}
