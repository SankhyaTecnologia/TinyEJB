package org.tinyejb.test.ejbs.stateless;

import javax.ejb.EJBException;
import javax.ejb.EJBObject;

public interface InvoiceFacade extends EJBObject {
	double insertInvoice(double totalAmount, double importFees) throws Exception, EJBException;

	void removeInvoiceRollingBack() throws EJBException;

	void removeInvoiceThrowingSystemException() throws EJBException;
}
