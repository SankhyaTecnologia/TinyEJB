package org.tinyejb.test.ejbs.stateless;

import javax.ejb.EJBHome;

public interface InvoiceFacadeHome extends EJBHome{
	public static final String COMP_NAME = "java:/comp/env/ejb/InvoiceFacadeHome";
	public static final String JNDI_NAME = "InvoiceFacadeHome";

	public InvoiceFacade create() throws javax.ejb.CreateException;
}
