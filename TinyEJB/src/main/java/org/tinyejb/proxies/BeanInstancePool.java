package org.tinyejb.proxies;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ejb.SessionBean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinyejb.core.EJBMetadata;

/**
 * Stateless SessionBean instance pool
 * 
 * @author Cl�udio Gualberto
 * 19/09/2014
 *
 */
public class BeanInstancePool {
	private final static Logger LOGGER = LoggerFactory.getLogger(BeanInstancePool.class);
	//EJB that owns this pool
	private EJBMetadata ejbMetadata;

	//pooled instances managed by this pool
	private List<PoolEntry> instancePool = new ArrayList<PoolEntry>();

	//instance factory
	private BeanInstanceFactory factory;

	//global resizer task. It notifies the pools frequently to resize themselves
	private static PoolResizerTask resizer;

	static {
		resizer = new PoolResizerTask();

		Thread t = new Thread(resizer, "TinyEJB pool resizer task");
		t.setDaemon(true);

		t.start();
	}

	public BeanInstancePool(EJBMetadata ejbMetadata, BeanInstanceFactory factory) {
		this.ejbMetadata = ejbMetadata;
		this.factory = factory;

		//registry this pool on resizer
		resizer.registryPool(this);

	}

	public void shutdown() {
		resizer.unregistry(this);
	}

	public SessionBean getFromPool() {
		SessionBean result = null;

		synchronized (instancePool) {
			if (!instancePool.isEmpty()) {
				//gets always the youngest instance
				result = instancePool.remove(instancePool.size() - 1).instance;
			} else {
				//empty pool, so ask the factory
				result = factory.build();
			}
		}

		return result;
	}

	public void runResizing() {
		synchronized (instancePool) {
			long now = System.currentTimeMillis();

			int removedCount = 0;

			for (Iterator<PoolEntry> ite = instancePool.iterator(); ite.hasNext();) {
				PoolEntry e = ite.next();

				if (now - e.idleSince > ejbMetadata.getEjbContainer().getPooledBeanMaxAge()) {
					removedCount++;
					ite.remove();
				}
			}
			if (removedCount > 0) {
				LOGGER.info(removedCount + " SessionBean instances removed from '" + ejbMetadata.getName() + "'s pool.");
			}
		}
	}

	/**
	 * returns a instance to the pool, so it can be used again later (if it don't expires)
	 * @param sb
	 */
	public void returnToPool(SessionBean sb) {
		synchronized (instancePool) {
			PoolEntry e = new PoolEntry();
			e.idleSince = System.currentTimeMillis();
			e.instance = sb;
			instancePool.add(e);
		}
	}

	private static class PoolEntry {
		long idleSince;
		SessionBean instance;
	}

	private static class PoolResizerTask implements Runnable {
		//registry for pools. 
		private Map<String, WeakReference<BeanInstancePool>> registeredPools = new HashMap<String, WeakReference<BeanInstancePool>>();

		public void registryPool(BeanInstancePool pool) {
			synchronized (registeredPools) {
				registeredPools.put(pool.ejbMetadata.getEjbClassName(), new WeakReference<BeanInstancePool>(pool));
			}
		}

		public void unregistry(BeanInstancePool pool) {
			synchronized (registeredPools) {
				registeredPools.remove(pool.ejbMetadata.getEjbClassName());
			}
		}

		@Override
		public void run() {
			LOGGER.info("Starting InstancePool resizer task");

			try {
				while (!Thread.currentThread().isInterrupted()) {
					synchronized (registeredPools) {
						for (Iterator<WeakReference<BeanInstancePool>> ite = registeredPools.values().iterator(); ite.hasNext();) {
							try {
								WeakReference<BeanInstancePool> entry = ite.next();
								BeanInstancePool pool = entry.get();

								if (pool != null) {
									//notifies the pool
									pool.runResizing();
								} else {
									ite.remove();
								}
							} catch (Exception ignored) {
							}
						}
					}

					Thread.sleep(5000); //snooze...
				}
			} catch (Exception ignored) {
			} finally {
				LOGGER.info("InstancePool resizer finalized");
			}

		}
	}

	/**
	 * Interface for the Bean instance factory
	 * The pool delegates instance creation for it
	 * 
	 * @author Cl�udio Gualberto
	 * 19/09/2014
	 *
	 */
	public static interface BeanInstanceFactory {
		SessionBean build();
	}
}
