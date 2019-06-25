package myserver.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import myserver.servlet.sse.EventTarget;
import myserver.servlet.sse.SSEPushManager;

/**
 * サーバープッシュ（SSE）をハンドリングするサーブレット
 * 
 * JS側で以下のよう、Server PUSHを登録するとGETが呼ばれる
 * <code>
 * const eventSource = new EventSource('sse');//http://localhost:5000/sse
 * </code>
 *
 * POSTが呼ばれた時に登録されたターゲットにPUSHを送信する
 * 
 * @author Tom Misawa (riversun.org@gmail.com)
 */
@SuppressWarnings("serial")
public class ServerPushServlet extends HttpServlet {

    private final SSEPushManager mPushSender = new SSEPushManager();

    final ObjectMapper mObjectMapper = new ObjectMapper();

    final class Result {
        public boolean success;
        public String message;
    }

    /**
     * JavaScriptからリクエストを受けたら、リクエスト元のクライアントをPUSH(ブロードキャスト)のターゲットリストに追加する
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        System.out.println(req + "をPUSHブロードキャストのターゲットに追加しました");
        mPushSender.addTarget(new EventTarget(req));
    }

    /**
     * クライアントからPOSTリクエストを受けたら、メッセージをブロードキャストする
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String msgToSend = req.getParameter("message");
        if (msgToSend != null && !msgToSend.isEmpty()) {

        } else {
            msgToSend = "No Message";
        }
        mPushSender.broadcast("message", msgToSend);

        final Result result = new Result();
        result.success = true;
        result.message = "Push message '" + msgToSend + "' is broadcasted.";

        final String CONTENT_TYPE = "application/json; charset=UTF-8";
        resp.setContentType(CONTENT_TYPE);
        final PrintWriter out = resp.getWriter();
        final String json = mObjectMapper.writeValueAsString(result);
        out.println(json);
        out.close();
    }

}
