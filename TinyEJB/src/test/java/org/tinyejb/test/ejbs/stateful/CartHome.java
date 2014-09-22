package org.tinyejb.test.ejbs.stateful;

import javax.ejb.EJBHome;

/**
 * Remote home interface for Cart bean
 * 
 * @author Cláudio Gualberto
 * 20/09/2014
 *
 */
public interface CartHome extends EJBHome {
	public static final String COMP_NAME = "java:comp/env/ejb/CartHome";
	public static final String JNDI_NAME = "CartHome";

	public Cart create(String customerName) throws javax.ejb.CreateException;
}
