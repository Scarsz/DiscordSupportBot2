package github.scarsz.discordsupportbot.support;

import github.scarsz.discordsupportbot.SupportBot;
import github.scarsz.discordsupportbot.config.Configuration;
import github.scarsz.discordsupportbot.exception.DataInvalidException;
import github.scarsz.discordsupportbot.exception.HelpdeskDoesNotExistException;
import github.scarsz.discordsupportbot.prompt.Prompt;
import github.scarsz.discordsupportbot.util.NumberUtil;
import lombok.Getter;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Category;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.channel.category.CategoryDeleteEvent;
import net.dv8tion.jda.core.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import java.awt.*;
import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Helpdesk extends ListenerAdapter {

    @Getter private final UUID uuid;
    @Getter private final String categoryId;
    @Getter private final String startingChannelId;
    @Getter private final Configuration config;
    @Getter private final List<Prompt> prompts;
    @Getter private final List<Ticket> tickets;
    @Getter private final AtomicInteger ticketCounter;

    /**
     * Construct an existing helpdesk object, retrieving values from database
     * @param uuid
     * @throws HelpdeskDoesNotExistException
     */
    public Helpdesk(UUID uuid) throws HelpdeskDoesNotExistException, DataInvalidException, SQLException {
        this.uuid = uuid;
        if (!doesExist(uuid)) {
            System.err.println("Failed to create helpdesk " + uuid);
            throw new HelpdeskDoesNotExistException(this);
        } else {
            System.out.println("Instantiating helpdesk from database: " + uuid);
        }

        PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("SELECT * FROM `helpdesks` WHERE `uuid` = ?");
        statement.setString(1, uuid.toString());
        ResultSet result = statement.executeQuery();
        result.next();
        categoryId = result.getString("category");
        if (getCategory() == null) throw new DataInvalidException(this, "category invalid", result.getString("category"));
        startingChannelId = result.getString("startingChannel");
        if (getStartingChannel() == null) throw new DataInvalidException(this, "startingChannel invalid", result.getString("startingChannel"));
        ticketCounter = new AtomicInteger(result.getInt("counter"));
        statement.close();

        config = new Configuration(uuid);
        prompts = Prompt.collect(this);
        tickets = Ticket.collect(this);

        SupportBot.get().getJda().addEventListener(this);
        System.out.println();
    }

    /**
     * Create a new, default helpdesk
     * @param category The parent category of the helpdesk
     * @param startingChannel The channel where tickets are started from
     */
    public Helpdesk(Category category, TextChannel startingChannel) {
        this(category, startingChannel, 0, UUID.randomUUID());
    }

    /**
     * Construct a new helpdesk with all the needed objects
     */
    private Helpdesk(Category category, TextChannel startingChannel, int counterValue, UUID uuid) {
        this.categoryId = category.getId();
        this.startingChannelId = startingChannel.getId();
        this.ticketCounter = new AtomicInteger(counterValue);
        this.uuid = uuid;
        this.config = new Configuration(uuid);
        this.prompts = new LinkedList<>();
        this.tickets = new ArrayList<>();

        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("INSERT INTO `helpdesks` (uuid, category, startingChannel, counter) VALUES (?, ?, ?, ?)");
            statement.setString(1, uuid.toString());
            statement.setString(2, category.getId());
            statement.setString(3, startingChannel.getId());
            statement.setInt(4, ticketCounter.get());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("New helpdesk created for " + startingChannel.getGuild() + " [" + (SupportBot.get().getHelpdesksForGuild(startingChannel.getGuild()).size() + 1) + " total]");
        SupportBot.get().getJda().addEventListener(this);
    }

    public static boolean doesExist(UUID uuid) {
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("SELECT * FROM `helpdesks` WHERE `uuid` = ?");
            statement.setString(1, uuid.toString());
            ResultSet result = statement.executeQuery();
            return result.isBeforeFirst();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getAuthor().equals(event.getJDA().getSelfUser())) return;
        if (event.getAuthor().isBot()) return;
        if (!event.getChannel().equals(getStartingChannel())) return;
        if (event.getMember().hasPermission(Permission.ADMINISTRATOR) && !event.getMessage().getContentRaw().startsWith("!!!")) return;

        String message = event.getMessage().getContentRaw();
        List<File> attachments = new LinkedList<>();
        for (Message.Attachment attachment : event.getMessage().getAttachments()) {
            if (attachment.getSize() < 8388608) {
                message += "\n`" + attachment.getFileName() + "`";
                File file = new File("user-upload-" + attachment.getFileName());
                attachment.download(file);
                attachments.add(file);
            }
        }

        event.getMessage().delete().queue();

        Ticket ticket;
        try {
            ticket = createTicket(event.getMember(), message);
        } catch (InsufficientPermissionException e) {
            getStartingChannel().sendMessage(new EmbedBuilder().setTitle("I am unable to create the ticket due to lack of permission.").setColor(Color.RED).build()).queue();
            return;
        }
        tickets.add(ticket);

        for (File attachment : attachments) {
            ticket.getChannel().sendFile(attachment).complete();
            if (!attachment.delete()) {
                System.err.println("Failed to delete " + attachment.getName());
            }
        }
    }

    @Override
    public void onTextChannelDelete(TextChannelDeleteEvent event) {
        if (event.getChannel().equals(getStartingChannel())) {
            destroy();
        }
    }
    @Override
    public void onCategoryDelete(CategoryDeleteEvent event) {
        if (event.getCategory().equals(getCategory())) {
            destroy();
        }
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
        if (getConfig().shouldMarkAsSolvedOnAbandon()) {
            tickets.stream()
                    .filter(ticket -> event.getUser() == null || ticket.getAuthorId().equals(event.getUser().getId()))
                    .forEach(ticket -> ticket.setStatus(Status.ABANDONED));
        }
    }

    public Ticket createTicket(Member author, String message) {
        int number = ticketCounter.incrementAndGet();
        TextChannel channel = (TextChannel) getCategory().createTextChannel(NumberUtil.pad(number))
                .addPermissionOverride(author, Arrays.asList(
                        Permission.VIEW_CHANNEL,
                        Permission.MESSAGE_READ,
                        Permission.MESSAGE_WRITE,
                        Permission.MESSAGE_ADD_REACTION,
                        Permission.MESSAGE_ATTACH_FILES,
                        Permission.MESSAGE_HISTORY
                ), Collections.emptySet())
                .addPermissionOverride(getConfig().getTicketMasterRole(), Arrays.asList(
                        Permission.VIEW_CHANNEL,
                        Permission.MESSAGE_READ,
                        Permission.MESSAGE_WRITE,
                        Permission.MESSAGE_ADD_REACTION,
                        Permission.MESSAGE_ATTACH_FILES,
                        Permission.MESSAGE_HISTORY
                ), Collections.emptySet())
                .complete();

        try {
            Ticket ticket = new Ticket(this, channel, UUID.randomUUID(), number, author.getUser().getId(), message, Status.GATHERING_INFO);
            tickets.add(ticket);
            getStartingChannel().sendMessage(author.getAsMention() + ",").embed(new EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setTitle("Support ticket created")
                    .setDescription("You have created ticket " + channel.getAsMention() + ". Direct further messages to this channel.")
                    .build()).queue(m -> m.delete().queueAfter(10, TimeUnit.SECONDS));
            System.out.println("Helpdesk " + this + " has created ticket #" + number);
            return ticket;
        } catch (DataInvalidException e) {
            e.printStackTrace();
            channel.delete().queue();
            return null;
        }
    }

    public void flush() {
        tickets.forEach(Ticket::flush);

        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("UPDATE `helpdesks` SET `counter` = ? WHERE `uuid` = ?");
            statement.setInt(1, ticketCounter.get());
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void destroy() {
        SupportBot.get().getJda().removeEventListener(this);
        SupportBot.get().getHelpdesks().remove(this);
        new ArrayList<>(tickets).forEach(Ticket::destroy);

        // remove from database
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("DELETE FROM `helpdesks` WHERE `uuid` = ?");
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Category getCategory() {
        return SupportBot.get().getJda().getCategoryById(categoryId);
    }

    public TextChannel getStartingChannel() {
        TextChannel channel = SupportBot.get().getJda().getTextChannelById(startingChannelId);
        if (channel == null) {
            destroy();
            return null;
        } else {
            return channel;
        }
    }

    @Override
    public String toString() {
        return "Helpdesk{" +
                "uuid=" + uuid +
                (tickets != null ? ", tickets=" + tickets.size() : "") +
                '}';
    }

}
