package org.tinyejb.test;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.ejb.EJBException;
import javax.naming.Context;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.tinyejb.core.EJBContainer;
import org.tinyejb.core.IJndiResolver;
import org.tinyejb.core.JBossJndiResolver;
import org.tinyejb.core.ResourceHolder;
import org.tinyejb.test.ejbs.stateless.InvoiceFacade;
import org.tinyejb.test.ejbs.stateless.InvoiceFacadeHome;
import org.tinyejb.test.mocks.MockNamingContext;
import org.tinyejb.test.mocks.MockTransacionManager;

@RunWith(Parameterized.class)
public class EJBStatelessTest {
	private final static Random		random	= new Random();
	private static EJBContainer		ejbContainer;
	private static Context			jndiContext;
	private static InvoiceFacade	bean;

	private double					totalProductsPrice;
	private double					importFees;
	private double					expected;

	public EJBStatelessTest(double totalProductsPrice, double importFees, double expected) {
		this.totalProductsPrice = totalProductsPrice;
		this.importFees = importFees;
		this.expected = expected;
	}

	@Parameters(name = "{index}: Invoice {0}, {1} ={2}")
	public static Iterable<Double[]> data() {
		List<Double[]> tests = new ArrayList<Double[]>();
		for (int i = 0; i < 50; i++) {
			double totalProductsPrice = random.nextInt(30000);
			double importFees = random.nextInt(1000);
			double resultShouldBe = totalProductsPrice + (totalProductsPrice * importFees / 100);
			tests.add(new Double[] { totalProductsPrice, importFees, resultShouldBe });
		}

		return tests;
	}

	@Test
	public void test_insertInvoice() throws EJBException, Exception {
		// assertEquals(expected, totalProductsPrice + (totalProductsPrice *
		// importFees / 100),0);
		assertEquals(expected, bean.insertInvoice(totalProductsPrice, importFees), Double.MIN_VALUE);
	}

	@BeforeClass
	public static void initContainer() throws Exception {
		jndiContext = new MockNamingContext(); // very simple naming context
												// (test purpose only)

		ResourceHolder.setHolder(new MockTransacionManager(), jndiContext);
		ejbContainer = new EJBContainer();

		/*
		 * using JBoss deployment descriptor to define jndi names. if we don't
		 * use a special resolver, TinyEJB will bind EJB home proxies on JNDI
		 * using names as defined on EJB 2.1 spec. for example: Cart bean's
		 * local home would be on 'java:comp/env/ejb/{localHomeSimpleName}' Cart
		 * bean's remote home would be on
		 * 'java:comp/env/ejb/{remoteHomeSimpleName}'
		 * 
		 * In this example, the file is named tinyjboss.xml, but naturally it
		 * can be named as you can, but it must be compliant with jboss_4_0.dtd
		 */
		IJndiResolver jndi = JBossJndiResolver.buildFromJBossDescriptor(CartTest.class.getResourceAsStream("/tinyejb-jboss.xml"));

		ejbContainer.setJndiResolver(jndi);

		/*
		 * client code must locate and load ejb-jar.xml from classpath or
		 * wherever it can be. Naturally, the file name is irrelevant for
		 * TinyEJB container, but it must be, at least, compliant with EJB 2.0
		 * XML DTD or EJB 2.1 schema
		 */
		ejbContainer.deployModuleFromDescriptor(CartTest.class.getResourceAsStream("/tinyejb-ejb-jar.xml"));

		//		ejbContainer.setUseSingletonForStateless(true);

		InvoiceFacadeHome home = (InvoiceFacadeHome) jndiContext.lookup(InvoiceFacadeHome.COMP_NAME);// note thar we are using
																										// the default name (as
																										// EJB spec)

		bean = home.create();

	}

	@AfterClass
	public static void tearDownOnce() {
		ejbContainer.undeploy();
	}

}
