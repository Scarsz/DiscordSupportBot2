package github.scarsz.discordsupportbot.config;

import github.scarsz.discordsupportbot.SupportBot;
import github.scarsz.discordsupportbot.support.Helpdesk;
import github.scarsz.discordsupportbot.util.Emoji;
import github.scarsz.discordsupportbot.util.FooterUtil;
import lombok.Getter;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ConfigListener extends ListenerAdapter {

    @Getter private Set<ConfigurationMessage> configurationMessages = new HashSet<>();

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
//        if (!event.getAuthor().equals(event.getJDA().getSelfUser()) && event.getChannel().getId().equals("382266272877903885")) {
//            String message = "```";
//            message += "\n" + event.getAuthor() + " > " + event.getMessage();
//            message += "\nevent.getMessage().getContentRaw().contains(event.getJDA().getSelfUser().getAsMention()) = " + event.getMessage().getContentRaw().contains(event.getJDA().getSelfUser().getAsMention());
//            message += "\nevent.getMessage().getContentRaw().contains(event.getGuild().getSelfMember().getAsMention()) = " + event.getMessage().getContentRaw().contains(event.getGuild().getSelfMember().getAsMention());
//            message += "\nevent.getMessage().getMentionedUsers().contains(event.getJDA().getSelfUser()) = " + event.getMessage().getMentionedUsers().contains(event.getJDA().getSelfUser());
//            message += "\nevent.getMessage().getMentionedMembers().contains(event.getGuild().getSelfMember()) = " + event.getMessage().getMentionedMembers().contains(event.getGuild().getSelfMember());
//            message += "\n```";
//            event.getChannel().sendMessage(message).queue();
//        }

        if (event.getMember() == null || event.getAuthor().isFake() || (!event.getMember().hasPermission(Permission.ADMINISTRATOR) && !event.getAuthor().getId().equals("95088531931672576"))) return;
        if (!event.getMessage().getMentionedMembers().contains(event.getGuild().getSelfMember())) return;
        Set<Helpdesk> helpdesks = SupportBot.get().getHelpdesksForGuild(event.getGuild());

        if (StringUtils.isBlank(event.getMessage().getContentRaw().replace(event.getJDA().getSelfUser().getAsMention(), "").trim())) {
            EmbedBuilder embed = new EmbedBuilder().setThumbnail(event.getGuild().getIconUrl());
            embed.setFooter(FooterUtil.make(event.getAuthor()), event.getAuthor().getEffectiveAvatarUrl());
            if (helpdesks.size() == 0) {
                embed.setColor(Color.RED);
                embed.setTitle("No helpdesks have been configured for this server.");
                embed.setDescription("Mention me with `@Support setup` in a text channel located inside a category with just that channel inside it to setup a new helpdesk.");
                embed.setFooter("Alternatively, use `@Support autosetup` to automatically create a new category and respective channels.", "https://upload.wikimedia.org/wikipedia/en/thumb/3/35/Information_icon.svg/1024px-Information_icon.svg.png");
                event.getChannel().sendMessage(embed.build()).queue(message -> message.delete().queueAfter(15, TimeUnit.SECONDS));
                return;
            } else {
                embed.setColor(Color.GREEN);
                embed.setTitle(event.getGuild().getName());
                embed.setDescription(helpdesks.size() + " helpdesk" + (helpdesks.size() > 1 ? "s" : "") + " available");
            }

            event.getChannel().sendMessage(embed.build()).queue(message -> {
                message.addReaction(Emoji.WRENCH).queue();
                SupportBot.get().getWaiter().waitForEvent(
                        GuildMessageReactionAddEvent.class,
                        e -> event.getAuthor().equals(e.getUser()) && e.getMessageId().equals(message.getId()) && e.getReactionEmote().getName().equals(Emoji.WRENCH),
                        e -> initiate(message, e.getMember()),
                        1, TimeUnit.MINUTES,
                        () -> message.delete().queue()
                );
            });
        } else {
            if (!event.getMessage().getContentRaw().replace(event.getJDA().getSelfUser().getAsMention(), "").trim().equalsIgnoreCase("config")) return;
            event.getChannel().sendMessage(new EmbedBuilder().setTitle("Initializing...").setColor(Color.WHITE).build()).queue(message -> initiate(message, event.getMember()));
        }

        event.getMessage().delete().queue();
    }

    private void initiate(Message message, Member member) {
        ConfigurationMessage configMessage = configurationMessages.stream().filter(streamMessage -> streamMessage.getGuild().equals(member.getGuild())).findFirst().orElse(null);
        if (configMessage != null) {
            message.editMessage(new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("Guild already being configured")
                    .setDescription(configMessage.getMember().getAsMention() + " is already configuring this guild in " + configMessage.getMessage().getTextChannel().getAsMention())
                    .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                    .build()
            ).queue(message1 -> message.delete().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        configurationMessages.add(new ConfigurationMessage(this, message, member));
    }

}
