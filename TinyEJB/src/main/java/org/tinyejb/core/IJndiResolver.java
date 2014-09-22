package org.tinyejb.core;

/**
 * Interface for JNDI names resolvers.
 * By defauly, TinyEJB deploys home interfaces on 'java:comp/env/ejb/' JNDI path, as defined by EJB spec 2.x
 *  
 * If your application is dependent on another path, you can implements this interface and configure EJBContainer to use it.
 *  
 * @author Cláudio Gualberto
 * 19/09/2014
 *
 */
public interface IJndiResolver {
	String getRemoteJdniName(EJBMetadata ejb);

	String getLocalJdniName(EJBMetadata ejb);
}