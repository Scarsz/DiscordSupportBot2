package github.scarsz.discordsupportbot.support;

import github.scarsz.discordsupportbot.SupportBot;
import github.scarsz.discordsupportbot.exception.DataInvalidException;
import github.scarsz.discordsupportbot.prompt.Prompt;
import github.scarsz.discordsupportbot.transcript.Transcript;
import github.scarsz.discordsupportbot.util.Emoji;
import github.scarsz.discordsupportbot.util.FooterUtil;
import github.scarsz.discordsupportbot.util.NumberUtil;
import github.scarsz.discordsupportbot.util.TimeUtil;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.managers.ChannelManager;
import net.dv8tion.jda.core.requests.restaction.PermissionOverrideAction;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Ticket extends ListenerAdapter {

    @Getter private final List<String> answers = new LinkedList<>();
    @Getter private final String authorId;
    @Getter private final String channelId;
    @Getter private final Helpdesk helpdesk;
    @Getter private final int number;
    @Getter private final UUID uuid;
    @Getter private final Thread transcriptThread = new Thread(() -> {
        try {
            Thread.sleep(15 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        sendTranscripts();
        destroy();
    });

    @Getter private String initialMessage;
    @Getter private Status status;
    private String startingMessageId = null;
    @Getter @Setter private TicketVoiceChannel voiceChannel = null;

    public Ticket(Helpdesk helpdesk, TextChannel channel, UUID uuid, int number, String authorId, String initialMessage, Status status) throws DataInvalidException {
        if (channel == null) throw new DataInvalidException(helpdesk, "Channel for ticket " + uuid + " doesn't exist", null);

        this.helpdesk = helpdesk;
        this.channelId = channel.getId();
        this.uuid = uuid;
        this.number = number;
        this.authorId = authorId;
//        this.initialMessage = initialMessage != null
//                ? initialMessage
//                : getStartingMessage() != null
//                    ? getStartingMessage().getEmbeds().size() > 0
//                        ? getStartingMessage().getEmbeds().get(0).getDescription()
//                        : "[Initial message unavailable]"
//                    : "[Initial message unavailable]";
        this.initialMessage = initialMessage;
        if (initialMessage == null) {
            new Thread(() -> {
                Message retrieved = getStartingMessage();
                if (retrieved != null && retrieved.getEmbeds().size() > 0) {
                    MessageEmbed embed = retrieved.getEmbeds().get(0);
                    if (embed.getDescription() != null) {
                        this.initialMessage = embed.getDescription();
                    }
                }
            }).start();
        }
        setStatus(status, true);

        // add to database
        if (!exists()) {
            try {
                PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("INSERT INTO `tickets` (uuid, helpdesk, author, channel, number, status) VALUES (?, ?, ?, ?, ?, ?)");
                statement.setString(1, uuid.toString());
                statement.setString(2, helpdesk.getUuid().toString());
                statement.setString(3, authorId);
                statement.setString(4, channel.getId());
                statement.setInt(5, number);
                statement.setInt(6, status.ordinal());
                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        SupportBot.get().getJda().addEventListener(this);
    }

    public static List<Ticket> collect(Helpdesk helpdesk) {
        try {
            List<Ticket> tickets = new ArrayList<>();

            System.out.println("Querying tickets for " + helpdesk);
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("SELECT * FROM `tickets` WHERE `helpdesk` = ?");
            statement.setString(1, helpdesk.getUuid().toString());
            ResultSet result = statement.executeQuery();
            while (result.next()) {
                UUID uuid = UUID.fromString(result.getString("uuid"));
                System.out.print("Building ticket " + uuid);
                TextChannel channel = SupportBot.get().getJda().getTextChannelById(result.getString("channel"));
                System.out.print(".");
                if (channel == null) {
                    System.out.println(" fail: channel not found, deleting ticket from database");
                    statement = SupportBot.get().getDatabase().prepareStatement("DELETE FROM `tickets` WHERE `uuid` = ?");
                    statement.setString(1, uuid.toString());
                    statement.executeUpdate();
                    continue;
                }
                System.out.print(".");
                int number = result.getInt("number");
                System.out.print(".");
                String author = result.getString("author");
                System.out.print(".");
                int statusId = result.getInt("status");
                System.out.print(".");
                Status status = Arrays.stream(Status.values()).filter(st -> st.ordinal() == statusId).findFirst().orElse(Status.RESPONDED);
                System.out.print(".");

                try {
                    tickets.add(new Ticket(helpdesk, channel, uuid, number, author, null, status));
                    System.out.println(" done");
                } catch (DataInvalidException e) {
                    System.out.println();
                    e.printStackTrace();
                }
            }

            if (tickets.size() > 0) System.out.println("Collected " + tickets.size() + " tickets for " + helpdesk);
            return tickets;
        } catch (SQLException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public boolean exists() {
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("SELECT * FROM `tickets` WHERE `uuid` = ?");
            statement.setString(1, uuid.toString());
            return statement.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
        if (!event.getChannel().equals(getChannel())) return;
        if (event.getUser().equals(event.getJDA().getSelfUser())) return;
        if (event.getUser().isBot()) return;
        if (getStartingMessage() == null) return;
        if (!event.getMessageId().equals(getStartingMessage().getId())) return;

        System.out.println(this + " received " + event.getReactionEmote().getName() + " from " + event.getMember().getEffectiveName());

        event.getReaction().removeReaction(event.getUser()).queue();

        if (event.getReactionEmote().getName().equals(Emoji.WHITE_CHECK_MARK)) {
            if (memberHasPermission(event.getMember())) {
                setStatus(Status.SOLVED, event.getMember());
            }
        }

        if (event.getReactionEmote().getName().equals(Emoji.CLIPBOARD)) {
            try {
                PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("INSERT INTO `transcript_requests` (ticket, user) VALUES (?, ?)");
                statement.setString(1, uuid.toString());
                statement.setString(2, event.getUser().getId());
                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        if (event.getReactionEmote().getName().equalsIgnoreCase(Emoji.MICROPHONE)) {
            if (!memberHasPermission(event.getMember())) return;
            if (voiceChannel == null || !getChannel().getParent().getVoiceChannels().contains(voiceChannel.getChannel())) {
                voiceChannel = new TicketVoiceChannel(this, event.getMember());
            } else {
                getChannel().sendMessage(new EmbedBuilder()
                        .setColor(Color.RED)
                        .setTitle("A voice channel already exists for this ticket.")
                        .setThumbnail(event.getUser().getEffectiveAvatarUrl())
                        .setFooter(FooterUtil.make(event.getUser()), event.getUser().getEffectiveAvatarUrl())
                        .build()
                ).queue(message -> message.delete().queueAfter(15, TimeUnit.SECONDS));
            }
        }

        if (event.getReactionEmote().getName().equals(Emoji.HEAVY_PLUS_SIGN)) {
            if (!memberHasPermission(event.getMember())) return;
            getChannel().sendMessage(new EmbedBuilder()
                    .setColor(Color.WHITE)
                    .setTitle("Who would you like to add?")
                    .setDescription("Must be in the format `USERID` or `DiscordUsername#1234`, replacing `DiscordUsername` with the person's Discord username and `1234` with the person's discriminator.")
                    .setFooter(event.getUser().getName() + "#" + event.getUser().getDiscriminator(), event.getUser().getEffectiveAvatarUrl())
                    .build()
            ).queue(message -> {
                SupportBot.get().getWaiter().waitForEvent(
                        GuildMessageReceivedEvent.class,
                        e -> {
                            if (!e.getAuthor().equals(event.getUser()) || !e.getChannel().equals(getChannel())) return false;

                            try {
                                if (e.getGuild().getMemberById(e.getMessage().getContentRaw()) != null) {
                                    return true;
                                }
                            } catch (Exception ignored) {}

                            if (e.getMessage().getContentRaw().split("#").length < 2) {
                                getChannel().sendMessage(new EmbedBuilder()
                                        .setColor(Color.RED)
                                        .setTitle("Try again...")
                                        .setDescription("You need to put the person's discriminator as well.")
                                        .setFooter(FooterUtil.make(e.getAuthor()), e.getAuthor().getEffectiveAvatarUrl())
                                        .build()
                                ).queue(badFormatMessage -> badFormatMessage.delete().queueAfter(15, TimeUnit.SECONDS));
                                return false;
                            }

                            String[] raw = e.getMessage().getContentRaw().split("#");
                            if (!StringUtils.isNumeric(raw[1])) {
                                getChannel().sendMessage(new EmbedBuilder()
                                        .setColor(Color.RED)
                                        .setTitle("Try again...")
                                        .setDescription("The discriminator you gave isn't numeric.")
                                        .setFooter(FooterUtil.make(e.getAuthor()), e.getAuthor().getEffectiveAvatarUrl())
                                        .build()
                                ).queue(badFormatMessage -> badFormatMessage.delete().queueAfter(15, TimeUnit.SECONDS));
                                return false;
                            }
                            String username = raw[0];
                            String discriminator = NumberUtil.pad(Integer.parseInt(raw[1]), 4);
                            Set<Member> found = e.getGuild().getMembersByName(username, true).stream().filter(member -> member.getUser().getDiscriminator().equals(discriminator)).collect(Collectors.toSet());
                            boolean multipleMatches = false;
                            if (found.size() > 1) {
                                multipleMatches = true;
                                found = e.getGuild().getMembersByName(username, false).stream().filter(member -> member.getUser().getDiscriminator().equals(discriminator)).collect(Collectors.toSet());
                            }
                            if (found.size() == 0) {
                                getChannel().sendMessage(new EmbedBuilder()
                                        .setColor(Color.RED)
                                        .setTitle("Try again...")
                                        .setDescription(multipleMatches
                                                ? "Multiple people were found case-insensitively but nobody was found case-sensitive when narrowing results down."
                                                : "Nobody was found by `" + username + "#" + discriminator + "`."
                                        )
                                        .setFooter(FooterUtil.make(e.getAuthor()), e.getAuthor().getEffectiveAvatarUrl())
                                        .build()
                                ).queue(badFormatMessage -> badFormatMessage.delete().queueAfter(15, TimeUnit.SECONDS));
                                return false;
                            }

                            return true;
                        },
                        e -> {
                            boolean userId;

                            try {
                                userId = e.getGuild().getMemberById(e.getMessage().getContentRaw()) != null;
                            } catch (Exception ex) {
                                userId = false;
                            }

                            Member target;
                            if (userId) {
                                target = e.getGuild().getMemberById(e.getMessage().getContentRaw());
                            } else {
                                String[] raw = e.getMessage().getContentRaw().split("#");
                                String username = raw[0];
                                String discriminator = raw[1];
                                Set<Member> found = e.getGuild().getMembersByName(username, true).stream().filter(member -> member.getUser().getDiscriminator().equals(NumberUtil.pad(Integer.parseInt(discriminator), 4))).collect(Collectors.toSet());
                                if (found.size() > 1) found = e.getGuild().getMembersByName(username, false).stream().filter(member -> member.getUser().getDiscriminator().equals(NumberUtil.pad(Integer.parseInt(discriminator), 4))).collect(Collectors.toSet());
                                target = found.iterator().next();
                            }

                            addPermissions(target);
                            if (voiceChannel != null) voiceChannel.addPermissions(target);

                            getChannel().sendMessage(new EmbedBuilder()
                                    .setColor(Color.GREEN)
                                    .setTitle("Success")
                                    .setDescription("Added member " + target.getAsMention() + " to this ticket.")
                                    .setFooter(FooterUtil.make(e.getAuthor()), e.getAuthor().getEffectiveAvatarUrl())
                                    .build()
                            ).queue();
                            message.delete().queue();
                        },
                        30, TimeUnit.SECONDS,
                        () -> message.editMessage(new EmbedBuilder()
                                .setColor(Color.RED)
                                .setTitle("Canceled adding user because you didn't give a valid response in time.")
                                .setFooter(FooterUtil.make(event.getUser()), event.getUser().getEffectiveAvatarUrl())
                                .build()
                        ).queue(v -> message.delete().queueAfter(5, TimeUnit.SECONDS))
                );
            });
        }
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (status == Status.GATHERING_INFO) return;

        if (!event.getChannel().equals(getChannel()) || event.getAuthor() == null || event.getJDA().getSelfUser().equals(event.getAuthor())) return;
        Role ticketMasterRole = helpdesk.getConfig().getTicketMasterRole();
        if (event.getAuthor().isBot() && !event.getAuthor().equals(event.getJDA().getSelfUser())) {
            setStatus(Status.RESPONDED);
        } else if (authorId.equals(event.getAuthor().getId()) || !event.getMember().getRoles().contains(ticketMasterRole)) {
            setStatus(Status.AWAITING_RESPONSE);
        } else {
            setStatus(Status.RESPONDED);
        }
    }

    public void answer(int index, String answer) {
        if (answers.size() == index) {
            answers.add(answer);
        } else {
            answers.set(index, answer);
        }

        Prompt prompt = helpdesk.getPrompts().size() > index + 1 ? helpdesk.getPrompts().get(index + 1) : null;
        if (prompt != null) {
            prompt.send(this);
            prompt.setQuery(this, index + 1);
        } else {
            setStatus(Status.READY);
        }
    }

    private void setStatus(Status status) {
        setStatus(status, null);
    }

    private void setStatus(Status status, boolean queue) {
        setStatus(status, null, queue);
    }

    private void setStatus(Status status, Member cause) {
        setStatus(status, cause, true);
    }

    private void setStatus(Status status, Member cause, boolean queue) {
        boolean sameState = this.status == status;
        this.status = status;
//        System.out.println("Set status: " + status.name() + "\n" + Arrays.stream(ExceptionUtils.getStackFrames(new Throwable()))
//                .filter(s -> s.contains("github.scarsz") || s.contains(".jda."))
//                .skip(1)
//                .collect(Collectors.joining("\n"))
//        );
        if (getChannel() == null) {
            destroy();
            return;
        }
        flush();
        ChannelManager setTopic = getChannel().getManager().setTopic("Status: " + status.toString() + " | Ticket author: <@" + getAuthorId() + ">");
        if (queue) setTopic.queue(); else setTopic.complete();

        switch (status) {
            case GATHERING_INFO:
                Prompt firstPrompt = helpdesk.getPrompts().size() > 0 ? helpdesk.getPrompts().get(0) : null;
                if (firstPrompt != null) {
                    firstPrompt.send(this);
                    firstPrompt.setQuery(this, 0);
                } else {
                    setStatus(Status.READY);
                }
                break;
            case READY:
                // clear messages
                List<Message> history = getChannel().getHistory().retrievePast(100).complete();
                if (history.size() > 0) getChannel().deleteMessages(history).queue();

                // send the leading message
                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(Color.WHITE)
                        .setTitle("Ticket by " + getMember().getEffectiveName())
                        .setThumbnail(getAuthor().getEffectiveAvatarUrl())
                        .setDescription(initialMessage)
                        .setFooter(TimeUtil.timestamp() + " | " + Emoji.WHITE_CHECK_MARK + " Mark as solved " + Emoji.MICROPHONE + " Spawn voice channel " + Emoji.CLIPBOARD + " Request transcript " + Emoji.HEAVY_PLUS_SIGN + " Add user to ticket", "http://icons.iconarchive.com/icons/paomedia/small-n-flat/1024/clock-icon.png");
                for (int i = 0; i < helpdesk.getPrompts().size(); i++) {
                    Prompt prompt = helpdesk.getPrompts().get(i);
                    String answer = answers.get(i);
                    if (prompt == null || answer == null) continue;
                    embed.addField(prompt.getName(), answer, true);
                }

                // permissions
//                List<String> messages = new LinkedList<>();
//                for (PermissionOverride override : channel.getPermissionOverrides()) {
//                    messages.add(override.isRoleOverride() ? override.getRole().getName() : override.getMember().getEffectiveName());
//                    messages.add(" allowed: " + override.getAllowed().stream().map(Permission::getName).collect(Collectors.joining(", ")));
//                    messages.add(" denied: " + override.getDenied().stream().map(Permission::getName).collect(Collectors.joining(", ")));
//                }
//                channel.sendMessage("```\n" + String.join("\n", messages) + "\n```").complete();

                getChannel().sendMessage(embed.build()).queue(message -> {
                    startingMessageId = message.getId();

                    message.addReaction(Emoji.WHITE_CHECK_MARK).queue();
                    message.addReaction(Emoji.MICROPHONE).queue();
                    message.addReaction(Emoji.CLIPBOARD).queue();
                    message.addReaction(Emoji.HEAVY_PLUS_SIGN).queue();

                    message.pin().queue(v -> message.getChannel().getHistory().retrievePast(100).queue(messages -> messages.stream()
                            .filter(msg -> msg.getAttachments().size() == 0 && msg.getEmbeds().size() == 0)
                            .findFirst().ifPresent(m -> message.getChannel().deleteMessageById(m.getId()).queue())));
                    flush();
                });

                setStatus(Status.AWAITING_RESPONSE);
                break;
            case SOLVED:
                if (sameState) break;
                if (transcriptThread.getState() != Thread.State.NEW) break;
                transcriptThread.start();
                getChannel().sendMessage(new EmbedBuilder()
                        .setColor(Color.GREEN)
                        .setTitle("Ticket has been marked as solved!")
                        .setFooter(cause != null ? FooterUtil.make(cause) : null, cause != null ? cause.getUser().getEffectiveAvatarUrl() : null)
                        .build()
                ).queue();
                break;
            case ABANDONED:
                getChannel().sendMessage(new EmbedBuilder().setColor(Color.DARK_GRAY).setTitle("The ticket author has left.").build()).queue();
                if (helpdesk.getConfig().shouldMarkAsSolvedOnAbandon()) setStatus(Status.SOLVED);
                break;
        }
    }

    public void addPermissions(IPermissionHolder holder) {
        PermissionOverrideAction override = holder instanceof Member
                ? getChannel().createPermissionOverride((Member) holder)
                : getChannel().createPermissionOverride((Role) holder);
        override.setAllow(Permission.MESSAGE_READ, Permission.MESSAGE_WRITE, Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES).queue();
    }

    public void flush() {
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("UPDATE `tickets` SET `status` = ? WHERE `uuid` = ?");
            statement.setInt(1, status.ordinal());
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void sendTranscripts() {
        Transcript transcript = Transcript.post(this);
        if (transcript == null) return;

        MessageEmbed embed = new EmbedBuilder()
                .setColor(Color.WHITE)
                .setTitle("A transcript is available for ticket " + number)
                .setThumbnail(getAuthor() == null ? getChannel().getGuild().getIconUrl() : getAuthor().getEffectiveAvatarUrl())
                .setDescription(transcript.getUrl())
                .build();

        for (String userId : transcript.getUsersToNotify()) {
            User user = SupportBot.get().getJda().getUserById(userId);
            if (user == null || user.isBot() || user.isFake() || user.equals(SupportBot.get().getJda().getSelfUser())) continue;
            user.openPrivateChannel().queue(dm -> dm.sendMessage(embed).queue());
        }

        TextChannel logChannel = helpdesk.getConfig().getTranscriptLogChannel();
        if (logChannel != null) {
            logChannel.sendMessage(embed).queue();
        }
    }

    public void destroy() {
        destroy(true);
    }
    public void destroy(boolean deleteChannel) {
        SupportBot.get().getJda().removeEventListener(this);
        helpdesk.getTickets().remove(this);
        if (deleteChannel && getChannel() != null) getChannel().delete().queue();

        // remove from database
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("DELETE FROM `tickets` WHERE `uuid` = ?");
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTextChannelDelete(TextChannelDeleteEvent event) {
        if (!event.getChannel().equals(getChannel())) return;
        destroy(false);
    }

    @Override
    public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
        if (event.getChannel().getId().equals(channelId) && getStartingMessage() != null && event.getMessageId().equals(getStartingMessage().getId())) {
            destroy();
        }
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
        if (helpdesk.getConfig().shouldMarkAsSolvedOnAbandon()) {
            helpdesk.getTickets().stream()
                    .filter(ticket -> ticket.authorId.equals(event.getUser().getId()))
                    .forEach(ticket -> ticket.setStatus(Status.ABANDONED));
        }
    }

    public User getAuthor() {
        if (getChannel() == null) {
            destroy();
            return null;
        } else {
            return getChannel().getJDA().getUserById(authorId);
        }
    }

    public Member getMember() {
        return getChannel().getGuild().getMemberById(authorId);
    }

    public Message getStartingMessage() {
        if (getChannel() == null) {
            destroy();
            return null;
        }
        if (SupportBot.get().getJda().getStatus() != JDA.Status.CONNECTED) return null;
        if (startingMessageId != null && getChannel().getMessageById(startingMessageId).complete() != null) return getChannel().getMessageById(startingMessageId).complete();
        MessageHistory history = getChannel().getHistory();
        System.out.print("Retrieving messages");
        int count = 0;
        do {
            count++;
            System.out.print(".");
            if (history.retrievePast(100).complete().size() == 0) {
                System.out.println(" done");
                break;
            }
        } while (count < 10);
        List<Message> retrievedHistory = new LinkedList<>(history.getRetrievedHistory());
        Collections.reverse(retrievedHistory);
        Message msg = retrievedHistory.stream()
                .filter(message -> message.getAuthor().equals(SupportBot.get().getJda().getSelfUser()))
                .filter(message -> message.getEmbeds().size() > 0)
                .findFirst().orElse(null);
        if (msg == null) {
            destroy();
            return null;
        } else {
            startingMessageId = msg.getId();
            return msg;
        }
    }

    public boolean memberHasPermission(Member member) {
        boolean ticketMaster = member.getRoles().contains(helpdesk.getConfig().getTicketMasterRole());
        boolean admin = member.hasPermission(Permission.ADMINISTRATOR);
        boolean hasOverride = getChannel().getPermissionOverride(member) != null;
        return ticketMaster || admin || hasOverride;
    }

    public TextChannel getChannel() {
        return SupportBot.get().getJda().getTextChannelById(channelId);
    }

    @Override
    public String toString() {
        return "Ticket{" +
                "channelId='" + channelId + '\'' +
                ", helpdesk=" + helpdesk +
                ", number=" + number +
                ", uuid=" + uuid +
                ", status=" + status +
                '}';
    }

}
