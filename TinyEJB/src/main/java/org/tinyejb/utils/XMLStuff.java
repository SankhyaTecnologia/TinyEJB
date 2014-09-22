package org.tinyejb.utils;

import java.io.InputStream;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

public class XMLStuff {
	public static boolean checkRequiredChildren(Element xmlElem, String... elements) {
		int errors = 0;

		for (String elemName : elements) {
			Element elem = xmlElem.getChild(elemName);

			if (elem == null) {
				Logger.log("element <" + xmlElem.getName() + "> must contain <" + elemName + "> as a child element");
				errors++;
			}
		}

		return errors == 0;
	}

	public static String getChildElementText(Element parentElem, String childName) {
		return getChildElementText(parentElem, childName, null);
	}

	public static String getChildElementText(Element parentElem, String childName, String def) {
		Element childElem = parentElem.getChild(childName);

		if (childElem == null) {
			return def;
		}

		String text = childElem.getTextTrim();

		return text != null && text.length() == 0 ? null : text;
	}

	public static Document buildDomDocument(InputStream ejbDDInputStream) throws Exception {
		SAXBuilder builder = new SAXBuilder();
		Document doc = builder.build(ejbDDInputStream);
		return doc;
	}
}
