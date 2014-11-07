package org.tinyejb.core;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.TransactionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceHolder {
	private final static Logger		LOGGER	= LoggerFactory.getLogger(ResourceHolder.class);
	private TransactionManager		txManager;
	private Context					jndiContext;
	private static ResourceHolder	holder;

	private ResourceHolder() {
	}

	private void init() {
		try {
			this.jndiContext = new InitialContext();
			this.txManager = lookupForTransactionManager();
			jndiContext.createSubcontext("java:/comp/env/ejb");
		} catch (NamingException e) {
			LOGGER.error("trying get resource", e);
		}
	}

	private TransactionManager lookupForTransactionManager() throws NamingException {
		String[] jndi = { "java:jboss/TransactionManager", "java:/TransactionManager" };
		for (String jndiAddress : jndi) {
			Object tx;
			try {
				tx = jndiContext.lookup(jndiAddress);
				if (tx != null && tx instanceof TransactionManager) {
					return (TransactionManager) tx;
				}
			} catch (NamingException e) {
				LOGGER.warn("TransactionManager not found at " + jndiAddress);
			}
		}
		throw new NamingException("TransactionManager not found");
	}

	private static ResourceHolder getHolder() {
		if (holder == null) {
			holder = new ResourceHolder();
			holder.init();
		}
		return holder;
	}

	public static TransactionManager getTxManager() {
		return getHolder().txManager;
	}

	public static Context getJndiContext() {

		return getHolder().jndiContext;
	}

	public static void setHolder(TransactionManager txManager, Context jndiContex) {
		LOGGER.warn("Only set this in Test");
		if (holder == null) {
			holder = new ResourceHolder();
		}
		getHolder().txManager = txManager;
		getHolder().jndiContext = jndiContex;
	}
}
