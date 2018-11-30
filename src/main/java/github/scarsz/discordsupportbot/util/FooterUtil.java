package github.scarsz.discordsupportbot.util;

import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;

public class FooterUtil {

    public static String make(User user) {
        return user.getName() + "#" + user.getDiscriminator();
    }

    public static String make(Member member) {
        return make(member.getUser());
    }

}
