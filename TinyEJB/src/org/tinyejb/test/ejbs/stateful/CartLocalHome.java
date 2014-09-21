package org.tinyejb.test.ejbs.stateful;

import javax.ejb.EJBLocalHome;

/**
 * Local home interface for Cart bean
 * 
 * @author Cláudio Gualberto
 * 20/09/2014
 *
 */
public interface CartLocalHome extends EJBLocalHome {
	public static final String COMP_NAME = "java:comp/env/ejb/CartLocalHome";
	public static final String JNDI_NAME = "CartLocalHome";

	public CartLocal create(String customerName) throws javax.ejb.CreateException;
}
