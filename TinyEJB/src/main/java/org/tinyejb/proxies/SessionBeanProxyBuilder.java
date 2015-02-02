package org.tinyejb.proxies;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.ejb.EJBException;
import javax.ejb.EJBLocalObject;
import javax.ejb.EJBObject;
import javax.ejb.Handle;
import javax.ejb.SessionBean;
import javax.ejb.SessionSynchronization;
import javax.ejb.TransactionRequiredLocalException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionRequiredException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinyejb.core.EJBContainer;
import org.tinyejb.core.EJBMetadata;
import org.tinyejb.core.EJBMetadata.BEAN_TYPE;
import org.tinyejb.core.EJBMetadata.EJBMethodTransactionInfo;
import org.tinyejb.core.EJBMetadata.EJBMethodTransactionInfo.METHOD_INTF;
import org.tinyejb.core.EJBMetadata.TRANSACTION_TYPE;

public class SessionBeanProxyBuilder {
	private final static Logger LOGGER = LoggerFactory.getLogger(SessionBeanProxyBuilder.class);
	public static ISessionEJB build(EJBMetadata ejbMetadata, Object homeProxy, METHOD_INTF methodIntf) throws Exception {
		SessionEJBProxy proxyIH = new SessionEJBProxy(ejbMetadata, homeProxy);

		return (ISessionEJB) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), buildInterfacesToImplement(ejbMetadata, methodIntf), proxyIH);
	}

	private static Class<?>[] buildInterfacesToImplement(EJBMetadata ejbmd, METHOD_INTF methodIntf) throws Exception {
		List<Class<?>> interfaces = new ArrayList<Class<?>>();

		if (methodIntf.equals(METHOD_INTF.Local)) {
			interfaces.add(Class.forName(ejbmd.getLocalIntf(), true, Thread.currentThread().getContextClassLoader()));
		} else if (methodIntf.equals(METHOD_INTF.Remote)) {
			interfaces.add(Class.forName(ejbmd.getRemoteIntf(), true, Thread.currentThread().getContextClassLoader()));
		} else {
			throw new IllegalStateException("Invalid business interface type: " + methodIntf);
		}

		interfaces.add(ISessionEJB.class);

		return interfaces.toArray(new Class[] {});
	}

	private static class SessionEJBProxy implements InvocationHandler, ISessionEJB {
		private EJBMetadata ejbMetadata;
		private SessionBean beanDelegate;
		private BeanInstancePool beanInstancePool;
		private Object homeProxy;
		private Method[] statefulMethodLock = new Method[1];

		private SessionEJBProxy(EJBMetadata ejbMetadata, Object homeProxy) {
			this.ejbMetadata = ejbMetadata;
			this.homeProxy = homeProxy;
		}

		@Override
		public Object invoke(Object beanProxy, Method method, Object[] args) throws Throwable {
			if (ejbMetadata.getEjbContainer().getContainerStatus().equals(EJBContainer.ContainerStatus.SHUT_DOWN)) {
				throw new IllegalStateException("EJBContainer for bean '" + ejbMetadata.getName() + "' has shut down.");
			}

			if (method.getDeclaringClass().equals(ISessionEJB.class)) {
				return method.invoke(this, args);
			} else if (method.getDeclaringClass().equals(EJBLocalObject.class)) {
				return handleEJBLocalObjectMethods(beanProxy, method, args);
			} else if (method.getDeclaringClass().equals(EJBObject.class)) {
				return handleEJBObjectMethods(beanProxy, method, args);
			}

			return handleBusinessInterfaceMethods(beanProxy, method, args);
		}

		private Object handleBusinessInterfaceMethods(Object beanProxy, Method method, Object[] args) throws Throwable {
			MethodChain callChain = new MethodCaller(ejbMetadata);

			TransactionWrapper txWrapper = new TransactionWrapper(ejbMetadata);
			txWrapper.setNext(callChain);

			callChain = txWrapper;

			if (!isStateless()) {
				MethodChain serializerChain = new StatefulSerializerWrapper(this, ejbMetadata);
				serializerChain.setNext(callChain);
				callChain = serializerChain;
			}

			if (ejbMetadata.isStateless() && beanDelegate == null) { //Singleton stateless beans do not have a fixed delegate instance, we must get one from pool
				SessionBean beanInstance = null;
				;
				try {
					beanInstance = beanInstancePool.getFromPool();
					return callChain.call(beanInstance, method, args);
				} finally {
					if (beanInstance != null) {
						beanInstancePool.returnToPool(beanInstance);
					}
				}
			}

			return callChain.call(beanDelegate, method, args);
		}

		private Object handleEJBObjectMethods(final Object beanProxy, Method method, Object[] args) throws Exception {
			if (method.getName().equals("getEJBHome")) {
				return homeProxy;
			} else if (method.getName().equals("getHandle")) {
				return new Handle() {
					private static final long serialVersionUID = 1L;

					@Override
					public EJBObject getEJBObject() throws RemoteException {
						return (EJBObject) beanProxy;
					}
				};
			} else if (method.getName().equals("isIdentical")) {
				return handleIsIdenticalMethod(beanProxy, method, args);
			} else if (method.getName().equals("remove")) {
				callRemoveOnBean();
			} else {
				throw new UnsupportedOperationException(method.toString());
			}

			return null;
		}

		private boolean isStateless() {
			return ejbMetadata.getType().equals(BEAN_TYPE.Stateless);
		}

		private Object handleEJBLocalObjectMethods(Object beanProxy, Method method, Object[] args) throws Exception {
			if (method.getName().equals("getEJBLocalHome")) {
				return homeProxy;
			} else if (method.getName().equals("remove")) {
				callRemoveOnBean();
			} else if (method.getName().equals("isIdentical")) {
				return handleIsIdenticalMethod(beanProxy, method, args);
			} else {
				throw new UnsupportedOperationException(method.toString());
			}

			return null;
		}

		private Object handleIsIdenticalMethod(Object beanProxy, Method m, Object[] args) {
			try {
				if (ejbMetadata.isStateless()) {
					/*
					 EJB spec 6.9.2
					 todos os EJBs stateless criados pela mesma home sempre serão idênticos, independente de instância do SessionBean
					 */
					return homeProxy == (ejbMetadata.isLocalObjectInterface(m) ? ((EJBLocalObject) args[0]).getEJBLocalHome() : ((EJBObject) args[0]).getEJBHome());
				}

					/*
				  	EJB spec 6.9.1
					Stateful session beans devem possuir uma identidade única.
					Considerando que cada EJB deste tipo possui uma instância de proxy para cada instância de SessionBean, então
					um bean será considerado idêntico ao outro caso ambos possuam o mesmo proxy.
					 */
				return beanProxy == args[0];
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}

		private void callRemoveOnBean() throws Exception {
			if (isStateless()) {
				LOGGER.debug("ignoring remove() call on stateless bean");
			} else {//Stateful
				try {
					//Segundo a especificação EJB 2.x a chamada a ejbRemove() é feita em um contexto não transacional.
					beanDelegate.getClass().getMethod("ejbRemove").invoke(beanDelegate);
				} catch (NoSuchMethodException e) {
					throw new IllegalStateException("No ejbRemove() method found on '" + ejbMetadata.getName() + ".");
				}
			}
		}

		@Override
		public void setDelegationInstance(SessionBean bean) {
			this.beanDelegate = bean;
		}

		@Override
		public void setBeanInstancePool(BeanInstancePool pool) {
			this.beanInstancePool = pool;
		}
	}

	private static interface MethodChain {
		void setNext(MethodChain next);

		Object call(SessionBean instance, Method m, Object[] args) throws Throwable;
	}

	private static class MethodCaller implements MethodChain {
		private EJBMetadata ejbmd;

		public MethodCaller(EJBMetadata ejbmd) {
			this.ejbmd = ejbmd;
		}

		@Override
		public Object call(SessionBean instance, Method m, Object[] args) throws Throwable {
			try {
				return instance.getClass().getMethod(m.getName(), m.getParameterTypes()).invoke(instance, args);
			} catch (InvocationTargetException e) {
				if (!(e instanceof Exception) && !ejbmd.isLocalObjectInterface(m)) {
					EJBException re = new EJBException();
					re.initCause(e.getTargetException());
					throw re;
				}
				throw e.getTargetException();
			}
		}

		@Override
		public void setNext(MethodChain next) {
		}

	}

	private static class TransactionWrapper implements MethodChain {
		private MethodChain next;
		private EJBMetadata ejbMetadata;

		public TransactionWrapper(EJBMetadata ejbMetadata) {
			this.ejbMetadata = ejbMetadata;
		}

		@Override
		public void setNext(MethodChain next) {
			this.next = next;
		}

		@Override
		public Object call(SessionBean beanInstance, Method m, Object[] args) throws Throwable {
			boolean wasNewTx = false;

			TransactionManager txManager = ejbMetadata.getEjbContainer().getTransactionManager();

			if (txManager == null) {
				throw new IllegalStateException("No JTA TransactionManager configured");
			}

			Transaction suspendedTx = null;
			Transaction tx = null;

			try {
				EJBMethodTransactionInfo mti = ejbMetadata.getTransactionInfoForMethod(m);

				TRANSACTION_TYPE txType = mti != null ? mti.getTxType() : ejbMetadata.getDefaultTxType();

				switch (txType) {
					case Required:
						Transaction currentTx = txManager.getTransaction();
						if (currentTx == null) {
							txManager.begin();
							wasNewTx = true;
						} else if (currentTx.getStatus() != Status.STATUS_ACTIVE) {
							throw new IllegalStateException("There is a Transaction associated to this Thread, but it is not active: " + m.toString());
						}

						break;
					case RequiresNew:
						suspendedTx = txManager.suspend();
						txManager.begin();
						wasNewTx = true;

						break;
					case Mandatory:
						tx = txManager.getTransaction();

						if (tx == null) {
							if (ejbMetadata.isLocalObjectInterface(m)) {
								throw new TransactionRequiredLocalException("Transaction is required for this method: " + m.toString());
							} else {
								throw new TransactionRequiredException("Transaction is required for this method: " + m.toString());
							}
						}
						break;
					case NotSupported:
						suspendedTx = txManager.suspend();
						break;
					case Supports:
						break; //nada a fazer neste caso

					case Never:
						tx = txManager.getTransaction();

						if (tx != null) {
							if (ejbMetadata.isLocalObjectInterface(m)) {
								throw new EJBException("Transaction is not allowed for this method: " + m.toString());
							} else {
								throw new RemoteException("Transaction is not allowed for this method: " + m.toString());
							}
						}
						break;
					default:
						break;
				}

				if (wasNewTx) {
					if (beanInstance instanceof SessionSynchronization) {
						if (ejbMetadata.isStateless()) {
							throw new IllegalStateException("Stateless Session Bean spec violation (cap.7.11.2). Can not implements javax.ejb.SessionSynchronization");
						}
						((SessionSynchronization) beanInstance).afterBegin();
						txManager.getTransaction().registerSynchronization(new SessionSynchronizationImpl(beanInstance));
					}
				}

				return next.call(beanInstance, m, args);
			} catch (RuntimeException re) {
				if (wasNewTx) {
					assertRollbackOnly(txManager);
				}
				throw re;
			} finally {
				if (wasNewTx) {
					endTransaction(txManager);
				}

				if (suspendedTx != null) {
					txManager.resume(suspendedTx);
				}
			}
		}

		private void assertRollbackOnly(TransactionManager txManager) throws Exception {
			Transaction tx = txManager.getTransaction();
			if (tx != null && tx.getStatus() != Status.STATUS_MARKED_ROLLBACK) {
				LOGGER.info("System exception, so transaction will rollback!");
				tx.setRollbackOnly();
			}
		}

		private void endTransaction(TransactionManager txManager) throws Exception {
			Transaction tx = txManager.getTransaction();

			if (tx.getStatus() == Status.STATUS_MARKED_ROLLBACK) {
				tx.rollback();
			} else {
				tx.commit();
			}

			txManager.suspend(); //desvinculamos a TX da Thread atual, pois ela já não tem utilidade
		}
	}

	/**
	 * Method call serializer for Stateful beans
	 * 
	 * @author Cláudio Gualberto
	 * 20/09/2014
	 *
	 */
	private static class StatefulSerializerWrapper implements MethodChain {
		private MethodChain next;
		private EJBMetadata ejbmd;
		private SessionEJBProxy beanProxyHnd;

		public StatefulSerializerWrapper(SessionEJBProxy beanProxyHnd, EJBMetadata ejbmd) {
			this.beanProxyHnd = beanProxyHnd;
			this.ejbmd = ejbmd;
		}

		@Override
		public void setNext(MethodChain next) {
			this.next = next;
		}

		@Override
		public Object call(SessionBean instance, Method m, Object[] args) throws Throwable {
			//this avoids the known 'No cuncorrent call on Stateful beans' from JBoss
			synchronized (beanProxyHnd.statefulMethodLock) {
				long start = System.currentTimeMillis();

				while (beanProxyHnd.statefulMethodLock[0] != null) {
					if (System.currentTimeMillis() - start > ejbmd.getEjbContainer().getConcurrentCallWaitTimeout()) {
						throw new IllegalStateException("Timeout waiting for method call on Statefull bean. Method using the instance: '" + beanProxyHnd.statefulMethodLock[0].getName() + "'");
					}
					LOGGER.info(m.getName() + " waiting for " + beanProxyHnd.statefulMethodLock[0].getName());
					beanProxyHnd.statefulMethodLock.wait(1000);
				}
				beanProxyHnd.statefulMethodLock[0] = m;
			}
			try {
				return next.call(instance, m, args);
			} finally {
				waitRandomly();
				synchronized (beanProxyHnd.statefulMethodLock) {
					beanProxyHnd.statefulMethodLock[0] = null;
					beanProxyHnd.statefulMethodLock.notifyAll();
				}
			}
		}

		private void waitRandomly() {
			try {
				int milliseconds = ejbmd.getEjbContainer().getRandomTimeToWaitOnStatefulCalls();

				if (milliseconds > 0) {
					milliseconds = new Random().nextInt(milliseconds);
					LOGGER.info("sleeping randomly for " + (milliseconds / 1000) + " secs");
					Thread.sleep(new Random().nextInt(milliseconds));
				}
			} catch (Exception ignored) {
			}
		}
	}

	private static class SessionSynchronizationImpl implements Synchronization {
		private SessionBean bean;

		public SessionSynchronizationImpl(SessionBean bean) {
			this.bean = bean;
		}

		@Override
		public void afterCompletion(int status) {
			try {
				((SessionSynchronization) bean).afterCompletion(status == Status.STATUS_COMMITTED);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		public void beforeCompletion() {
			try {
				((SessionSynchronization) bean).beforeCompletion();
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}

		}

	}
}
