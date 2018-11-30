package github.scarsz.discordsupportbot.prompt;

import github.scarsz.discordsupportbot.support.Ticket;
import net.dv8tion.jda.core.entities.Message;
import org.json.simple.JSONObject;

import java.util.UUID;

public class MultipleChoicePrompt extends Prompt {

    public MultipleChoicePrompt(UUID helpdesk, UUID uuid, String name, String message, JSONObject data) {
        super(helpdesk, uuid, name, message);
    }

    @Override
    public Message send(Ticket ticket) {
        return null;
    }

    @Override
    public void setQuery(Ticket ticket, int answerIndex) {

    }

}
