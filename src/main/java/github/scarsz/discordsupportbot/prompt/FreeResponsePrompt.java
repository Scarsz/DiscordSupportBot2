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
                    boolean correctChannel = event.getChannel().equals(ticket.getChannel());
                    boolean correctAuthor = event.getAuthor().equals(ticket.getAuthor());
                    return correctChannel && correctAuthor;
                },
                event -> ticket.answer(answerIndex, event.getMessage().getContentRaw()),
                5, TimeUnit.MINUTES,
                ticket::destroy
        );
    }

}
