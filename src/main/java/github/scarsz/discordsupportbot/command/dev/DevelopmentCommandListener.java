package github.scarsz.discordsupportbot.command.dev;

import github.scarsz.discordsupportbot.SupportBot;
import github.scarsz.discordsupportbot.config.ConfigurationMessage;
import github.scarsz.discordsupportbot.support.Helpdesk;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import java.util.concurrent.TimeUnit;

public class DevelopmentCommandListener extends ListenerAdapter {

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getAuthor() == null || event.getAuthor().getId() == null || !event.getAuthor().getId().equals("95088531931672576")) return;
        if (event.getMessage() == null) return;

        String command = event.getMessage().getContentRaw().replace(SupportBot.get().getJda().getSelfUser().getAsMention(), "").trim().toLowerCase();

        if (command.equals("stop") || command.equals("shutdown") || command.equals("restart")) {
            for (ConfigurationMessage message : SupportBot.get().getConfigListener().getConfigurationMessages()) message.getMessage().editMessage("Bot is being restarted, this message will no longer work.").complete();
//            event.getMessage().addReaction(Emoji.WHITE_CHECK_MARK).complete();
            event.getMessage().delete().complete();
            Runtime.getRuntime().removeShutdownHook(SupportBot.get().getShutdownThread());
            SupportBot.get().shutdown();
        }

        if (command.equals("purge")) {
            event.getChannel().sendMessage("Purging guild configuration in 5 seconds").queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for (Helpdesk helpdesk : SupportBot.get().getHelpdesksForGuild(event.getGuild())) {
                    helpdesk.destroy();
                }
            }).start();
        }
    }

}
