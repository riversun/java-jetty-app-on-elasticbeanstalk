package myserver;

import java.io.File;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import myserver.servlet.ApiServlet;
import myserver.servlet.ServerPushServlet;
import myserver.servlet.ShowPersonServlet;

/**
 * サーバー起動のメインクラス
 * 
 * @author Tom Misawa (riversun.org@gmail.com)
 *
 */
public class StartServer {

    public static void main(String[] args) {
        new StartServer().start();
    }

    public void start() {

        final int PORT = 5000;

        final ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);

        servletContextHandler.addServlet(ShowPersonServlet.class, "/show");

        final ServletHolder apiServletHolder = servletContextHandler.addServlet(ApiServlet.class, "/api");
        apiServletHolder.setAsyncSupported(true);

        final ServletHolder pushServletHolder = servletContextHandler.addServlet(ServerPushServlet.class, "/sse");
        pushServletHolder.setAsyncSupported(true);

        final Server jettyServer = new Server();
        jettyServer.setHandler(servletContextHandler);

        final HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion(false);

        final HttpConnectionFactory httpConnFactory = new HttpConnectionFactory(httpConfig);
        final ServerConnector httpConnector = new ServerConnector(jettyServer, httpConnFactory);
        httpConnector.setPort(PORT);
        jettyServer.setConnectors(new Connector[] { httpConnector });

        // JSPのコンパイル結果を保存する位置ディレクトリを作る
        final File jspOutputDir = new File(new File(System.getProperty("java.io.tmpdir")).toString(), "jsp");

        JspInitiallizer.enableJSP(jettyServer, jspOutputDir, "webroot");

        try {
            jettyServer.start();
            System.out.println("Server started on port" + PORT);
            jettyServer.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}