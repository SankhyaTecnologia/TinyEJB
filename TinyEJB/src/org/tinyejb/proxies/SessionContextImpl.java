package org.tinyejb.proxies;

import java.security.Identity;
import java.security.Principal;
import java.util.Properties;

import javax.ejb.EJBHome;
import javax.ejb.EJBLocalHome;
import javax.ejb.EJBLocalObject;
import javax.ejb.EJBObject;
import javax.ejb.SessionContext;
import javax.ejb.TimerService;
import javax.transaction.Status;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;
import javax.xml.rpc.handler.MessageContext;

import org.tinyejb.core.EJBContainer;
import org.tinyejb.core.EJBMetadata;
import org.tinyejb.core.EJBMetadata.TRANSACTION_MANAGED_BY;

public class SessionContextImpl implements SessionContext {
	private Object homeProxy;
	private Object beanProxy;
	private EJBContainer ejbContainer;
	private EJBMetadata ejbMetadata;

	public SessionContextImpl(Object homeProxy, Object beanProxy, EJBContainer ejbContainer, EJBMetadata ejbMetadata) {
		this.homeProxy = homeProxy;
		this.beanProxy = beanProxy;
		this.ejbContainer = ejbContainer;
		this.ejbMetadata = ejbMetadata;
	}

	@Override
	public Identity getCallerIdentity() {
		throw new UnsupportedOperationException("getCallerIdentity");
	}

	@Override
	public Principal getCallerPrincipal() {
		throw new UnsupportedOperationException("getCallerPrincipal");
	}

	@Override
	public EJBHome getEJBHome() {
		return (EJBHome) homeProxy;
	}

	@Override
	public EJBLocalHome getEJBLocalHome() {
		return (EJBLocalHome) homeProxy;
	}

	@Override
	public Properties getEnvironment() {
		throw new UnsupportedOperationException("getEnvironment");
	}

	private void assertContainerManagedTx(String errorMessage) {
		if (!ejbMetadata.getTxManagedBy().equals(TRANSACTION_MANAGED_BY.Container)) {
			throw new IllegalStateException(errorMessage);
		}
	}

	@Override
	public boolean getRollbackOnly() throws IllegalStateException {
		assertContainerManagedTx("SessionContext.getRollbackOnly() not allowed for bean-managed transaction");
		try {
			Transaction tx = ejbContainer.getTransactionManager().getTransaction();

			if (tx != null) {
				return tx.getStatus() == Status.STATUS_MARKED_ROLLBACK;
			} else {
				throw new IllegalStateException("No transaction scope for SessionContext.getRollbackOnly()");
			}
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public TimerService getTimerService() throws IllegalStateException {
		throw new UnsupportedOperationException("getTimerService");
	}

	@Override
	public UserTransaction getUserTransaction() throws IllegalStateException {
		throw new UnsupportedOperationException("getUserTransaction");
	}

	@Override
	public boolean isCallerInRole(Identity arg0) {
		throw new UnsupportedOperationException("isCallerInRole");
	}

	@Override
	public boolean isCallerInRole(String arg0) {
		throw new UnsupportedOperationException("isCallerInRole");
	}

	@Override
	public void setRollbackOnly() throws IllegalStateException {
		assertContainerManagedTx("SessionContext.setRollbackOnly() not allowed for bean-managed transaction");
		try {
			Transaction tx = ejbContainer.getTransactionManager().getTransaction();

			if (tx != null) {
				tx.setRollbackOnly();
			} else {
				throw new IllegalStateException("No transaction scope for SessionContext.setRollbackOnly()");
			}
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public EJBLocalObject getEJBLocalObject() throws IllegalStateException {
		return (EJBLocalObject) beanProxy;
	}

	@Override
	public EJBObject getEJBObject() throws IllegalStateException {
		return (EJBObject) beanProxy;
	}

	@Override
	public MessageContext getMessageContext() throws IllegalStateException {
		throw new UnsupportedOperationException("getMessageContext");
	}

}
