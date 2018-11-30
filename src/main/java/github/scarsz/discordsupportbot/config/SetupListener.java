package github.scarsz.discordsupportbot.config;

import github.scarsz.discordsupportbot.SupportBot;
import github.scarsz.discordsupportbot.support.Helpdesk;
import github.scarsz.discordsupportbot.util.Emoji;
import github.scarsz.discordsupportbot.util.FooterUtil;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Category;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import java.awt.*;
import java.util.concurrent.TimeUnit;

public class SetupListener extends ListenerAdapter {

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            boolean hasAdmin = event.getGuild().getSelfMember().hasPermission(Permission.ADMINISTRATOR);
            System.out.println("Joined guild " + event.getGuild() + " with" + (hasAdmin ? "" : "out") + " admin permission");
            event.getGuild().getOwner().getUser().openPrivateChannel().queue(pc -> {
                if (hasAdmin) {
                    pc.sendMessage(new EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setTitle("Discord Support Bot has been added to your server " + event.getGuild().getName())
                            .setDescription("Thank you for adding the bot to your server!" +
                                    "\n\n" +
                                    "To automatically create and configure a support category for you, @mention the bot as such: `@Support autosetup` " +
                                    "Alternatively, you can create an empty category and an empty text channel inside of it and use `setup` instead of `autosetup` to designate the channel yourself. " +
                                    "Once you have at least one (yes, you can have multiple) helpdesks setup in your server you can @mention the bot to open up the configuration panel at any time." +
                                    "\n\n" +
                                    "If you have any questions/concerns/suggestions, feel free to join the bot's Discord server at https://support.scarsz.me/discord.")
                            .setThumbnail(event.getGuild().getIconUrl() == null ? event.getJDA().getGuildById("382266272450215937").getIconUrl() : event.getGuild().getIconUrl())
                            .build()
                    ).queue();
                } else {
                    pc.sendMessage(new EmbedBuilder()
                            .setColor(Color.RED)
                            .setTitle("Discord Support Bot has been added to your server " + event.getGuild().getName() + " but does not have administrator permission")
                            .setDescription("This support bot does a lot of managerial functions and thus recommends having administrator permission. " +
                                    "The bot will not warn you or anyone else if an action it attempts fails as a result of not having permission." +
                                    "\n\n" +
                                    "To automatically create and configure a support category for you, @mention the bot as such: `@Support autosetup` " +
                                    "Alternatively, you can create an empty category and an empty text channel inside of it and use `setup` instead of `autosetup` to designate the channel yourself. " +
                                    "Once you have at least one (yes, you can have multiple) helpdesks setup in your server you can @mention the bot to open up the configuration panel at any time." +
                                    "\n\n" +
                                    "If you have any questions/concerns/suggestions, feel free to join the bot's Discord server at https://support.scarsz.me/discord.")
                            .setThumbnail(event.getGuild().getIconUrl() == null ? event.getJDA().getGuildById("382266272450215937").getIconUrl() : event.getGuild().getIconUrl())
                            .build()
                    ).queue();
                }

                if (event.getGuild().getRequiredMFALevel() == Guild.MFALevel.TWO_FACTOR_AUTH) {
                    pc.sendMessage(new EmbedBuilder()
                            .setColor(Color.RED)
                            .setTitle("MFA (multi-factor authentication) enforcement is enabled")
                            .setDescription("You can't have MFA required for servers where bots do administrative functions- the support bot is no exception to this." +
                                    "\n\n" +
                                    "You must disable the MFA requirement for the server in order for bots to perform administrative tasks. " +
                                    "In the case of the support bot, managing channels is an example of this." +
                                    "\n\n" +
                                    "The bot will **__NOT__** perform properly until this is addressed.")
                            .build()
                    ).queue();
                }
            });
        }).start();
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getMember() == null || event.getAuthor().isFake() || (!event.getMember().hasPermission(Permission.ADMINISTRATOR) && !event.getAuthor().getId().equals("95088531931672576"))) return;
        if (!event.getMessage().getMentionedUsers().contains(event.getJDA().getSelfUser())) return;
        if (event.getMessage().getContentRaw().replace(event.getJDA().getSelfUser().getAsMention(), "").trim().equalsIgnoreCase("autosetup")) {
            Category category = (Category) event.getGuild().getController().createCategory("support").complete();
            TextChannel channel = (TextChannel) category.createTextChannel("support").complete();
            TextChannel logChannel = (TextChannel) category.createTextChannel("transcripts").complete();
            Helpdesk helpdesk = new Helpdesk(category, channel);
            helpdesk.getConfig().setTicketMasterRole(event.getGuild().getRoles().stream().filter(role -> !role.isManaged() && !role.isPublicRole()).findFirst().orElse(null));
            helpdesk.getConfig().setTranscriptLogChannel(logChannel.getId());
            SupportBot.get().getHelpdesks().add(helpdesk);
            event.getMessage().delete().queue();
            event.getChannel().sendMessage(new EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setTitle("Helpdesk successfully automatically created")
                    .setDescription("Users can now send messages to " + helpdesk.getStartingChannel().getAsMention() + " to create support tickets. To configure helpdesks, @mention me.")
                    .setFooter("If you did not mean to create this helpdesk, react with " + Emoji.X + " to this message to delete it.", "https://upload.wikimedia.org/wikipedia/en/thumb/3/35/Information_icon.svg/1024px-Information_icon.svg.png")
                    .build()
            ).queue(message -> {
                message.addReaction(Emoji.X).queue();
                SupportBot.get().getWaiter().waitForEvent(
                        GuildMessageReactionAddEvent.class,
                        e -> e.getUser().equals(event.getAuthor()) && e.getMessageId().equals(message.getId()),
                        e -> {
                            message.delete().queue();
                            helpdesk.destroy();
                            category.getChannels().forEach(c -> c.delete().complete());
                            category.delete().queue();
                            event.getChannel().sendMessage(new EmbedBuilder().setColor(Color.GREEN).setTitle("Helpdesk creation canceled.").build()).queue(msg -> msg.delete().queueAfter(15, TimeUnit.SECONDS));
                        },
                        30, TimeUnit.SECONDS, null
                );
                message.delete().queueAfter(30, TimeUnit.SECONDS);
            });
        }
        if (!event.getMessage().getContentRaw().replace(event.getJDA().getSelfUser().getAsMention(), "").trim().equalsIgnoreCase("setup")) return;
        if (SupportBot.get().getHelpdeskForCategory(event.getChannel().getParent()) != null) {
            event.getChannel().sendMessage(new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("A helpdesk already exists for this category.")
                    .setFooter(FooterUtil.make(event.getAuthor()), event.getAuthor().getEffectiveAvatarUrl())
                    .build()
            ).queue(message -> message.delete().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        Helpdesk helpdesk = new Helpdesk(event.getChannel().getParent(), event.getChannel());
        helpdesk.getConfig().setTicketMasterRole(event.getGuild().getRoles().stream().filter(role -> !role.isManaged() && !role.isPublicRole()).findFirst().orElse(null));
        SupportBot.get().getHelpdesks().add(helpdesk);
        event.getMessage().delete().queue();

        event.getChannel().sendMessage(new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("Helpdesk successfully created")
                .setDescription("Users can now send messages to this channel to create support tickets. To configure helpdesks, @mention me in another channel.")
                .setFooter("If you did not mean to create this helpdesk, react with " + Emoji.X + " to this message to delete it.", "https://upload.wikimedia.org/wikipedia/en/thumb/3/35/Information_icon.svg/1024px-Information_icon.svg.png")
                .build()
        ).queue(message -> {
            message.addReaction(Emoji.X).queue();
            SupportBot.get().getWaiter().waitForEvent(
                    GuildMessageReactionAddEvent.class,
                    e -> e.getUser().equals(event.getAuthor()) && e.getMessageId().equals(message.getId()),
                    e -> {
                        message.delete().queue();
                        helpdesk.destroy();
                        event.getChannel().sendMessage(new EmbedBuilder().setColor(Color.GREEN).setTitle("Helpdesk creation canceled.").build()).queue(msg -> msg.delete().queueAfter(15, TimeUnit.SECONDS));
                    },
                    30, TimeUnit.SECONDS, null
            );
            message.delete().queueAfter(30, TimeUnit.SECONDS);
        });
    }

}
