package org.tinyejb.test;

import javax.naming.Context;

import org.tinyejb.core.EJBContainer;
import org.tinyejb.core.IJndiResolver;
import org.tinyejb.core.JBossJndiResolver;
import org.tinyejb.test.ejbs.stateful.Cart;
import org.tinyejb.test.ejbs.stateful.CartHome;
import org.tinyejb.test.ejbs.stateful.CartItem;
import org.tinyejb.test.mocks.MockNamingContext;
import org.tinyejb.test.mocks.MockTransacionManager;

public class StatefulConcurrentCallsTest {
	private EJBContainer ejbContainer;
	private Context jndiContext;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {

			new StatefulConcurrentCallsTest().test();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void test() throws Exception {
		System.out.println("\n\n**** CONCURRENT CALLS ON STATEFUL ****\n");

		initContainer();

		//makes stateful calls randomly waits between 0 and 6 seconds, simulating system load
		ejbContainer.setRandomTimeToWaitOnStatefulCalls(6000); //this method exists for test purpose only

		//on this example, we will use remote interfaces
		CartHome home = (CartHome) jndiContext.lookup(CartHome.JNDI_NAME);

		final Cart bean = home.create("Very quick customer");

		//run 2 threads, simulating concurrent calls to stateful bean
		addTask(new Runnable() {

			@Override
			public void run() {
				while (true) {
					try {
						bean.addItem(new CartItem("Book", 1, "Capital, by Thomas Piketty", 50));
						Thread.currentThread().sleep(10);
					} catch (Exception e) {
						throw new IllegalStateException(e);
					}
				}
			}
		});

		addTask(new Runnable() {

			@Override
			public void run() {
				while (true) {
					try {
						int removeCount = bean.removeItem("Capital, by Thomas Piketty");
						System.out.println(removeCount + " items removed");
						Thread.currentThread().sleep(10);
					} catch (Exception e) {
						throw new IllegalStateException(e);
					}
				}
			}
		});

		Thread.sleep(20000); //just to see the results
		
		ejbContainer.undeploy();
	}

	private void addTask(Runnable r) throws Exception {
		Thread t = new Thread(r);
		t.setDaemon(true);
		t.start();
	}

	private void initContainer() throws Exception {
		jndiContext = new MockNamingContext(); //very simple naming context (test purpose only)

		ejbContainer = new EJBContainer(new MockTransacionManager(), jndiContext);

		/*
		 using JBoss deployment descriptor to define jndi names. 
		 if we don't use a special resolver, TinyEJB will bind EJB home proxies on JNDI using names as defined on EJB 2.1 spec.
		 for example:
		      Cart bean's local home would be on 'java:comp/env/ejb/{localHomeSimpleName}' 
		      Cart bean's remote home would be on 'java:comp/env/ejb/{remoteHomeSimpleName}' 
		      
		 In this example, the file is named tinyjboss.xml, but naturally it can be named as you can, but it must be compliant with jboss_4_0.dtd     
		 */
		IJndiResolver jndi = JBossJndiResolver.buildFromJBossDescriptor(CartTest.class.getResourceAsStream("/tinyejb-jboss.xml"));

		ejbContainer.setJndiResolver(jndi);

		/*
		   client code must locate and load ejb-jar.xml from classpath or wherever it can be.
		   Naturally, the file name is irrelevant for TinyEJB container, but it must be, at least, compliant with EJB 2.0 XML DTD or EJB 2.1 schema  
		*/
		ejbContainer.deployModuleFromDescriptor(CartTest.class.getResourceAsStream("/tinyejb-ejb-jar.xml"));

	}

}
