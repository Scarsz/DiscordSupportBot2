package github.scarsz.discordsupportbot.support;

import github.scarsz.discordsupportbot.SupportBot;
import github.scarsz.discordsupportbot.util.FooterUtil;
import github.scarsz.discordsupportbot.util.NumberUtil;
import github.scarsz.discordsupportbot.util.PermissionUtil;
import lombok.Getter;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.requests.restaction.PermissionOverrideAction;

import java.awt.*;
import java.util.concurrent.TimeUnit;

public class TicketVoiceChannel extends ListenerAdapter {

    @Getter private final Ticket ticket;
    @Getter private final String channelId;

    private final long abandonmentTimer = System.currentTimeMillis() + 60000;

    public TicketVoiceChannel(Ticket ticket, Member cause) {
        this.ticket = ticket;
        this.channelId = ticket.getHelpdesk().getCategory().createVoiceChannel(NumberUtil.pad(ticket.getNumber())).complete().getId();

        PermissionUtil.clearPermissions(getChannel());
        PermissionUtil.copyPermissions(ticket.getChannel(), getChannel());

        ticket.getChannel().sendMessage(new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("A voice channel has been spawned for this ticket.")
                .setDescription("If nobody joins it within a minute, it will be deleted.")
                .setFooter(FooterUtil.make(cause), cause.getUser().getEffectiveAvatarUrl())
                .build()
        ).queue(message -> {
            SupportBot.get().getWaiter().waitForEvent(GuildVoiceJoinEvent.class, event -> event.getChannelJoined().equals(getChannel()), event -> {
                message.delete().queue();
            }, 60, TimeUnit.SECONDS, () -> {
                message.delete().queue();
                ticket.getChannel().sendMessage(new EmbedBuilder()
                        .setColor(Color.RED)
                        .setTitle("Voice channel expired")
                        .setDescription("Nobody joined the spawned voice channel within 60 seconds.")
                        .build()
                ).queue(message2 -> message2.delete().queueAfter(15, TimeUnit.SECONDS));
                destroy();
            });

            message.delete().queueAfter(60, TimeUnit.SECONDS);
        });

        //TODO make this a thread pool or some shit
        new Thread(() -> {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(60));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            checkAbandonment();
        }).start();

        SupportBot.get().getJda().addEventListener(this);
    }

    private void checkAbandonment() {
        if (System.currentTimeMillis() < abandonmentTimer) return;
        if (getChannel() == null) {
            destroy();
            return;
        }

        if (getChannel().getMembers().size() == 0) {
            getChannel().delete().queue(v -> {
                ticket.setVoiceChannel(null);
                ticket.getChannel().sendMessage(new EmbedBuilder()
                        .setColor(Color.RED)
                        .setTitle("Voice channel expired")
                        .setDescription("Everyone left the channel.")
                        .build()
                ).queue(message -> message.delete().queueAfter(15, TimeUnit.SECONDS));
            });
        }
    }

    public void addPermissions(IPermissionHolder holder) {
        PermissionOverrideAction override = holder instanceof Member
                ? getChannel().createPermissionOverride((Member) holder)
                : getChannel().createPermissionOverride((Role) holder);
        override.setAllow(Permission.VOICE_CONNECT, Permission.VOICE_SPEAK).queue();
    }

    public void destroy() {
        System.out.println("Destroying " + this);
        SupportBot.get().getJda().removeEventListener(this);
        if (getChannel() != null) getChannel().delete().complete();
        ticket.setVoiceChannel(null);
    }

    @Override
    public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {
        checkAbandonment();
    }

    @Override
    public void onGuildVoiceMove(GuildVoiceMoveEvent event) {
        checkAbandonment();
    }

    @Override
    public void onTextChannelDelete(TextChannelDeleteEvent event) {
        if (!event.getChannel().equals(ticket.getChannel())) return;
        destroy();
    }

    public VoiceChannel getChannel() {
        return SupportBot.get().getJda().getVoiceChannelById(channelId);
    }

    @Override
    public String toString() {
        return "TicketVoiceChannel{" +
                "ticket=" + ticket +
                ", channelId='" + channelId + '\'' +
                '}';
    }
    
}
