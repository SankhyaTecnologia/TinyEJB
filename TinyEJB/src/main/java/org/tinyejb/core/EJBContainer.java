package org.tinyejb.core;

import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.ejb.EJBLocalObject;
import javax.ejb.EJBObject;
import javax.ejb.SessionBean;
import javax.transaction.TransactionManager;

import org.jdom.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinyejb.core.EJBMetadata.BEAN_TYPE;
import org.tinyejb.core.EJBMetadata.EJBMethodTransactionInfo;
import org.tinyejb.core.EJBMetadata.EJBMethodTransactionInfo.METHOD_INTF;
import org.tinyejb.core.EJBMetadata.TRANSACTION_MANAGED_BY;
import org.tinyejb.core.EJBMetadata.TRANSACTION_TYPE;
import org.tinyejb.proxies.EJBHomeBuilder;
import org.tinyejb.proxies.IEJBHome;
import org.tinyejb.utils.XMLStuff;

public class EJBContainer implements Serializable {
	private static final String					JAVA_COMP_ENV_EJB			= "java:/comp/env/ejb/";
	private static final long					serialVersionUID			= 1L;
	private final static Logger					LOGGER						= LoggerFactory.getLogger(EJBContainer.class);
	private Map<String, EJBMetadata>			deployedBeans;
	private IJndiResolver						jndiResolver;

	/*
	 * EJB 2.1 spec session 7.12.10 determines that a session bean instance must
	 * be thread-safe. TinyEJB has an instance pool for Stateless session beans,
	 * so each client call is serviced by a specific instance, and while the
	 * call is going on, the instance remains unavailable for other clients.
	 * There is a background task that keeps this pool small as possible, an the
	 * following atribute configures the maximum time (in milleseconds) an
	 * instance bean can stand idle, before the cleaner release it. Default
	 * value is 2 minutes
	 */
	private long								pooledBeanMaxAge			= 120000;

	/*
	 * This is an out-of-spec alternative for Stateless session beans. When the
	 * following attribute is true, TinyEJB will serve all client calls with
	 * only one session bean instance (ie. no instance poll is used) This
	 * aproach performs better on applications that uses stateless beans as
	 * business delegation or just take advantage on container transactions.
	 * Obviously, singleton stateless beans are thread-unsafe
	 */
	private boolean								useSingletonForStateless;

	/*
	 * EJB spec determines that concurrent calls on stateful are forbidden, and
	 * EJB container must throw an exception. TinyEJB has a convenience way to
	 * avoid this situation, just waiting for the other call conclude, thus both
	 * calls executes on the right way and on its own transaction. The following
	 * attribute defines the timeout that incoming calls must wait before EJB
	 * container throws the exception. Default value is 30 seconds
	 */
	private long								concurrentCallWaitTimeout	= 30000;

	/*
	 * Random to generate waitings on stateful calls (test only purposes)
	 */
	private int									randomTimeToWaitOnStatefulCalls;

	private AtomicReference<ContainerStatus>	status;

	public EJBContainer() {
		this.deployedBeans = new HashMap<String, EJBMetadata>();
		this.status = new AtomicReference<EJBContainer.ContainerStatus>(ContainerStatus.NORMAL);
	}

	public TransactionManager getTransactionManager() {
		return ResourceHolder.getTxManager();
	}

	public ContainerStatus getContainerStatus() {
		return status.get();
	}

	@SuppressWarnings("unchecked")
	public void deployModuleFromDescriptor(List<InputStream> ejbDDsInputStream) throws Exception {
		if (ejbDDsInputStream.isEmpty()) {
			throw new IllegalArgumentException("null InputStream for deployment descriptor.");
		}

		LOGGER.info("starting module deploy ...");
		int beanCount = 0;
		for (InputStream ejbDDInputStream : ejbDDsInputStream) {
			Element xml = XMLStuff.buildDomDocument(ejbDDInputStream).getRootElement();
			ejbDDInputStream.close();

			// parses ejb-jar.xml and deploys the beans found on it
			if (XMLStuff.checkRequiredChildren(xml, "enterprise-beans")) {
				Element enterpriseBeansElem = xml.getChild("enterprise-beans");
				List<Element> sessionBeans = enterpriseBeansElem.getChildren("session");

				if (sessionBeans.isEmpty()) {
					LOGGER.info("There is no session-bean on descriptor file");
				} else {
					Map<String, List<EJBMethodTransactionInfo>> listOfMethods = processAssemblyDescriptor(xml);

					for (Iterator<Element> ite = sessionBeans.iterator(); ite.hasNext();) {
						Element sessionBean = ite.next();

						EJBMetadata ejbmd = processBean(sessionBean);

						if (ejbmd != null) {
							List<EJBMethodTransactionInfo> lstTXInfo = listOfMethods.get(ejbmd.getName());

							if (lstTXInfo != null) {
								ejbmd.addMethodTransactionInfo(lstTXInfo);
							} else {
								LOGGER.info("no transaction info for bean '" + ejbmd.getName() + "'. Assuming 'Required' as default for all business methods.");
							}

							if (deployIt(ejbmd)) {
								beanCount++;
							}
						}
					}
				}
			}
		}
		LOGGER.info("Total of " + beanCount + " bean(s) deployed.");
	}

	public void undeploy() {
		/*
		 * Undeploy process just unbinds home interfaces. Any thread using beans
		 * from this EJBContainer can have unexpected results.
		 */
		LOGGER.info("Undeploying " + deployedBeans.size() + " EJBs ...");

		status.set(ContainerStatus.SHUT_DOWN);

		for (EJBMetadata ejbmd : deployedBeans.values()) {
			LOGGER.info("undeploying '" + ejbmd.getName() + "' ...");

			for (String jndiName : ejbmd.getJndiNames()) {
				try {
					IEJBHome home = (IEJBHome) ResourceHolder.getJndiContext().lookup(jndiName);
					home.onContainerShutDown();
					ResourceHolder.getJndiContext().unbind(jndiName);
				} catch (Exception ignored) {
				}
			}
		}

		LOGGER.info("Undeploy done.");
	}

	private void createSubcontext(String jndiName) {
		String[] sub = jndiName.split("/");
		String path = "";
		for (int i = 0; i < sub.length - 1; i++) {
			try {
				ResourceHolder.getJndiContext().createSubcontext(path + sub[i]);

			} catch (Exception ex) {
			} finally {
				path += sub[i] + '/';
			}
		}

	}

	private boolean deployIt(EJBMetadata ejbmd) throws Exception {
		try {

			LOGGER.info("deploying " + ejbmd.getType() + " SessionBean '" + ejbmd.getName() + "' ...");
			int bindCount = 0;

			if (ejbmd.getHomeIntf() != null) {
				// deploy remote home proxy
				String jndiName = null;

				if (jndiResolver != null) {
					jndiName = jndiResolver.getRemoteJdniName(ejbmd);
				}

				if (jndiName == null) {
					Class<?> homeIntfClass = getClass(ejbmd.getHomeIntf());
					jndiName = JAVA_COMP_ENV_EJB + homeIntfClass.getSimpleName();
				}

				IEJBHome home = EJBHomeBuilder.build(ejbmd, this, METHOD_INTF.Home);
				
				LOGGER.info(ejbmd.getName() + ": remote factory bound to JNDI entry '" + jndiName + "'");
				createSubcontext(jndiName);
				ResourceHolder.getJndiContext().bind(jndiName, home);
				ejbmd.addJndiName(jndiName);
				
				bindCount++;
			}

			if (ejbmd.getLocalHomeIntf() != null) {
				// deploy local home proxy
				String jndiName = null;

				if (jndiResolver != null) {
					jndiName = jndiResolver.getLocalJdniName(ejbmd);
				}

				if (jndiName == null) {
					Class<?> localHomeIntfClass = getClass(ejbmd.getLocalHomeIntf());
					jndiName = JAVA_COMP_ENV_EJB + localHomeIntfClass.getSimpleName();
				}

				IEJBHome home = EJBHomeBuilder.build(ejbmd, this, METHOD_INTF.LocalHome);
				LOGGER.info(ejbmd.getName() + ": local factory bound to JNDI entry '" + jndiName + "'");
				ResourceHolder.getJndiContext().bind(jndiName, home);
				ejbmd.addJndiName(jndiName);
				bindCount++;
			}

			if (bindCount > 0) {
				deployedBeans.put(ejbmd.getName(), ejbmd);
				LOGGER.info("'" + ejbmd.getName() + "' deployed on " + bindCount + " factories.");
			} else {
				LOGGER.info("Deploy of bean '" + ejbmd.getName() + "' canceled. No JNDI definition.");
			}

			return bindCount > 0;
		} catch (Exception e) {
			LOGGER.info("Error when deploying " + ejbmd.getName() + ": " + e.getMessage());
			e.printStackTrace();
		}

		return false;
	}

	private EJBMetadata processBean(Element beanElem) throws Exception {
		if (XMLStuff.checkRequiredChildren(beanElem, "ejb-name", "ejb-class", "session-type", "transaction-type")) {

			String name = XMLStuff.getChildElementText(beanElem, "ejb-name");
			String ejbClass = XMLStuff.getChildElementText(beanElem, "ejb-class");
			BEAN_TYPE sessionType = BEAN_TYPE.valueOf(XMLStuff.getChildElementText(beanElem, "session-type"));
			TRANSACTION_MANAGED_BY txManagedBy = TRANSACTION_MANAGED_BY.valueOf(XMLStuff.getChildElementText(beanElem, "transaction-type"));

			String localHomeIntf = XMLStuff.getChildElementText(beanElem, "local-home");
			String localIntf = XMLStuff.getChildElementText(beanElem, "local");

			String remoteHomeIntf = XMLStuff.getChildElementText(beanElem, "home");
			String remoteIntf = XMLStuff.getChildElementText(beanElem, "remote");

			if ((localHomeIntf == null && localIntf != null) || (localHomeIntf != null && localIntf == null)) {
				LOGGER.info("local and local-home interfaces must be declared together or neither at all");
			} else {
				if ((remoteHomeIntf == null && remoteIntf != null) || (remoteHomeIntf != null && remoteIntf == null)) {
					LOGGER.info("home and remote interfaces must be declared together or neither at all");
				} else {
					EJBMetadata ejbm = new EJBMetadata(name, sessionType, txManagedBy, this);

					ejbm.setEjbClassName(ejbClass);
					ejbm.setHomeIntf(remoteHomeIntf);
					ejbm.setLocalHomeIntf(localHomeIntf);
					ejbm.setLocalIntf(localIntf);
					ejbm.setRemoteIntf(remoteIntf);

					try {
						checkEJBSpecViolations(ejbm);
					} catch (Exception e) {
						LOGGER.error("Error when deploying '" + name + "': " + e.getMessage());
					}

					return ejbm;
				}
			}
		}

		return null;
	}

	private Class<?> getClass(String name) throws Exception {
		return Class.forName(name, true, Thread.currentThread().getContextClassLoader());
	}

	private void checkEJBSpecViolations(EJBMetadata ejbm) throws Exception {
		// checks some EJB spec violations.

		Class<?> ejbClass = getClass(ejbm.getEjbClassName());

		if (!SessionBean.class.isAssignableFrom(ejbClass)) {
			throw new IllegalStateException("EJB spec violation (cap: 7.5.1): Session Bean class must implements javax.ejb.SessionBean interface (directly ou indirectly).");
		}

		if (ejbm.getLocalHomeIntf() != null) {
			Class<?> localHomeIntfClass = getClass(ejbm.getLocalHomeIntf());
			Class<?> localIntfClass = getClass(ejbm.getLocalIntf());

			if (!EJBLocalObject.class.isAssignableFrom(localIntfClass)) {
				throw new IllegalStateException("EJB spec violation (cap: 7.11.7): Session Bean's Local Interface must extend the javax.ejb.EJBLocalObject interface.");
			}

			int createCount = 0;
			for (Method iMethod : localHomeIntfClass.getMethods()) {
				if (iMethod.getName().equals("create")) {
					if (!iMethod.getReturnType().isAssignableFrom(localIntfClass)) {
						throw new IllegalStateException("EJB spec violation (cap: 7.8): For LocalHome's create() methods, the return type must be beans's local interface.");
					}

					Method cMethod = null;
					try {
						cMethod = ejbClass.getMethod("ejbCreate", iMethod.getParameterTypes());
					} catch (NoSuchMethodException e) {
						IllegalStateException error = new IllegalStateException("EJB spec violation (cap: 7.8): Session Bean class must have one ejbCreate(...) method for each create(...) method present on home interface.");
						error.initCause(e);
						throw error;
					}

					if (!cMethod.getReturnType().equals(void.class)) {
						throw new IllegalStateException("EJB spec violation (cap: 7.8): ejbCreate(...) methods on Session Bean class must return void.");
					}
					createCount++;
				}
			}

			if (createCount <= 0) {
				throw new IllegalStateException("EJB spec violation (cap: 7.8): No valid create() method found on bean's LocalHome interface.");
			}
		}

		if (ejbm.getHomeIntf() != null) {
			Class<?> homeIntfClass = getClass(ejbm.getHomeIntf());
			Class<?> remoteIntfClass = getClass(ejbm.getRemoteIntf());

			if (!EJBObject.class.isAssignableFrom(remoteIntfClass)) {
				throw new IllegalStateException("EJB spec violation (cap: 7.11.7): Session Bean's Remote Interface must extend the javax.ejb.EJBObject interface.");
			}

			int createCount = 0;
			for (Method iMethod : homeIntfClass.getMethods()) {
				if (iMethod.getName().equals("create")) {
					if (!iMethod.getReturnType().isAssignableFrom(remoteIntfClass)) {
						throw new IllegalStateException("EJB spec violation (cap: 7.8): For Remote Home's create() methods, the return type must be beans's remote interface.");
					}
					Method cMethod = null;
					try {
						cMethod = ejbClass.getMethod("ejbCreate", iMethod.getParameterTypes());
					} catch (NoSuchMethodException e) {
						IllegalStateException error = new IllegalStateException("EJB spec violation (cap: 7.8): Session Bean class must have one ejbCreate(...) method for each create(...) method present on home interface.");
						error.initCause(e);
						throw error;
					}
					if (!cMethod.getReturnType().equals(void.class)) {
						throw new IllegalStateException("EJB spec violation (cap: 7.8): ejbCreate(...) methods on Session Bean class must return void.");
					}

					createCount++;
				}
			}

			if (createCount <= 0) {
				throw new IllegalStateException("EJB spec violation (cap: 7.8): No valid create() method found on bean's Remote Home interface.");
			}

		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, List<EJBMethodTransactionInfo>> processAssemblyDescriptor(Element xml) throws Exception {
		Map<String, List<EJBMethodTransactionInfo>> result = new HashMap<String, List<EJBMethodTransactionInfo>>();

		Element assemblyDescriptorElem = xml.getChild("assembly-descriptor");

		if (assemblyDescriptorElem != null) {
			List<Element> containerTransactionElems = assemblyDescriptorElem.getChildren("container-transaction");

			for (Iterator<Element> ite = containerTransactionElems.iterator(); ite.hasNext();) {
				Element containerTransactionElem = ite.next();

				if (XMLStuff.checkRequiredChildren(containerTransactionElem, "method", "trans-attribute")) {
					List<Element> methodElems = containerTransactionElem.getChildren("method");
					TRANSACTION_TYPE txType = TRANSACTION_TYPE.valueOf(XMLStuff.getChildElementText(containerTransactionElem, "trans-attribute"));

					for (Iterator<Element> ite2 = methodElems.iterator(); ite2.hasNext();) {
						Element methodElem = ite2.next();

						if (XMLStuff.checkRequiredChildren(methodElem, "ejb-name", "method-name")) {
							String ejbName = XMLStuff.getChildElementText(methodElem, "ejb-name");

							List<EJBMethodTransactionInfo> methods = result.get(ejbName);

							if (methods == null) {
								methods = new ArrayList<EJBMethodTransactionInfo>();
								result.put(ejbName, methods);
							}

							METHOD_INTF methodIntf = METHOD_INTF.valueOf(XMLStuff.getChildElementText(methodElem, "method-intf", "Unknown"));

							String methodName = XMLStuff.getChildElementText(methodElem, "method-name");
							String signature = buildMethodSignature(methodElem, methodName, methodIntf);

							EJBMethodTransactionInfo ejbMethod = new EJBMethodTransactionInfo(methodName, signature, txType, methodIntf);

							methods.add(ejbMethod);
						}
					}
				}
			}
		}

		return result;
	}

	@SuppressWarnings("unchecked")
	private String buildMethodSignature(Element methodEle, String methodName, METHOD_INTF methodIntf) throws Exception {
		StringBuilder b = new StringBuilder();

		b.append(methodIntf.toString()).append("@").append(methodName).append("(");

		Element params = methodEle.getChild("method-params");

		int pCount = 0;

		if (params != null) {
			for (Iterator<Element> ite = params.getChildren("method-param").iterator(); ite.hasNext();) {
				Element paramElem = ite.next();
				if (pCount > 0) {
					b.append(",");
				}
				b.append(paramElem.getTextTrim());
				pCount++;
			}
		}

		if (pCount == 0) {
			b.append("void");
		}

		b.append(")");

		return b.toString();
	}

	public boolean isUseSingletonForStateless() {
		return useSingletonForStateless;
	}

	public void setUseSingletonForStateless(boolean useSingletonForStateless) {
		this.useSingletonForStateless = useSingletonForStateless;
	}

	public long getPooledBeanMaxAge() {
		return pooledBeanMaxAge;
	}

	public void setPooledBeanMaxAge(long pooledBeanMaxAge) {
		this.pooledBeanMaxAge = pooledBeanMaxAge;
	}

	public void setJndiResolver(IJndiResolver jndiResolver) {
		this.jndiResolver = jndiResolver;
	}

	public static enum ContainerStatus {
		NORMAL, SHUT_DOWN
	}

	public long getConcurrentCallWaitTimeout() {
		return concurrentCallWaitTimeout;
	}

	public void setConcurrentCallWaitTimeout(long concurrentCallWaitTimeout) {
		this.concurrentCallWaitTimeout = concurrentCallWaitTimeout;
	}

	public int getRandomTimeToWaitOnStatefulCalls() {
		return randomTimeToWaitOnStatefulCalls;
	}

	public void setRandomTimeToWaitOnStatefulCalls(int randomTimeToWaitOnStatefulCalls) {
		this.randomTimeToWaitOnStatefulCalls = randomTimeToWaitOnStatefulCalls;
	}

	public void deployModuleFromDescriptor(InputStream resourceAsStream) throws Exception {
		deployModuleFromDescriptor(Collections.singletonList(resourceAsStream));
		
	}
}
