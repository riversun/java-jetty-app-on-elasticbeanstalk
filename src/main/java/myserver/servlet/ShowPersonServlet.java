package myserver.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * アクセスされたら、変数をRequestスコープに保持し、View(JSP)にディスパッチするサーブレット
 * 
 * @author Tom Misawa (riversun.org@gmail.com)
 *
 */
@SuppressWarnings("serial")
public class ShowPersonServlet extends HttpServlet {

    public static final class Person {

        public String name;

        public String address;

        public Person(String name, String address) {
            super();
            this.name = name;
            this.address = address;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        final List<Person> personList = new ArrayList<Person>();

        personList.add(new Person("tom", "tokyo"));
        personList.add(new Person("jack", "kanagawa"));
        personList.add(new Person("mike", "kyoto"));
        personList.add(new Person("erica", "nara"));

        req.setAttribute("personList", personList);
        req.setAttribute("mode", "MODE_01");

        RequestDispatcher dispatcher = req.getRequestDispatcher("show.jsp");
        dispatcher.forward(req, resp);

    }

}