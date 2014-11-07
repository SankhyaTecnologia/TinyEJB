package org.tinyejb.proxies;

import java.io.Serializable;

public interface IEJBHome extends Serializable{
	void onContainerShutDown();
}
