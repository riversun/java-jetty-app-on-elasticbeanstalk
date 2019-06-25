package myserver;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.tomcat.util.scan.StandardJarScanner;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.jsp.JettyJspServlet;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.webapp.Configuration;

/**
 * JettyでJSPを有効にするヘルパー
 * 
 * @author Tom Misawa (riversun.org@gmail.com)
 *
 */
public class JspInitiallizer {
    private JspInitiallizer() {
    }

    public static void enableJSP(Server jettyServer, File jspCompilationDir, String webrootPath) {

        ServletContextHandler servletContextHandler = null;

        // jetty serverからServletContextHandlerを取得する
        final Handler[] handlers = jettyServer.getHandlers();
        for (Handler handler : handlers) {
            // handlerがServletContextHandlerら、ServletContextHandlerに対してjspを有効にする
            if (handler instanceof ServletContextHandler) {
                servletContextHandler = (ServletContextHandler) handler;
            }
        }

        if (servletContextHandler == null) {
            throw new RuntimeException("servletContextHandler is not found.Please call #enable after server.setHandler(servletContextHandler);");
        }

        // アノテーションにつかう
        final Configuration.ClassList classlist = Configuration.ClassList.setServerDefault(jettyServer);
        classlist.addBefore("org.eclipse.jetty.webapp.JettyWebXmlConfiguration",
                "org.eclipse.jetty.annotations.AnnotationConfiguration");

        enableJspServlet(servletContextHandler, jspCompilationDir);
        enableDefaultServlet(servletContextHandler, jspCompilationDir, webrootPath);
    }

    /**
     * JspをハンドリングするためのJspServletを追加する
     * 
     * @param servletContextHandler
     * @param jspCompilationDir
     */
    private static void enableJspServlet(ServletContextHandler servletContextHandler, File jspCompilationDir) {
        // コンパイル済みのJSPの生成場所を記憶する
        servletContextHandler.setAttribute("javax.servlet.context.tempdir", jspCompilationDir);

        // JSTLに必要になるのでContext用のクラスローダーをセットする。JSP用はシステムのクラスローダーではないクラスローダをつかう。
        ClassLoader jspClassLoader = new URLClassLoader(new URL[0], JspInitiallizer.class.getClassLoader());
        servletContextHandler.setClassLoader(jspClassLoader);
        servletContextHandler.addBean(new JspStarter(servletContextHandler));

        // jspリクエストを受け取るサーブレットを定義。名前は"jsp"である必要がある。
        ServletHolder holderJsp = new ServletHolder("jsp", JettyJspServlet.class);
        holderJsp.setInitOrder(0);

        // 以下はinitParameterの設定
        // https://www.eclipse.org/jetty/documentation/9.4.x/configuring-jsp.html

        // デバッグレベル ALL,DEBUG,INFO,WARN,OFF.
        holderJsp.setInitParameter("logVerbosityLevel", "OFF");
        holderJsp.setInitParameter("fork", "false");
        holderJsp.setInitParameter("xpoweredBy", "false");
        holderJsp.setInitParameter("compilerTargetVM", "1.8");
        holderJsp.setInitParameter("compilerSourceVM", "1.8");

        // Do you want to keep the generated Java files around?
        holderJsp.setInitParameter("keepgenerated", "true");
        servletContextHandler.addServlet(holderJsp, "*.jsp");
    }

    /**
     * 静的コンテンツふくむその他のコンテンツをハンドリングするためのDefaultServletを追加する
     * 
     * @param servletContextHandler
     * @param jspCompilationDir
     */
    private static void enableDefaultServlet(ServletContextHandler servletContextHandler, File jspCompilationDir, String webRootPath) {
        servletContextHandler.setContextPath("/");
        servletContextHandler.setResourceBase(getWebRootResourceUri(webRootPath));

        // "/"はDefaultServletに拾わせ、静的コンテンツもDefaultServletにやらせる
        ServletHolder holderDefault = new ServletHolder("default", DefaultServlet.class);

        // init parameterについては以下
        // http://www.eclipse.org/jetty/javadoc/9.4.12.v20180830/org/eclipse/jetty/servlet/DefaultServlet.html

        holderDefault.setInitParameter("resourceBase", getWebRootResourceUri(webRootPath));
        holderDefault.setInitParameter("dirAllowed", "false");
        holderDefault.setInitParameter("cacheControl", "no-store,no-cache,must-revalidate");
        servletContextHandler.addServlet(holderDefault, "/");
    }

    static String getWebRootResourceUri(String webrootPath) {
        if (!webrootPath.startsWith("/")) {
            webrootPath = "/" + webrootPath;
        }

        if (!webrootPath.endsWith("/")) {
            webrootPath = webrootPath + "/";
        }

        final URL webRootUri = JspInitiallizer.class.getResource(webrootPath);

        if (webRootUri == null) {
            throw new RuntimeException("WebRoot dir not found path=" + webrootPath);
        }

        try {
            return webRootUri.toURI().toASCIIString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

    }

    static class JspStarter extends AbstractLifeCycle implements ServletContextHandler.ServletContainerInitializerCaller {
        JettyJasperInitializer sci;
        ServletContextHandler context;

        public JspStarter(ServletContextHandler context) {
            this.sci = new JettyJasperInitializer();
            this.context = context;
            this.context.setAttribute("org.apache.tomcat.JarScanner", new StandardJarScanner());
        }

        @Override
        protected void doStart() throws Exception {
            ClassLoader old = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(context.getClassLoader());
            try {
                sci.onStartup(null, context.getServletContext());
                super.doStart();
            } finally {
                Thread.currentThread().setContextClassLoader(old);
            }
        }
    }

}
