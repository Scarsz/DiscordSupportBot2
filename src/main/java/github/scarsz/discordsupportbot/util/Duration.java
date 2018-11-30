package github.scarsz.discordsupportbot.util;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Duration {

    private static final Pattern PATTERN = Pattern.compile("(\\d+)((?:w(?:eek(?:s)?)?)|(?:d(?:ay(?:s)?)?)|(?:h(?:our(?:s)?)?)|(?:m(?:in(?:ute(?:s)?)?)?)|(?:s(?:ec(?:ond(?:s)?)?)?))");

    public static long from(String duration) {
        Matcher matcher = PATTERN.matcher(duration);
        if (!matcher.matches()) return -1;

        long time = 0;

        while (matcher.find()) {
            try {
                int number = Integer.parseInt(matcher.group(1));
                switch (matcher.group(2).toLowerCase().substring(0, 1)) {
                    case "h": time += TimeUnit.HOURS.toMillis(number);
                    case "d": time += TimeUnit.DAYS.toMillis(number);
                    case "w": time += TimeUnit.DAYS.toMillis(number * 7);
                    case "m": time += TimeUnit.DAYS.toMillis(number * 30);
                }
            } catch (IllegalArgumentException ignored) {}
        }

        return time;
    }

}
