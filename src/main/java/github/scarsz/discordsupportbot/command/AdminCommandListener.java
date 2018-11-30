package github.scarsz.discordsupportbot.command;

import github.scarsz.discordsupportbot.SupportBot;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AdminCommandListener extends ListenerAdapter {

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getAuthor() == null || event.getAuthor().getId() == null || event.getMessage() == null) return;
        String[] commandRaw = event.getMessage().getContentRaw().replace(SupportBot.get().getJda().getSelfUser().getAsMention(), "").trim().split(" ");
        if (commandRaw.length < 2) return;
        String command = commandRaw[0];
        List<String> args = Arrays.stream(commandRaw).skip(1).collect(Collectors.toList());
        Arrays.stream(getClass().getMethods())
                .filter(method -> method.getName().equalsIgnoreCase(command))
                .findFirst().ifPresent(method -> {
            try {
                boolean isAdmin = event.getMember().hasPermission(Permission.ADMINISTRATOR);
                if (!isAdmin) {
                    event.getChannel().sendMessage(new EmbedBuilder()
                            .setColor(Color.RED)
                            .setTitle("Insufficient permission")
                            .setDescription("You must be a guild administrator to use these commands.")
                            .build()
                    ).queue();
                    event.getMessage().delete().queue();
                } else {
                    method.invoke(this, event, args);
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        });
    }

}
