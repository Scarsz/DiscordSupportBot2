package github.scarsz.discordsupportbot.http.servlet;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.http.MimeTypes;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

public class AssetServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String fileName = "www/" + request.getRequestURI().substring(1).replace("..", ".");
        FileInputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            inputStream = new FileInputStream(fileName);
//            if (fileName.endsWith(".ico")) response.setContentType("image/x-icon");
//            if (fileName.endsWith(".css")) response.setContentType("text/css");
//            if (fileName.endsWith(".svg")) response.setContentType("image/svg+xml");
//            if (fileName.endsWith(".png")) response.setContentType("image/png");
//            if (fileName.endsWith(".png")) response.setContentType("image/png");
//            if (fileName.endsWith(".js")) response.setContentType("application/javascript");
//            if (fileName.endsWith(".mp4")) response.setContentType("video/mp4");
//            if (fileName.endsWith(".otf")) response.setContentType("application/octet-stream");
//            if (fileName.endsWith(".eot")) response.setContentType("application/octet-stream");
//            if (fileName.endsWith(".ttf")) response.setContentType("application/octet-stream");
//            if (fileName.endsWith(".woff")) response.setContentType("application/octet-stream");
//            if (fileName.endsWith(".woff2")) response.setContentType("application/octet-stream");
            response.setContentType(MimeTypes.getDefaultMimeByExtension(fileName));
            outputStream = response.getOutputStream();
            IOUtils.copy(inputStream, outputStream);
        } finally {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
        }
    }

}
