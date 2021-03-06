package org.tinyejb.core;

import java.io.InputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jdom.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinyejb.utils.XMLStuff;

/**
 * IJndiResolver implementation for JBoss specific deployment descriptor file (jboss.xml)
 * This class reads the descriptor file and creates a cache of JNDI names declared on enterprise-beans/session elements.
 * 
 * Other elements defined on jboss_x.dtd are simply ignored. 
 * 
 * @author Cláudio Gualberto
 * 
 * 19/09/2014
 *
 */
public class JBossJndiResolver implements IJndiResolver {
	private static final long		serialVersionUID	= 1L;

	private final static Logger		LOGGER				= LoggerFactory.getLogger(JBossJndiResolver.class);

	private Map<String, BeanEntry>	entries				= new HashMap<String, JBossJndiResolver.BeanEntry>();

	@SuppressWarnings("unchecked")
	public static IJndiResolver buildFromJBossDescriptor(List<InputStream> descriptorsInput) throws Exception {
		if (descriptorsInput.isEmpty()) {
			throw new IllegalArgumentException("stream para o descritor JBoss deve ser informado.");
		}

		JBossJndiResolver result = new JBossJndiResolver();
		for (InputStream descriptorInput : descriptorsInput) {

			Element xml = XMLStuff.buildDomDocument(descriptorInput).getRootElement();
			descriptorInput.close();
			if (XMLStuff.checkRequiredChildren(xml, "enterprise-beans")) {
				Element enterpriseBeansElem = xml.getChild("enterprise-beans");

				if (XMLStuff.checkRequiredChildren(enterpriseBeansElem, "session")) {
					for (Iterator<Element> ite = enterpriseBeansElem.getChildren("session").iterator(); ite.hasNext();) {
						Element sessionElem = ite.next();
						if (XMLStuff.checkRequiredChildren(sessionElem, "ejb-name")) {
							String name = XMLStuff.getChildElementText(sessionElem, "ejb-name");
							if (!result.entries.containsKey(name)) {
								BeanEntry be = new BeanEntry();
								be.localHomeName = XMLStuff.getChildElementText(sessionElem, "local-jndi-name");
								be.remoteHomeName = XMLStuff.getChildElementText(sessionElem, "jndi-name");
								result.entries.put(name, be);
							} else {
								LOGGER.info("Duplicated bean definition on JBoss descriptor: " + name);
							}
						}
					}
				}
			}
		}
		return result;
	}

	@Override
	public String getRemoteJdniName(EJBMetadata ejb) {
		BeanEntry be = entries.get(ejb.getName());

		if (be != null) {
			return be.remoteHomeName;
		}

		return null;
	}

	@Override
	public String getLocalJdniName(EJBMetadata ejb) {
		BeanEntry be = entries.get(ejb.getName());

		if (be != null) {
			return be.localHomeName;
		}

		return null;
	}

	private static class BeanEntry implements Serializable {
		/**
		 * 
		 */
		private static final long	serialVersionUID	= 1L;
		String	remoteHomeName;
		String	localHomeName;
	}

	public static IJndiResolver buildFromJBossDescriptor(InputStream resourceAsStream) throws Exception {
		return buildFromJBossDescriptor(Collections.singletonList(resourceAsStream));
	}
}
