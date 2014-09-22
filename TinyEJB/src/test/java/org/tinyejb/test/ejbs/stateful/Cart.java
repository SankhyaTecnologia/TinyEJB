package org.tinyejb.test.ejbs.stateful;

import java.rmi.RemoteException;

import javax.ejb.EJBObject;

/**
 * Remote business interface for Cart bean
 * 
 * @author Cláudio Gualberto
 * 20/09/2014
 *
 */
public interface Cart extends EJBObject {
	void addItem(CartItem ci) throws Exception, RemoteException;

	void listItems() throws Exception, RemoteException;

	double getTotalAmount() throws Exception, RemoteException;

	int removeItem(String item) throws Exception, RemoteException;
}
