package org.tinyejb.test.ejbs.stateful;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.ejb.EJBException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.ejb.SessionSynchronization;

public class CartBean implements SessionBean, SessionSynchronization {
	private SessionContext ctx;
	private List<CartItem> cartItems;
	private String customerName;

	public void ejbCreate(String customerName) throws EJBException {
		System.out.println("CartBean.ejbCreate(customerName) = (" + customerName + ")");
		this.customerName = customerName;
		this.cartItems = new ArrayList<CartItem>();
	}

	@Override
	public void ejbActivate() throws EJBException, RemoteException {
		System.out.println("CartBean.ejbActivate()");
	}

	@Override
	public void ejbPassivate() throws EJBException, RemoteException {
		System.out.println("CartBean.ejbPassivate()");
	}

	@Override
	public void ejbRemove() throws EJBException, RemoteException {
		System.out.println("CartBean.ejbRemove()");
	}

	@Override
	public void setSessionContext(SessionContext ctx) throws EJBException, RemoteException {
		this.ctx = ctx;
	}

	public void addItem(CartItem item) {
		System.out.println("CartBean.addItem()");
		cartItems.add(item);
	}

	public void listItems() {
		System.out.println("CartBean.listItems()");
		
		System.out.println("listing shopping cart for customer " + customerName);
		int i = 1;
		for (CartItem item : cartItems) {
			System.out.println("#" + i + " - " + item);
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
		System.out.println("CartBean.afterBegin()");
	}

	@Override
	public void afterCompletion(boolean b) throws EJBException, RemoteException {
		System.out.println("CartBean.afterCompletion(): " + b);
	}

	@Override
	public void beforeCompletion() throws EJBException, RemoteException {
		System.out.println("CartBean.beforeCompletion()");
	}
}
