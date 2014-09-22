package org.tinyejb.core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EJBMetadata {
	private String name;
	private BEAN_TYPE type;
	private TRANSACTION_MANAGED_BY txManagedBy;
	private TRANSACTION_TYPE defaultTxType;
	private Map<String, EJBMethodTransactionInfo> methodsTransactionInfo;
	private String ejbClassName;
	private String homeIntf;
	private String remoteIntf;
	private String localHomeIntf;
	private String localIntf;
	private List<String> jndiNames;
	private EJBContainer ejbContainer;

	public EJBMetadata(String name, BEAN_TYPE type, TRANSACTION_MANAGED_BY txManagedBy, EJBContainer ejbContainer) {
		this.name = name;
		this.type = type;
		this.txManagedBy = txManagedBy;
		this.jndiNames = new ArrayList<String>();
		this.defaultTxType = TRANSACTION_TYPE.Required;
		methodsTransactionInfo = new HashMap<String, EJBMethodTransactionInfo>();
		this.ejbContainer = ejbContainer;
	}

	public void addMethodTransactionInfo(List<EJBMethodTransactionInfo> mList) {
		for (EJBMethodTransactionInfo m : mList) {
			if (m.getName().equals("*")) { //default para marcação de TX padrão
				this.defaultTxType = m.txType;
			} else {
				methodsTransactionInfo.put(m.getSignature(), m);
			}
		}
	}
	
	public EJBMethodTransactionInfo getTransactionInfoForMethod(Method m) throws Exception {
		String baseMethodID = buildMethodID(m);
		
		if(isLocalObjectInterface(m)){
			baseMethodID = "Local@" + baseMethodID;
		}else{
			baseMethodID = "Remote@" + baseMethodID;	
		}
		
		return methodsTransactionInfo.get(baseMethodID);
	}
	
	private String buildMethodID(Method m) {
		StringBuilder b = new StringBuilder();
		b.append(m.getName()).append("(");
		
		int i = 0;
		for(Class pType : m.getParameterTypes()){
			if(i > 0){
				b.append(",");
			}
			b.append(pType.getName());
			i++;
		}
		
		if(i == 0){
			b.append("void");
		}
		
		b.append(")");
		
		return b.toString();
	}
	
	public boolean isLocalObjectInterface(Method m) throws Exception {
		return m.getDeclaringClass().getName().equals(getLocalIntf());
	}
	
	public boolean isStateless(){
		return type.equals(BEAN_TYPE.Stateless);
	}


	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append("Bean name: ").append(name).append("\n");
		b.append("Bean type: ").append(type).append("\n");
		b.append("tx.managed: ").append(txManagedBy).append("\n");
		b.append("def.tx.type: ").append(defaultTxType).append("\n");
		b.append("home.intf: ").append(homeIntf).append("\n");
		b.append("remote.intf: ").append(remoteIntf).append("\n");
		b.append("localhome.intf: ").append(localHomeIntf).append("\n");
		b.append("local.intf: ").append(localIntf).append("\n");
		b.append("methods:").append("\n");

		for (EJBMethodTransactionInfo m : methodsTransactionInfo.values()) {
			b.append(m).append("\n");
		}

		return b.toString();
	}

	public static class EJBMethodTransactionInfo {
		private String name;
		private String signature;
		private TRANSACTION_TYPE txType;
		private METHOD_INTF methodIntf;

		public EJBMethodTransactionInfo(String name, String signature, TRANSACTION_TYPE txType, METHOD_INTF methodIntf) {
			this.name = name;
			this.signature = signature;
			this.txType = txType;
			this.methodIntf = methodIntf;
		}

		public String getName() {
			return name;
		}

		public String getSignature() {
			return signature;
		}

		public TRANSACTION_TYPE getTxType() {
			return txType;
		}

		public String toString() {
			StringBuilder b = new StringBuilder();
			b.append("method: ").append(signature).append(", txType: ").append(txType).append(", intf: ").append(methodIntf);
			return b.toString();
		}

		public static enum METHOD_INTF {
			Home, Remote, LocalHome, Local, Unknown;
		}
	}

	public static enum BEAN_TYPE {
		Stateless, Stateful;
	}

	public static enum TRANSACTION_TYPE {
		Required, RequiresNew, NotSupported, Supports, Mandatory, Never;
	}

	public static enum TRANSACTION_MANAGED_BY {
		Container, Bean;
	}

	public String getEjbClassName() {
		return ejbClassName;
	}

	public void setEjbClassName(String ejbClassName) {
		this.ejbClassName = ejbClassName;
	}

	public String getHomeIntf() {
		return homeIntf;
	}

	public void setHomeIntf(String homeIntf) {
		this.homeIntf = homeIntf;
	}

	public String getLocalHomeIntf() {
		return localHomeIntf;
	}

	public void setLocalHomeIntf(String localHomeIntf) {
		this.localHomeIntf = localHomeIntf;
	}

	public String getRemoteIntf() {
		return remoteIntf;
	}

	public void setRemoteIntf(String remoteIntf) {
		this.remoteIntf = remoteIntf;
	}

	public String getName() {
		return name;
	}

	public BEAN_TYPE getType() {
		return type;
	}

	public TRANSACTION_MANAGED_BY getTxManagedBy() {
		return txManagedBy;
	}

	public TRANSACTION_TYPE getDefaultTxType() {
		return defaultTxType;
	}

	public String getLocalIntf() {
		return localIntf;
	}

	public void setLocalIntf(String localIntf) {
		this.localIntf = localIntf;
	}

	public String[] getJndiNames() {
		return jndiNames.toArray(new String[] {});
	}

	public void addJndiName(String jndiName) {
		this.jndiNames.add(jndiName);
	}

	public EJBContainer getEjbContainer() {
		return ejbContainer;
	}
}
