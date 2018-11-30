package github.scarsz.discordsupportbot.config;

import github.scarsz.discordsupportbot.SupportBot;
import github.scarsz.discordsupportbot.util.Duration;
import lombok.Getter;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

public class Configuration {

    @Getter private final UUID helpdesk;

    public Configuration(UUID helpdesk) {
        this.helpdesk = helpdesk;

        // create the configuration if it's not already in the database
        if (!exists(helpdesk)) {
            try {
                PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("INSERT INTO `configuration` (helpdesk) VALUES (?)");
                statement.setString(1, helpdesk.toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean exists(UUID helpdesk) {
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("SELECT * FROM `configuration` WHERE `helpdesk` = ?");
            statement.setString(1, helpdesk.toString());
            ResultSet result = statement.executeQuery();
            return result.isBeforeFirst();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean shouldMarkAsSolvedOnAbandon() {
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("SELECT `solvedOnAbandon` FROM `configuration` WHERE `helpdesk` = ?");
            statement.setString(1, helpdesk.toString());
            ResultSet result = statement.executeQuery();
            result.next();
            return result.getBoolean("solvedOnAbandon");
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    public Configuration setMarkAsSolvedOnAbandon(boolean value) {
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("UPDATE `configuration` SET `solvedOnAbandon` = ? WHERE `helpdesk` = ?");
            statement.setInt(1, value ? 1 : 0);
            statement.setString(2, helpdesk.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return this;
    }

    public boolean shouldSendTranscripts() {
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("SELECT `dmTranscripts` FROM `configuration` WHERE `helpdesk` = ?");
            statement.setString(1, helpdesk.toString());
            ResultSet result = statement.executeQuery();
            result.next();
            return result.getBoolean("dmTranscripts");
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    public Configuration setSendTranscripts(boolean value) {
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("UPDATE `configuration` SET `dmTranscripts` = ? WHERE `helpdesk` = ?");
            statement.setInt(1, value ? 1 : 0);
            statement.setString(2, helpdesk.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return this;
    }

    public TextChannel getTranscriptLogChannel() {
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("SELECT `transcriptsChannel` FROM `configuration` WHERE `helpdesk` = ?");
            statement.setString(1, helpdesk.toString());
            ResultSet result = statement.executeQuery();
            result.next();
            String channelId = result.getString("transcriptsChannel");
            if (channelId == null) return null;
            TextChannel channel = SupportBot.get().getJda().getTextChannelById(channelId);
            if (channel != null) return channel;
            setTranscriptLogChannel(null);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    public Configuration setTranscriptLogChannel(String channelId) {
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("UPDATE `configuration` SET `transcriptsChannel` = ? WHERE `helpdesk` = ?");
            statement.setObject(1, channelId != null ? channelId : Types.NULL);
            statement.setString(2, helpdesk.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return this;
    }

    public Role getTicketMasterRole() {
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("SELECT `masterRole` FROM `configuration` WHERE `helpdesk` = ?");
            statement.setString(1, helpdesk.toString());
            ResultSet result = statement.executeQuery();
            result.next();
            String roleId = result.getString("masterRole");
            if (roleId == null) return null;
            Role role = SupportBot.get().getJda().getRoleById(roleId);
            if (role != null) return role;
            setTicketMasterRole(null);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    public Configuration setTicketMasterRole(Role role) {
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("UPDATE `configuration` SET `masterRole` = ? WHERE `helpdesk` = ?");
            statement.setObject(1, role != null ? role.getId() : Types.NULL);
            statement.setString(2, helpdesk.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return this;
    }

    public long getTranscriptExpirationTime() {
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("SELECT `expirationTime` FROM `configuration` WHERE `helpdesk` = ?");
            statement.setString(1, helpdesk.toString());
            ResultSet result = statement.executeQuery();
            result.next();
            String timeFormat = result.getString("expirationTime");
            if (timeFormat == null) return -1;
            long time = Duration.from(timeFormat);
            if (time > 0) return time;
            setTranscriptExpirationTime(null);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }
    public Configuration setTranscriptExpirationTime(String time) {
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("UPDATE `configuration` SET `expirationTime` = ? WHERE `helpdesk` = ?");
            statement.setObject(1, time != null ? time : Types.NULL);
            statement.setString(2, helpdesk.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public String toString() {
        return "Configuration{MASOA=" + shouldMarkAsSolvedOnAbandon() + ", SST=" + shouldSendTranscripts() + ", TLC=" + getTranscriptLogChannel() + "}";
    }
}
