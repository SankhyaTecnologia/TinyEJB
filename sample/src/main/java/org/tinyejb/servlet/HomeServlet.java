package org.tinyejb.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jnp.interfaces.MarshalledValuePair;
import org.tinyejb.test.ejbs.stateless.InvoiceFacade;
import org.tinyejb.test.ejbs.stateless.InvoiceFacadeHome;

public class HomeServlet extends HttpServlet {
	private static final long	serialVersionUID	= 1L;

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		PrintWriter out = response.getWriter();
		try {
			Object doLookup = InitialContext.doLookup(InvoiceFacadeHome.COMP_NAME);
			InvoiceFacadeHome home;
			if(doLookup instanceof MarshalledValuePair){
				home = (InvoiceFacadeHome) ((MarshalledValuePair) doLookup).get();
			}else{
				home = (InvoiceFacadeHome) doLookup;
			}
			InvoiceFacade facade = home.create();

			out.println("<html>");
			out.println("<body>");
			out.println("Primeira servlet");
			out.println(facade.insertInvoice(80, 10));
			out.println("</body>");
			out.println("</html>");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
