package github.scarsz.discordsupportbot.util;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.PermissionOverride;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.requests.restaction.PermissionOverrideAction;

import java.util.HashSet;
import java.util.Set;

public class PermissionUtil {

    public static void clearPermissions(Channel channel) {
        channel.getPermissionOverrides().forEach(override -> override.delete().complete());
    }

    public static void copyPermissions(TextChannel source, TextChannel destination) {
        for (PermissionOverride oldOverride : source.getPermissionOverrides()) {
            PermissionOverrideAction newOverride;
            if (oldOverride.getRole() == null) {
                newOverride = destination.createPermissionOverride(oldOverride.getMember());
            } else {
                newOverride = destination.createPermissionOverride(oldOverride.getRole());
            }

            newOverride.setAllow(oldOverride.getAllowed()).setDeny(oldOverride.getDenied()).complete();
        }
    }

    public static void copyPermissions(TextChannel source, VoiceChannel destination) {
        for (PermissionOverride oldOverride : source.getPermissionOverrides()) {
            PermissionOverrideAction newOverride;
            if (oldOverride.isMemberOverride()) {
                PermissionOverride existing = destination.getPermissionOverride(oldOverride.getMember());
                if (existing != null) existing.delete().complete();

                newOverride = destination.putPermissionOverride(oldOverride.getMember());
            } else {
                PermissionOverride existing = destination.getPermissionOverride(oldOverride.getRole());
                if (existing != null) existing.delete().complete();

                newOverride = destination.putPermissionOverride(oldOverride.getRole());
            }

            Set<Permission> allowed = new HashSet<>();
            if (oldOverride.getAllowed().contains(Permission.MESSAGE_READ)) {
                allowed.add(Permission.VIEW_CHANNEL);
                allowed.add(Permission.VOICE_CONNECT);
            }
            if (oldOverride.getAllowed().contains(Permission.MESSAGE_WRITE)) {
                allowed.add(Permission.VOICE_SPEAK);
                allowed.add(Permission.VOICE_USE_VAD);
            }
            newOverride.setAllow(allowed).queue(override -> {
                // temp voice permissions
                //source.sendMessage("```\n" + (override.isRoleOverride() ? override.getRole() : override.getMember()) + ":\n allowed: " + override.getAllowed() + "\n denied: " + override.getDenied() + "\n```").queue();
            });
        }
    }

}
