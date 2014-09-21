package org.tinyejb.test;

import java.util.ArrayList;
import java.util.List;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;

import org.tinyejb.utils.Logger;

public class MockTransacionManager implements TransactionManager {
	private static ThreadLocal<Transaction> current = new ThreadLocal<Transaction>();

	@Override
	public void begin() throws NotSupportedException, SystemException {
		Transaction tx = new MockTransacion();
		current.set(tx);
		Logger.log("Transaction started!!");
	}

	@Override
	public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, IllegalStateException, SystemException {
		getCurrent().commit();
	}

	private Transaction getCurrent() {
		Transaction tx = current.get();

		if (tx == null) {
			throw new IllegalStateException("No current transaction");
		}
		return tx;
	}

	@Override
	public int getStatus() throws SystemException {
		return getCurrent().getStatus();
	}

	@Override
	public Transaction getTransaction() throws SystemException {
		return current.get();
	}

	@Override
	public void resume(Transaction tx) throws InvalidTransactionException, IllegalStateException, SystemException {
		Logger.log("Transaction resumed!!");
		current.set(tx);
	}

	@Override
	public void rollback() throws IllegalStateException, SecurityException, SystemException {
		getCurrent().rollback();
	}

	@Override
	public void setRollbackOnly() throws IllegalStateException, SystemException {
		getCurrent().setRollbackOnly();
	}

	@Override
	public void setTransactionTimeout(int arg0) throws SystemException {
	}

	@Override
	public Transaction suspend() throws SystemException {
		Transaction tx = current.get();
		current.remove();
		Logger.log("Transaction suspended!!");
		return tx;
	}

	private static class MockTransacion implements Transaction {
		private int status = Status.STATUS_ACTIVE;
		private List<Synchronization> syncList = new ArrayList<Synchronization>();

		@Override
		public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, IllegalStateException, SystemException {
			if (getStatus() != Status.STATUS_ACTIVE) {
				throw new IllegalStateException("Transaction can not be commited, because is not active");
			}

			status = Status.STATUS_COMMITTING;

			callBeforeCompletion();

			status = Status.STATUS_COMMITTED;
			Logger.log("Transaction commited!!");

			callAfterCompletion();
		}

		private void callBeforeCompletion() {
			for (Synchronization s : syncList) {
				try {
					s.beforeCompletion();
				} catch (Exception ignored) {
				}
			}
		}

		private void callAfterCompletion() {
			for (Synchronization s : syncList) {
				try {
					s.afterCompletion(status);
				} catch (Exception ignored) {
				}
			}
		}

		@Override
		public boolean delistResource(XAResource arg0, int arg1) throws IllegalStateException, SystemException {
			return true;
		}

		@Override
		public boolean enlistResource(XAResource arg0) throws RollbackException, IllegalStateException, SystemException {
			return true;
		}

		@Override
		public int getStatus() throws SystemException {
			return status;
		}

		@Override
		public void registerSynchronization(Synchronization sync) throws RollbackException, IllegalStateException, SystemException {
			syncList.add(sync);
		}

		@Override
		public void rollback() throws IllegalStateException, SystemException {
			if (getStatus() != Status.STATUS_ACTIVE && getStatus() != Status.STATUS_MARKED_ROLLBACK) {
				throw new IllegalStateException("Transaction can not be rolledback, because is not active neither marked for rollback");
			}

			status = Status.STATUS_ROLLING_BACK;

			callBeforeCompletion();

			status = Status.STATUS_ROLLEDBACK;
			Logger.log("Transaction RolledBack!!");

			callAfterCompletion();
		}

		@Override
		public void setRollbackOnly() throws IllegalStateException, SystemException {
			if (getStatus() == Status.STATUS_MARKED_ROLLBACK) {
				return;
			}

			if (getStatus() != Status.STATUS_ACTIVE) {
				throw new IllegalStateException("Transaction can not be marked for rollback, because is not active");
			}

			status = Status.STATUS_MARKED_ROLLBACK;

			Logger.log("Transaction marked to rollback");
		}

	}

}
