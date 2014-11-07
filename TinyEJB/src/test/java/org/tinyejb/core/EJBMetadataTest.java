package org.tinyejb.core;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.tinyejb.core.EJBMetadata.BEAN_TYPE;
import org.tinyejb.core.EJBMetadata.EJBMethodTransactionInfo;
import org.tinyejb.core.EJBMetadata.EJBMethodTransactionInfo.METHOD_INTF;
import org.tinyejb.core.EJBMetadata.TRANSACTION_TYPE;

public class EJBMetadataTest {
	private EJBMetadata	ejbMetadata;

	@Before
	public void setUp() throws Exception {
		ejbMetadata = new EJBMetadata("ejb1", BEAN_TYPE.Stateless, null, null);
	}

	@Test
	public void testDefaultTxType() {

		assertEquals(TRANSACTION_TYPE.Required, ejbMetadata.getDefaultTxType());
		List<EJBMethodTransactionInfo> mList = new ArrayList<>();
		mList.add(new EJBMethodTransactionInfo("*", "Unknown@*(void)", TRANSACTION_TYPE.Supports, METHOD_INTF.Unknown));

		ejbMetadata.addMethodTransactionInfo(mList);
		assertEquals(TRANSACTION_TYPE.Supports, ejbMetadata.getDefaultTxType());

	}
}
