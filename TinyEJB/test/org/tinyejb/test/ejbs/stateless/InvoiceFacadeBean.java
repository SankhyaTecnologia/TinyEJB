package org.tinyejb.test.ejbs.stateless;

import java.rmi.RemoteException;

import javax.ejb.EJBException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;

public class InvoiceFacadeBean implements SessionBean {
	private SessionContext ctx;
	private double productsTotalPrice; //just to prove the thread-safeness of a stateless session bean (or not, if its a singleton)

	public void ejbCreate() throws EJBException {
		System.out.println("ServiceBean.ejbCreate()");
	}

	@Override
	public void ejbActivate() throws EJBException, RemoteException {
		System.out.println("ServiceBean.ejbActivate()");
	}

	@Override
	public void ejbPassivate() throws EJBException, RemoteException {
		System.out.println("ServiceBean.ejbPassivate()");
	}

	@Override
	public void ejbRemove() throws EJBException, RemoteException {
		System.out.println("ServiceBean.ejbRemove()");
	}

	@Override
	public void setSessionContext(SessionContext ctx) throws EJBException {
		System.out.println("ServiceBean.setSessionContext()");
		this.ctx = ctx;
	}

	public double insertInvoice(double totalAmount, double importFees) throws Exception, EJBException {
		double result;
		productsTotalPrice = totalAmount;

		sleepFor(200); //simulates systems load

		result = productsTotalPrice + (productsTotalPrice * importFees / 100);

		return result;
	}
	
	public void removeInvoiceRollingBack() throws EJBException{
		ctx.setRollbackOnly();
	}
	
	public void removeInvoiceThrowingSystemException()throws EJBException{
		throw new EJBException("Just testing automatic rollback");
	}

	private void sleepFor(long t) {
		try {
			Thread.sleep(t);
		} catch (Exception ignored) {
		}
	}
}
