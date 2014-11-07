package org.tinyejb.test;

import java.util.Random;

import javax.naming.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinyejb.core.EJBContainer;
import org.tinyejb.core.IJndiResolver;
import org.tinyejb.core.JBossJndiResolver;
import org.tinyejb.core.ResourceHolder;
import org.tinyejb.test.ejbs.stateless.InvoiceFacade;
import org.tinyejb.test.ejbs.stateless.InvoiceFacadeHome;
import org.tinyejb.test.mocks.MockNamingContext;
import org.tinyejb.test.mocks.MockTransacionManager;

public class InvoiceTest {
	private final static Logger	LOGGER	= LoggerFactory.getLogger(InvoiceTest.class);
	private EJBContainer		ejbContainer;
	private Context				jndiContext;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {

			new InvoiceTest().test();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void test() throws Exception {
		LOGGER.debug("\n\n**** CONCURRENT CALLS ON STATELESS****\n");

		initContainer();

		//uncomment the follwing line to use singleton stateless beans, so you will see concurrency errors
		//ejbContainer.setUseSingletonForStateless(true);

		//this bean has remote interfaces only
		InvoiceFacadeHome home = (InvoiceFacadeHome) jndiContext.lookup(InvoiceFacadeHome.COMP_NAME);//note thar we are using the default name (as EJB spec)

		final InvoiceFacade bean = home.create();

		LOGGER.debug("Total with fees: " + bean.insertInvoice(30000, 15));

		LOGGER.debug("Inserting invoices concurrently...");

		/*
		  if we are using default configuration (compliant with EJB spec thread-safe rules) we won't see any errors.
		  but when singleton stateless is turned on, we can see errors, because the bean implementation is not taking care of synchronizing the acess to
		  class atributes.
		  
		  So, if you're using singleton stateless beans, don't use class attributes, unless you synchronize the access to them by yourself
		*/
		addTask(new ConcurrentTask(bean, 30000));
		addTask(new ConcurrentTask(bean, 16000));

		Thread.sleep(15000); //just to see the results

		ejbContainer.undeploy();
	}

	private static class ConcurrentTask implements Runnable {
		private Random			random	= new Random();
		private InvoiceFacade	bean;

		ConcurrentTask(InvoiceFacade bean, int maxRandomValue) {
			this.bean = bean;
		}

		@Override
		public void run() {

			while (true) {
				try {
					double totalProductsPrice = random.nextInt(30000);
					double importFees = 15;
					double resultShouldBe = totalProductsPrice + (totalProductsPrice * importFees / 100);

					if (bean.insertInvoice(totalProductsPrice, importFees) != resultShouldBe) {
						throw new IllegalStateException("Wrong Result !!");
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

	}

	private void addTask(Runnable r) throws Exception {
		Thread t = new Thread(r);
		t.setDaemon(true);
		t.start();
	}

	private void initContainer() throws Exception {
		jndiContext = new MockNamingContext(); //very simple naming context (test purpose only)

		ResourceHolder.setHolder(new MockTransacionManager(), jndiContext);
		ejbContainer = new EJBContainer();

		/*
		 using JBoss deployment descriptor to define jndi names. 
		 if we don't use a special resolver, TinyEJB will bind EJB home proxies on JNDI using names as defined on EJB 2.1 spec.
		 for example:
		      Cart bean's local home would be on 'java:comp/env/ejb/{localHomeSimpleName}' 
		      Cart bean's remote home would be on 'java:comp/env/ejb/{remoteHomeSimpleName}' 
		      
		 In this example, the file is named tinyjboss.xml, but naturally it can be named as you can, but it must be compliant with jboss_4_0.dtd     
		 */
		IJndiResolver jndi = JBossJndiResolver.buildFromJBossDescriptor(getClass().getResourceAsStream("/tinyejb-jboss.xml"));

		ejbContainer.setJndiResolver(jndi);

		/*
		   client code must locate and load ejb-jar.xml from classpath or wherever it can be.
		   Naturally, the file name is irrelevant for TinyEJB container, but it must be, at least, compliant with EJB 2.0 XML DTD or EJB 2.1 schema  
		*/
		ejbContainer.deployModuleFromDescriptor(getClass().getResourceAsStream("/tinyejb-ejb-jar.xml"));

	}

}
