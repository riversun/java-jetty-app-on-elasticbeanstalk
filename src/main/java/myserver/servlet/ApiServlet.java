package myserver.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * パラメーターに応じてJSONを返すサンプルサーブレット
 * 
 * @author Tom Misawa (riversun.org@gmail.com)
 *
 */
@SuppressWarnings("serial")
public class ApiServlet extends HttpServlet {

    final ObjectMapper mObjectMapper = new ObjectMapper();

    final class Result {
        public boolean success;
        public String message;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        final String paramMessage = req.getParameter("message");

        final Result result = new Result();
        result.success = true;
        result.message = "You say '" + paramMessage + "'";

        // Enable CORS(Cross-Origin Resource Sharing)
        resp.addHeader("Access-Control-Allow-Origin", "*");
        resp.addHeader("Access-Control-Allow-Headers", "Content-Type");

        final String CONTENT_TYPE = "application/json; charset=UTF-8";
        resp.setContentType(CONTENT_TYPE);

        final PrintWriter out = resp.getWriter();

        final String json = mObjectMapper.writeValueAsString(result);

        out.println(json);
        out.close();

    }

}