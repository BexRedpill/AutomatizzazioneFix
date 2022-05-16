package it.thera.thip.base.pthupd;

import java.sql.SQLException;

import com.thera.thermfw.base.SystemParam;
import com.thera.thermfw.persist.Factory;
import com.thera.thermfw.persist.TableManager;

public class PsnCatalogoFixTM extends TableManager{
	 
	public static final String MAIL_TO     				= "MAIL_TO";
	public static final String REPOSITORY_FIX    		= "REPOSITORY_FIX";
	public static final String DIRECTORY_BLAT     		= "DIR_BLAT";
	public static final String UTENTE_DOMINO     		= "UTENTE_DOMINO";
	public static final String PWD_UTENTE_DOMINO        = "PWD_UTENTE_DOMINO";
	public static final String ID						= "ID";
	public static final String TIMESTAMP				= "TIMESTAMP";
	
	private static TableManager cInstance;
	
	private static final String CLASS_NAME = PsnCatalogoFix.class.getName();
	
	public static final String TABLE_NAME = SystemParam.getSchema("THIP")+ "PSN_CATALOGO_FIX";
	
	public PsnCatalogoFixTM() throws SQLException{
		super();
	}
	
	public synchronized static TableManager getInstance() throws SQLException {
		if(cInstance == null) {
			cInstance = (TableManager) Factory.createObject(PsnCatalogoFixTM.class);
		}
		return cInstance;
	}
	
	protected void initialize() throws SQLException {
		setTableName(TABLE_NAME);
		setObjClassName(CLASS_NAME);
		init();
	}
	
	protected void initializeRelation() throws SQLException {
		addAttribute("MailTo" , MAIL_TO);
		addAttribute("RepositoryFix" , REPOSITORY_FIX);
		addAttribute("DirectoryBlat" , DIRECTORY_BLAT);
		addAttribute("Utente" , UTENTE_DOMINO);
		addAttribute("Password" , PWD_UTENTE_DOMINO);
		addAttribute("Id", ID);
		addAttribute("Timestamp" , TIMESTAMP);
		setKeys(ID);
		setTimestampColumn(TIMESTAMP);
	}
    
	 private void init() throws SQLException
	 {
		 configure();
	 }

}
