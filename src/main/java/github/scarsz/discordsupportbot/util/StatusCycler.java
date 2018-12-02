package github.scarsz.discordsupportbot.util;

import github.scarsz.discordsupportbot.SupportBot;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.managers.Presence;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

public class StatusCycler extends Thread {

    private final Presence presence = SupportBot.get().getJda().getPresence();
    private final List<Callable<Game>> statuses = new LinkedList<>();

    private int index = 0;

    public StatusCycler(List<Callable<Game>> statuses) {
        this.statuses.addAll(statuses);
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(15 * 1000);

                if (SupportBot.get().getJda().getStatus() != JDA.Status.CONNECTED) continue;
                if (presence.getGame() != null && presence.getGame().getName().equals(statuses.get(index).call().getName())) break;

                Game newGame = statuses.get(index).call();
                presence.setGame(newGame);

                index++;
                if (index == statuses.size()) index = 0;
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
    }

}
