package org.tinyejb.proxies;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;

import javax.ejb.CreateException;
import javax.ejb.EJBHome;
import javax.ejb.EJBLocalHome;
import javax.ejb.EJBMetaData;
import javax.ejb.Handle;
import javax.ejb.HomeHandle;
import javax.ejb.SessionBean;

import org.tinyejb.core.EJBContainer;
import org.tinyejb.core.EJBMetadata;
import org.tinyejb.core.EJBMetadata.BEAN_TYPE;
import org.tinyejb.core.EJBMetadata.EJBMethodTransactionInfo.METHOD_INTF;
import org.tinyejb.proxies.BeanInstancePool.BeanInstanceFactory;

/**
 * Builder for home interfaces
 * 
 * @author Cláudio Gualberto
 * 19/09/2014
 *
 */
public class EJBHomeBuilder {

	public static IEJBHome build(EJBMetadata ejbMetadata, EJBContainer ejbContainer, METHOD_INTF methodIntf) throws Exception {
		EJBHomeProxy proxyHnd = new EJBHomeProxy(ejbMetadata, ejbContainer, methodIntf);

		Class<?> homeIntf;

		if (methodIntf.equals(METHOD_INTF.LocalHome)) {
			homeIntf = Class.forName(ejbMetadata.getLocalHomeIntf(), true, Thread.currentThread().getContextClassLoader());
		} else if (methodIntf.equals(METHOD_INTF.Home)) {
			homeIntf = Class.forName(ejbMetadata.getHomeIntf(), true, Thread.currentThread().getContextClassLoader());
		} else {
			throw new IllegalStateException("Unsupported home interface: " + methodIntf);
		}

		return (IEJBHome) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[] { IEJBHome.class, homeIntf}, proxyHnd);
	}

	/**
	 * Home proxy for beans.
	 * Acts as a factory for EJB instances
	 * 
	 * @author Cláudio Gualberto
	 * 19/09/2014
	 *
	 */
	private static class EJBHomeProxy implements InvocationHandler, IEJBHome, Serializable {
		private static final long serialVersionUID = 1L;

		private EJBMetadata ejbMetadata;

		//proxy that is used for all client calls of this bean. Only for stateless beans.
		private ISessionEJB statelessBeanProxy;

		//type of EJB interface that calls this proxy (remote or local)
		private METHOD_INTF homeIntf;

		//ejbContainer that owns this bean
		private EJBContainer ejbContainer;

		//instance pool used for this bean (only for Stateless)
		private BeanInstancePool instancePool;

		private EJBHomeProxy(EJBMetadata ejbMetadata, EJBContainer ejbContainer, METHOD_INTF methodIntf) {
			this.ejbMetadata = ejbMetadata;
			this.homeIntf = methodIntf;
			this.ejbContainer = ejbContainer;
		}

		@Override
		public Object invoke(Object homeProxy, Method method, Object[] args) throws Throwable {

			Class<?> methodDeclaringClass = method.getDeclaringClass();

			if (IEJBHome.class.equals(methodDeclaringClass)) {
				return method.invoke(this, args);
			} else {
				if (ejbContainer.getContainerStatus().equals(EJBContainer.ContainerStatus.SHUT_DOWN)) {
					throw new IllegalStateException("EJBContainer for bean '" + ejbMetadata.getName() + "' has shut down.");
				}
				
				if (EJBLocalHome.class.equals(methodDeclaringClass)) {
					//EJBLocalHome has only one method, and it is intended for EntityBeans, no supported for TinyEJB, so...
					throwMethodNotSupportedError(method);

				} else if (EJBHome.class.equals(methodDeclaringClass)) {

					return handleEJBHomeMethods(homeProxy, method, args);

				} else if (method.getName().equals("create")) {

					return newBeanProxy(homeProxy, method, args);

				}else if (Object.class.equals(methodDeclaringClass)) {
					return method.invoke(homeProxy, args);
				} else {
					throwMethodNotSupportedError(method);
				}

			}
			return null;
		}

		private Object newBeanProxy(final Object homeProxy, Method createMethod, Object[] args) throws Exception {
			try {
				ISessionEJB beanProxy = null;

				if (ejbMetadata.isStateless()) {
					synchronized (this) {
						//first call for create on the stateless home
						if (statelessBeanProxy == null) {
							statelessBeanProxy = buildBeanProxy(homeProxy);

							if (ejbContainer.isUseSingletonForStateless()) {
								//Singleton stateless beans always use the same bean instance to service all clients
								SessionBean beanInstance = buildSessionBeanInstance(homeProxy, statelessBeanProxy, createMethod, args);

								statelessBeanProxy.setDelegationInstance(beanInstance);
							} else {
								/*
								 Stateless beans use instance pool to get instances for method execution.
								 This way, we keep the beans Thread-safe, as EJB spec determines
								 */

								BeanInstanceFactory beanFactory = new BeanInstanceFactory() {
									@Override
									public SessionBean build() {
										try {
											return buildSessionBeanInstance(homeProxy, statelessBeanProxy);
										} catch (Exception e) {
											throw new IllegalStateException(e);
										}
									}
								};

								instancePool = new BeanInstancePool(ejbMetadata, beanFactory);

								statelessBeanProxy.setBeanInstancePool(instancePool);
							}
						}

						beanProxy = statelessBeanProxy;
					}
				} else {
					/*
					 Stateful beans use one bean instance for each bean proxy.
					 It's not pooled neither shared, so it keeps the conversational state as EJB spec determines.
					 TinyEJB never passivates a stateful bean, so instances keep alive until ejbRemove() is called.
					 
					 Forgotten instances can cause memory leaks, so we should have an clean task to avoid them.
					 */
					beanProxy = buildBeanProxy(homeProxy);

					SessionBean beanInstance = buildSessionBeanInstance(homeProxy, beanProxy, createMethod, args);

					beanProxy.setDelegationInstance(beanInstance);
				}

				return beanProxy;

			} catch (Exception e) {
				//As determined by EJB spec
				CreateException ce = null;
				if (e instanceof InvocationTargetException) {
					InvocationTargetException ite = (InvocationTargetException) e;
					ce = new CreateException(ite.getTargetException().getMessage());
					ce.initCause(ite.getTargetException());
				} else {
					ce = new CreateException(e.getMessage());
					ce.initCause(e);
				}
				throw ce;
			}
		}

		private ISessionEJB buildBeanProxy(Object homeProxy) throws Exception {
			return SessionBeanProxyBuilder.build(ejbMetadata, homeProxy, homeIntf.equals(METHOD_INTF.Home) ? METHOD_INTF.Remote : METHOD_INTF.Local);
		}

		private SessionBean buildSessionBeanInstance(Object homeProxy, ISessionEJB beanProxy) throws Exception {
			return buildSessionBeanInstance(homeProxy, beanProxy, null, null);
		}

		private SessionBean buildSessionBeanInstance(Object homeProxy, ISessionEJB beanProxy, Method createMethodOnHomeIntf, Object[] args) throws Exception {
			/*
			  SessionBean creation sequence, as defined by EJB 2.x spec: 
			 		a) new Bean instance, calling bean class's void constructor 
			 		b) call SessionBean.setSessionContext() on the new instance
			 		c) call ejbCreate() method on the new instance, matching arguments from create() home interface
			 */

			//SessionBean sb = (SessionBean) Class.forName(ejbMetadata.getEjbClassName(), true, Thread.currentThread().getContextClassLoader()).newInstance();
			SessionBean sb = (SessionBean) Class.forName(ejbMetadata.getEjbClassName(), true, EJBContainer.class.getClassLoader()).newInstance();

			sb.setSessionContext(new SessionContextImpl(homeProxy, beanProxy, ejbContainer, ejbMetadata));

			try {
				Method ejbCreateMethod;

				if (ejbMetadata.isStateless()) {
					//For stateless, we always use void arguments ejbCreate()
					ejbCreateMethod = sb.getClass().getMethod("ejbCreate", (Class[]) null);
				} else {
					//for Stateful, arguments must match
					ejbCreateMethod = sb.getClass().getMethod("ejbCreate", createMethodOnHomeIntf.getParameterTypes());
				}

				ejbCreateMethod.invoke(sb, args);

				return sb;
			} catch (NoSuchMethodException e) {
				throw new IllegalStateException("No ejbCreate() method found on '" + ejbMetadata.getName() + "' that matches home interface.", e);
			}
		}

		private Object handleEJBHomeMethods(final Object homeProxy, Method method, Object[] args) {
			String name = method.getName();

			if (name.equals("remove")) {
				try {
					if (method.getParameterTypes()[0].equals(Handle.class)) {
						//EJBHome.remove(Handle)
						((Handle) args[0]).getEJBObject().remove();
					} else {
						//EntityBeans only, so...
						throwMethodNotSupportedError(method);
					}

				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			} else if (name.equals("getHomeHandle")) {
				return new HomeHandle() {
					private static final long serialVersionUID = 1L;

					@Override
					public EJBHome getEJBHome() throws RemoteException {
						//this handle don't aims to be Serializable, just satisfy the spec
						return (EJBHome) homeProxy;
					}
				};
			} else if (name.equals("getEJBMetaData")) {
				return buildEJBMetaData(homeProxy);
			}

			return null;
		}

		private EJBMetaData buildEJBMetaData(final Object homeProxy) {
			return new EJBMetaData() {

				@Override
				public boolean isStatelessSession() {
					return ejbMetadata.getType().equals(BEAN_TYPE.Stateless);
				}

				@Override
				public boolean isSession() {
					return true;
				}

				@Override
				public Class<?> getRemoteInterfaceClass() {
					try {
						return ejbMetadata.getRemoteIntf() != null ? Class.forName(ejbMetadata.getRemoteIntf(), true, Thread.currentThread().getContextClassLoader()) : null;
					} catch (ClassNotFoundException e) {
						throw new IllegalStateException(e);
					}
				}

				@Override
				public Class<?> getPrimaryKeyClass() {
					throw new IllegalStateException("Method not supported: getPrimaryKeyClass");
				}

				@Override
				public Class<?> getHomeInterfaceClass() {
					try {
						return ejbMetadata.getHomeIntf() != null ? Class.forName(ejbMetadata.getHomeIntf(), true, Thread.currentThread().getContextClassLoader()) : null;
					} catch (ClassNotFoundException e) {
						throw new IllegalStateException(e);
					}
				}

				@Override
				public EJBHome getEJBHome() {
					return (EJBHome) homeProxy;
				}
			};
		}

		private void throwMethodNotSupportedError(Method m) {
			throw new IllegalStateException("Method not supported: " + m.getDeclaringClass().getName() + "." + m.getName() + "()");
		}

		@Override
		public void onContainerShutDown() {
			if (instancePool != null) {
				instancePool.shutdown();
			}
		}
	}
}
