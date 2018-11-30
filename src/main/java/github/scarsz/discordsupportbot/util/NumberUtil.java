package github.scarsz.discordsupportbot.util;

public class NumberUtil {

    public static String pad(int number) {
        return pad(number, 2);
    }

    public static String pad(int number, int paddingAmount) {
        return String.format("%0" + paddingAmount + "d", number);
    }

}
