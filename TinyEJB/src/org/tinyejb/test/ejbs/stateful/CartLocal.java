package org.tinyejb.test.ejbs.stateful;

import javax.ejb.EJBLocalObject;

/**
 * Local business interface for Cart bean
 * 
 * @author Cl�udio Gualberto
 * 20/09/2014
 *
 */
public interface CartLocal extends EJBLocalObject {
	void addItem(CartItem ci);

	void listItems();

	double getTotalAmount();
	
	int removeItem(String item);

}
