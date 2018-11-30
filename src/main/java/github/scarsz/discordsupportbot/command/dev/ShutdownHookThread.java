package github.scarsz.discordsupportbot.command.dev;

import github.scarsz.discordsupportbot.SupportBot;

public class ShutdownHookThread extends Thread {

    @Override
    public void run() {
        SupportBot.get().shutdown();
    }

}
