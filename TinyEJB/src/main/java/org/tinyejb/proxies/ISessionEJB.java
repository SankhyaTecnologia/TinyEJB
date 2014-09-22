package org.tinyejb.proxies;

import javax.ejb.SessionBean;

public interface ISessionEJB {
	void setDelegationInstance(SessionBean bean);
	void setBeanInstancePool(BeanInstancePool pool);
}
