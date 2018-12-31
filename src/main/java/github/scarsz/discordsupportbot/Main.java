package github.scarsz.discordsupportbot;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

public class Main {

    public static void main(String[] args) throws IOException {
        String token = FileUtils.readFileToString(new File(".token"), Charset.forName("UTF-8")).replace("\n", "").replaceAll("/[^A-Za-z0-9.]/", "");
        if (StringUtils.isBlank(token)) {
            System.out.println("No bot token provided");
            System.exit(1);
        }

        try {
            new SupportBot(token);
        } catch (Exception e) {
            System.err.println("Failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
