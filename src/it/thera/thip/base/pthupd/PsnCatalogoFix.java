package it.thera.thip.base.pthupd;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Vector;

import com.thera.thermfw.common.BaseComponentsCollection;
import com.thera.thermfw.common.BusinessObject;
import com.thera.thermfw.common.Deletable;
import com.thera.thermfw.common.ErrorMessage;
import com.thera.thermfw.log.LoggablePersistentObject;
import com.thera.thermfw.persist.ConnectionManager;
import com.thera.thermfw.persist.Factory;
import com.thera.thermfw.persist.KeyHelper;
import com.thera.thermfw.persist.PersistentObject;
import com.thera.thermfw.persist.TableManager;
import com.thera.thermfw.security.Security;

public class PsnCatalogoFix extends LoggablePersistentObject implements BusinessObject, Deletable{
	
	public static final String PROPERTIES = "it.thera.thip.base.pthupd.resources.PsnCatalogoFix";
	
	private static PsnCatalogoFix cInstance;
	protected Timestamp ivTimestamp = null;
	
	protected String ivMailTo  = "";
	protected String ivRepositoryFix = "";
	protected String ivDirectoryBlat = "";
	
	public Timestamp getTimestamp() {
		return ivTimestamp;
	}

	public void setTimestamp(Timestamp ivTimestamp) {
		this.ivTimestamp = ivTimestamp;
	}

	protected String ivUtente = "";
	protected String ivPassword = "";
	protected Integer ivId;
	
	public Integer getId() {
		return ivId;
	}

	public void setId(Integer ivId) {
		this.ivId = ivId;
		setDirty();
		setOnDB(false);
	}

	public String getMailTo() {
		return ivMailTo;
	}

	public void setMailTo(String iMailTo) {
		this.ivMailTo = iMailTo;
		setDirty();
	}

	public String getRepositoryFix() {
		return ivRepositoryFix;
	}

	public void setRepositoryFix(String ivRepositoryFix) {
		this.ivRepositoryFix = ivRepositoryFix;
		setDirty();
	}

	public String getDirectoryBlat() {
		return ivDirectoryBlat;
	}

	public void setDirectoryBlat(String ivDirectoryBlat) {
		this.ivDirectoryBlat = ivDirectoryBlat;
		setDirty();
	}

	public String getUtente() {
		return ivUtente;
	}

	public void setUtente(String ivUtente) {
		this.ivUtente = ivUtente;
		setDirty();
	}

	public String getPassword() {
		return ivPassword;
	}

	public void setPassword(String ivPassword) {
		this.ivPassword = ivPassword;
		setDirty();
	}

	public ErrorMessage checkDelete() {
		return null;
	}

	public Vector checkAll(BaseComponentsCollection arg0) {
		return null;
	}

	public static Vector retrieveList(String where, String orderBy, boolean optimistic) throws SQLException, ClassNotFoundException, InstantiationException, IllegalAccessException	{
		if (cInstance == null)
			cInstance = (PsnCatalogoFix)Factory.createObject(PsnCatalogoFix.class);
		return PersistentObject.retrieveList(cInstance, where, orderBy, optimistic);
	}
	
	public static PsnCatalogoFix elementWithKey(String key, int lockType) throws SQLException {
		return (PsnCatalogoFix) PersistentObject.elementWithKey(PsnCatalogoFix.class, key, lockType);
	}
	
	protected TableManager getTableManager() throws SQLException {
		return PsnCatalogoFixTM.getInstance();
	}

	public String getKey() {
		Object[] keyPart = {getId()};
		return KeyHelper.buildObjectKey(keyPart);
	}

	public void setKey(String key) {
		String keyId = KeyHelper.getTokenObjectKey(key, 1);
		setId(new Integer(keyId));
		
	}
	
	public int save() throws SQLException
	{
	    if (!onDB)
	    {
	      setId(new Integer(0));
	    }
	    
	    return super.save();
	  }
	
	public static void main(String[] args) throws SQLException {
		Security.setCurrentDatabase("SVILPW" , null); 
		Security.openDefaultConnection(); 
		PsnCatalogoFix psn = new PsnCatalogoFix();
		psn.setId(0);
		psn.retrieve();
		ConnectionManager.commit();
		Security.closeDefaultConnection(); 
	}
}