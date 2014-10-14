package org.tinyejb.test.ejbs.stateless;

import java.rmi.RemoteException;

import javax.ejb.EJBException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvoiceFacadeBean implements SessionBean {
	private static final long serialVersionUID = 1L;
	private final static Logger LOGGER = LoggerFactory.getLogger(InvoiceFacadeBean.class);
	private SessionContext ctx;
	private double productsTotalPrice; //just to prove the thread-safeness of a stateless session bean (or not, if its a singleton)

	public void ejbCreate() throws EJBException {
		LOGGER.debug("ServiceBean.ejbCreate()");
	}

	@Override
	public void ejbActivate() throws EJBException, RemoteException {
		LOGGER.debug("ServiceBean.ejbActivate()");
	}

	@Override
	public void ejbPassivate() throws EJBException, RemoteException {
		LOGGER.debug("ServiceBean.ejbPassivate()");
	}

	@Override
	public void ejbRemove() throws EJBException, RemoteException {
		LOGGER.debug("ServiceBean.ejbRemove()");
	}

	@Override
	public void setSessionContext(SessionContext ctx) throws EJBException {
		LOGGER.debug("ServiceBean.setSessionContext()");
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
