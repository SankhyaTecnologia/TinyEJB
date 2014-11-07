package org.tinyejb.web;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.tinyejb.core.EJBContainer;
import org.tinyejb.core.IJndiResolver;
import org.tinyejb.core.JBossJndiResolver;


public class ContextLoaderListener implements ServletContextListener {
	private EJBContainer ejbContainer;
	@Override
	public void contextDestroyed(ServletContextEvent event) {
		ejbContainer.undeploy();
		
	}

	@Override
	public void contextInitialized(ServletContextEvent event) {
		 try {
			ejbContainer = new EJBContainer();
			IJndiResolver jndi = JBossJndiResolver.buildFromJBossDescriptor(getClass().getResourceAsStream("/tinyejb-jboss.xml"));
			ejbContainer.setJndiResolver(jndi);
			ejbContainer.deployModuleFromDescriptor(getClass().getResourceAsStream("/tinyejb-ejb-jar.xml"));
		}  catch (Exception e) {
			e.printStackTrace();
		}
	}
}
