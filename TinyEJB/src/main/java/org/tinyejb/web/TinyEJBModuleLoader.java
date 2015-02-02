package org.tinyejb.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.tinyejb.core.EJBContainer;
import org.tinyejb.core.IJndiResolver;
import org.tinyejb.core.JBossJndiResolver;

public class TinyEJBModuleLoader implements ServletContextListener {
	private EJBContainer	ejbContainer;

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		ejbContainer.undeploy();

	}

	@Override
	public void contextInitialized(ServletContextEvent event) {
		try {
			ejbContainer = new EJBContainer();
			List<InputStream> tinyejbJboss = getResourceInputStream("META-INF/tinyejb-jboss.xml");
			List<InputStream> tinyejbEjb = getResourceInputStream("META-INF/tinyejb-ejb-jar.xml");
			IJndiResolver jndi = JBossJndiResolver.buildFromJBossDescriptor(tinyejbJboss);
			ejbContainer.setJndiResolver(jndi);
			ejbContainer.deployModuleFromDescriptor(tinyejbEjb);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	List<InputStream> getResourceInputStream(String resource) throws URISyntaxException, IOException {
		ClassLoader cl = EJBContainer.class.getClassLoader();
		Enumeration<URL> resources = cl.getResources(resource);
		List<InputStream> in = new ArrayList<InputStream>();
		while (resources.hasMoreElements()) {
			URL url = resources.nextElement();
			in.add(url.openStream());
		}
		return in;
	}
}
