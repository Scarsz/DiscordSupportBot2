package github.scarsz.discordsupportbot.prompt;

import github.scarsz.discordsupportbot.SupportBot;
import github.scarsz.discordsupportbot.support.Helpdesk;
import github.scarsz.discordsupportbot.support.Ticket;
import lombok.Getter;
import net.dv8tion.jda.core.entities.Message;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public abstract class Prompt {

    @Getter private final UUID helpdesk;
    @Getter private final UUID uuid;
    @Getter private final String name;
    @Getter private final String message;

    public Prompt(UUID helpdesk, UUID uuid, String name, String message) {
        this.helpdesk = helpdesk;
        this.uuid = uuid;
        this.name = name;
        this.message = message;

        System.out.println("Constructed " + this);
    }

    public static List<Prompt> collect(Helpdesk helpdesk) {
        try {
            LinkedList<Prompt> prompts = new LinkedList<>();

            System.out.println("Querying prompts for " + helpdesk);
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("SELECT * FROM `prompts` WHERE `helpdesk` = ?");
            statement.setString(1, helpdesk.getUuid().toString());
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                UUID uuid = UUID.fromString(result.getString("uuid"));
                String name = result.getString("name");
                String message = result.getString("message");
                int typeId = result.getInt("type");
                Type type = Arrays.stream(Type.values()).filter(t -> t.ordinal() == typeId).findFirst().orElse(Type.FREE_RESPONSE);
                String dataJson = result.getString("data");
                JSONObject data = dataJson != null ? (JSONObject) new JSONParser().parse(dataJson) : new JSONObject();

                Prompt prompt;
                switch (type) {
                    case FREE_RESPONSE: default:
                        prompt = new FreeResponsePrompt(helpdesk.getUuid(), uuid, name, message);
                        break;
                    case MULTIPLE_CHOICE:
                        prompt = new MultipleChoicePrompt(helpdesk.getUuid(), uuid, name, message, data);
                        break;
                }
                prompts.add(prompt);
            }

            if (prompts.size() > 0) System.out.println("Collected " + prompts.size() + " prompts for " + helpdesk);
            return prompts;
        } catch (SQLException | ParseException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public boolean exists() {
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("SELECT * FROM `prompts` WHERE `uuid` = ?");
            statement.setString(1, uuid.toString());
            return statement.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public abstract Message send(Ticket ticket);
    public abstract void setQuery(Ticket ticket, int answerIndex);

    public void destroy() {
        // remove prompt from helpdesks
        SupportBot.get().getHelpdesks().forEach(helpdesk -> helpdesk.getPrompts().removeIf(prompt -> prompt == this));

        // remove from database
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("DELETE FROM `prompts` WHERE `uuid` = ?");
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "Prompt{" +
                "helpdesk=" + helpdesk +
                ", name='" + name + '\'' +
                ", message='" + message + '\'' +
                '}';
    }

    public enum Type {

        FREE_RESPONSE,
        MULTIPLE_CHOICE

    }

}
