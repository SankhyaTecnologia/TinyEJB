package org.tinyejb.core;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.tinyejb.utils.Logger;
import org.tinyejb.utils.XMLStuff;

/**
 * IJndiResolver implementation for JBoss specific deployment descriptor file (jboss.xml)
 * This class reads the descriptor file and creates a cache of JNDI names declared on enterprise-beans/session elements.
 * 
 * Other elements defined on jboss_x.dtd are simply ignored. 
 * 
 * @author Cl�udio Gualberto
 * 
 * 19/09/2014
 *
 */
public class JBossJndiResolver implements IJndiResolver {
	private Map<String, BeanEntry> entries = new HashMap<String, JBossJndiResolver.BeanEntry>();

	public static IJndiResolver buildFromJBossDescriptor(InputStream descriptorInput) throws Exception {
		if (descriptorInput == null) {
			throw new IllegalArgumentException("stream para o descritor JBoss deve ser informado.");
		}

		JBossJndiResolver result = new JBossJndiResolver();

		Element xml = XMLStuff.buildDomDocument(descriptorInput).getRootElement();

		if (XMLStuff.checkRequiredChildren(xml, "enterprise-beans")) {
			Element enterpriseBeansElem = xml.getChild("enterprise-beans");

			if (XMLStuff.checkRequiredChildren(enterpriseBeansElem, "session")) {
				for (Iterator ite = enterpriseBeansElem.getChildren("session").iterator(); ite.hasNext();) {
					Element sessionElem = (Element) ite.next();
					if (XMLStuff.checkRequiredChildren(sessionElem, "ejb-name")) {
						String name = XMLStuff.getChildElementText(sessionElem, "ejb-name");
						if (!result.entries.containsKey(name)) {
							BeanEntry be = new BeanEntry();
							be.localHomeName = XMLStuff.getChildElementText(sessionElem, "local-jndi-name");
							be.remoteHomeName = XMLStuff.getChildElementText(sessionElem, "jndi-name");
							result.entries.put(name, be);
						} else {
							Logger.log("Duplicated bean definition on JBoss descriptor: " + name);
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

	private static class BeanEntry {
		String remoteHomeName;
		String localHomeName;
	}
}