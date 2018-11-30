package github.scarsz.discordsupportbot.http;

import github.scarsz.discordsupportbot.http.servlet.AssetServlet;
import github.scarsz.discordsupportbot.http.servlet.DiscordServlet;
import github.scarsz.discordsupportbot.http.servlet.IndexServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpServletRequest;

public class HttpServer extends Server {

    public HttpServer() {
        super(38005);

        ServletContextHandler handler = new ServletContextHandler(this, "/");
        handler.addServlet(AssetServlet.class, "/assets/*");
        handler.addServlet(DiscordServlet.class, "/discord");
        handler.addServlet(IndexServlet.class, "/");
        handler.addEventListener(new ServletRequestListener() {
            @Override
            public void requestInitialized(ServletRequestEvent sre) {
                if (!(sre.getServletRequest() instanceof HttpServletRequest)) return;
                HttpServletRequest request = (HttpServletRequest) sre.getServletRequest();
                System.out.println("HTTP: " + request.getMethod() + "\t" + request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : ""));
            }
            @Override
            public void requestDestroyed(ServletRequestEvent sre) {}
        });

        setStopAtShutdown(true);

        try {
            start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("HTTP server listening @ " + getURI().getPort());
    }

    @Override
    public void destroy() {
        try {
            stop();
            super.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
