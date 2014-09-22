package org.tinyejb.core;

import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.tinyejb.core.EJBMetadata.BEAN_TYPE;
import org.tinyejb.test.CartTest;

public class JBossJndiResolverTest {
	private IJndiResolver jndi;
	private final EJBMetadata cartEJB = new EJBMetadata("Cart",
			BEAN_TYPE.Stateful, null, null);
	private final EJBMetadata notExistsEJB = new EJBMetadata("notExistsEJB",
			BEAN_TYPE.Stateless, null, null);

	@Before
	public void setUp() throws Exception {
		jndi = JBossJndiResolver.buildFromJBossDescriptor(CartTest.class
				.getResourceAsStream("/tinyejb-jboss.xml"));
	}

	@Test
	public void testCart() {
		assertEquals("CartLocalHome", jndi.getLocalJdniName(cartEJB));
		assertEquals("CartHome", jndi.getRemoteJdniName(cartEJB));
	}

	@Test
	public void testInvoice() {
		assertEquals(null, jndi.getRemoteJdniName(notExistsEJB));
		assertEquals(null, jndi.getLocalJdniName(notExistsEJB));
	}

}
