package org.tinyejb.test;

import javax.naming.Context;

import org.tinyejb.core.EJBContainer;
import org.tinyejb.core.IJndiResolver;
import org.tinyejb.core.JBossJndiResolver;
import org.tinyejb.test.ejbs.stateless.InvoiceFacade;
import org.tinyejb.test.ejbs.stateless.InvoiceFacadeHome;

public class ContainerManagedTransactionTest {
	private EJBContainer ejbContainer;
	private Context jndiContext;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {

			new ContainerManagedTransactionTest().test();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void test() throws Exception {
		System.out.println("\n\n**** CMT TESTS****\n");

		initContainer();

		//this bean has remote interfaces only
		InvoiceFacadeHome home = (InvoiceFacadeHome) jndiContext.lookup(InvoiceFacadeHome.COMP_NAME);//note thar we are using the default name (as EJB spec)

		InvoiceFacade bean = home.create();

		//this call must rollback the transaction
		bean.removeInvoiceRollingBack();
		
		try{
			
			bean.removeInvoiceThrowingSystemException();
			
		}catch(Exception ignored ){}
		
		ejbContainer.undeploy();
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
		IJndiResolver jndi = JBossJndiResolver.buildFromJBossDescriptor(CartTest.class.getResourceAsStream("/tinyjboss.xml"));

		ejbContainer.setJndiResolver(jndi);

		/*
		   client code must locate and load ejb-jar.xml from classpath or wherever it can be.
		   Naturally, the file name is irrelevant for TinyEJB container, but it must be, at least, compliant with EJB 2.0 XML DTD or EJB 2.1 schema  
		*/
		ejbContainer.deployModuleFromDescriptor(CartTest.class.getResourceAsStream("/tinyejb-jar.xml"));

	}

}
