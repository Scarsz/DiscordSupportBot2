package github.scarsz.discordsupportbot.transcript;

import github.scarsz.discordsupportbot.SupportBot;
import github.scarsz.discordsupportbot.support.Ticket;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageHistory;
import net.dv8tion.jda.core.entities.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;

public class Transcript {

    private final UUID uuid;
    private final Set<User> usersToNotify = new HashSet<>();

    public Transcript(UUID uuid, List<String> lines, Expiration expiration, Set<User> usersToNotify) {
        this.uuid = uuid;
        this.usersToNotify.addAll(usersToNotify);

        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("INSERT INTO `transcripts` (uuid, lines, expiration) VALUES (?, ?, ?)");
            statement.setString(1, uuid.toString());
            statement.setString(2, String.join("\n", lines));
            statement.setLong(3, expiration.toMillis());
            statement.executeUpdate();

            System.out.println("Created transcript for ticket id " + uuid);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static Transcript post(Ticket ticket) {
        List<String> lines = new LinkedList<>();
        MessageHistory history = ticket.getChannel().getHistory();
        while (history.retrievePast(100).complete().size() > 0) {}
        for (Message message : history.getRetrievedHistory()) {
            if (message.getAuthor().isFake() || message.getAuthor().isBot()) continue;
            lines.add(0, format(message));
        }
        if (lines.size() == 0) return null;

        Set<User> usersToNotify = new HashSet<>();
        if (ticket.getAuthor() != null) usersToNotify.add(ticket.getAuthor());
        history.getRetrievedHistory().stream().filter(Objects::nonNull).map(Message::getAuthor).forEach(usersToNotify::add);
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("SELECT * FROM `transcript_requests` WHERE `ticket` = ?");
            statement.setString(1, ticket.getUuid().toString());
            ResultSet result = statement.executeQuery();
            while (result.next()) {
                User user = SupportBot.get().getJda().getUserById(result.getString("user"));
                if (user != null) usersToNotify.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("DELETE FROM `transcript_requests` WHERE `ticket` = ?");
            statement.setString(1, ticket.getUuid().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String user = ticket.getAuthor() == null ? "Unknown [" + ticket.getAuthorId() + "]" : ticket.getAuthor().getName() + "#" + ticket.getAuthor().getDiscriminator() + " [" + ticket.getAuthor() + "]";
        String message = ticket.getInitialMessage();
        String guild = ticket.getChannel().getGuild().getName() + " [" + ticket.getChannel().getGuild().getId() + "]";

        lines.add(0, "Ticket #" + ticket.getNumber() + " by " + user + " in " + guild);
        lines.add(1, message);
        lines.add(2, "");

        return new Transcript(ticket.getUuid(), lines, Expiration.NEVER, usersToNotify);
    }

    public static String format(Message message) {
        String time = message.getCreationTime().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM));
        String author = message.getAuthor().getName() + "#" + message.getAuthor().getDiscriminator() + " [" + message.getAuthor().getId() + "]";
        StringBuilder text = new StringBuilder();
        text.append(message.getContentStripped());
        message.getAttachments().stream()
                .map(attachment -> "\n< attachment " + attachment.getFileName() + " " + attachment.getUrl() + " >")
                .forEach(text::append);
        return "[" + time + "] " + author + " > " + text;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Set<User> getUsersToNotify() {
        return usersToNotify;
    }

    public String getUrl() {
        return "https://support.scarsz.me/?" + uuid;
    }

}
