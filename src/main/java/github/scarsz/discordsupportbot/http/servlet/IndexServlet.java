package github.scarsz.discordsupportbot.http.servlet;

import github.scarsz.discordsupportbot.SupportBot;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class IndexServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        String queryString = req.getQueryString();
        boolean blank = StringUtils.isBlank(queryString);
        if (blank) {
            handleIndex(resp);
        } else {
            UUID uuid;
            try {
                uuid = UUID.fromString(queryString);
            } catch (Exception e) {
                resp.setStatus(404);
                try {
                    resp.getWriter().println("unknown uuid");
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                return;
            }

            System.out.println("Handling transcript request for " + queryString);
            handleTranscriptRequest(resp, uuid);
        }
    }

    private static final File indexFile = new File("www/index.html");
    private void handleIndex(HttpServletResponse resp) {
        try {
            resp.getWriter().println(FileUtils.readFileToString(indexFile, Charset.forName("UTF-8")));
        } catch (IOException e) {
            try {
                e.printStackTrace();
                for (StackTraceElement element : e.getStackTrace()) {
                    resp.getWriter().println(element);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void handleTranscriptRequest(HttpServletResponse resp, UUID uuid) {
        try {
            PreparedStatement statement = SupportBot.get().getDatabase().prepareStatement("SELECT * FROM `transcripts` WHERE `uuid` = ?");
            statement.setString(1, uuid.toString());
            ResultSet result = statement.executeQuery();
            if (!result.isBeforeFirst()) {
                resp.setStatus(404);
                resp.getWriter().println("unknown uuid");
            } else {
                while (result.next()) {
                    String[] lines = result.getString("lines").split("\n");
                    for (String line : lines) {
                        resp.setStatus(200);
                        resp.getWriter().println(line);
                    }
                }
            }
        } catch (IOException | SQLException e) {
            try {
                for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                    resp.getWriter().println(stackTraceElement.toString());
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

}
