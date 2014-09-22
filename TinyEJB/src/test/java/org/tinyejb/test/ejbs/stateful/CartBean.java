package org.tinyejb.test.ejbs.stateful;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.ejb.EJBException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.ejb.SessionSynchronization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CartBean implements SessionBean, SessionSynchronization {
	private final static Logger LOGGER = LoggerFactory.getLogger(CartBean.class);
	private SessionContext ctx;
	private List<CartItem> cartItems;
	private String customerName;

	public void ejbCreate(String customerName) throws EJBException {
		LOGGER.debug("CartBean.ejbCreate(customerName) = (" + customerName + ")");
		this.customerName = customerName;
		this.cartItems = new ArrayList<CartItem>();
	}

	@Override
	public void ejbActivate() throws EJBException, RemoteException {
		LOGGER.debug("CartBean.ejbActivate()");
	}

	@Override
	public void ejbPassivate() throws EJBException, RemoteException {
		LOGGER.debug("CartBean.ejbPassivate()");
	}

	@Override
	public void ejbRemove() throws EJBException, RemoteException {
		LOGGER.debug("CartBean.ejbRemove()");
	}

	@Override
	public void setSessionContext(SessionContext ctx) throws EJBException, RemoteException {
		this.ctx = ctx;
	}

	public void addItem(CartItem item) {
		LOGGER.debug("CartBean.addItem()");
		cartItems.add(item);
	}

	public void listItems() {
		LOGGER.debug("CartBean.listItems()");
		
		LOGGER.debug("listing shopping cart for customer " + customerName);
		int i = 1;
		for (CartItem item : cartItems) {
			LOGGER.debug("#" + i + " - " + item);
			i++;
		}
	}
	
	public int removeItem(String itemName){
		int count = 0;
		for(Iterator<CartItem> ite = cartItems.iterator(); ite.hasNext();){
			CartItem item = ite.next();
			
			if(item.getItem().equals(itemName)){
				ite.remove();
				count++;
			}
		}
		return count;
	}
	
	public double getTotalAmount(){
		double result = 0;
		for (CartItem item : cartItems) {
			result += item.getUnitPrice() * item.getQty();
		}
		return result;
	}
	
	@Override
	public void afterBegin() throws EJBException, RemoteException {
		LOGGER.debug("CartBean.afterBegin()");
	}

	@Override
	public void afterCompletion(boolean b) throws EJBException, RemoteException {
		LOGGER.debug("CartBean.afterCompletion(): " + b);
	}

	@Override
	public void beforeCompletion() throws EJBException, RemoteException {
		LOGGER.debug("CartBean.beforeCompletion()");
	}
}
