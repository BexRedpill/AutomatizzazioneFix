package it.thera.thip.cs;

import java.io.*;
import java.sql.*;
import java.util.*;
import com.thera.thermfw.base.*;
import com.thera.thermfw.dict.*;
import com.thera.thermfw.persist.*;
import com.thera.thermfw.security.*;
import com.thera.thermfw.setup.*;

import it.thera.thip.base.util.*;
import com.thera.thermfw.setup.gui.FixRunner;
import com.thera.thermfw.util.file.FileUtil;

/*
 * Revisions:
 * Number  Date          Owner  Description
 * 03556   07/04/05      MN     Se una vista non viene trovata viene stampato un messaggio
																e si procede con il drop delle altre viste.
 * 04263   02/09/2005    PM     Aggiunta logica di rigenerazione delle viste quando si applicano le fix in ambiente SQLServer
 * 04397   29/09/2005    PM     Aggiunta System.out. che dice in che ambiente vengono applicate le fix
 * 04496   13/10/2005    PM     Aggiunta la possibilita di rigenerare le viste personalizzate
 * 04690   24/11/2005    PJ     drop view sqlserver: usato executeUpdate() al posto di execute()
 * 04668   09/11/2005    PM     Impostati come properties i parametri con cui viene eseguita la classe.
 * 04897   17/01/2006    PM     Eliminata gestione logica di rigenerazione delle fix per AS400.
 *                              E' stata spostata nella classe AS400ThipFixFileProcessor
 * 05184   13/04/2006    PM     Le viste contenute nei file VisteDaRigenerare.txt e VistePersonalizzateDaRigenerare.txt
																vengono rigenerate anche se il file ViewSchemas.txt non è stato trovato nel
																classpath. Inoltre i due file vengono cercati prima nella directory delle fix e poi
																nel classpath.
																Le viste non vengono mai rigenerate nel caso in cui il flag OnlyCopyFile sia attivo

 * 05273   05/04/2006    PM     Ridefinizione del metodo resetAutorizableFlag
 *                              in modo tale che la logica implementata
 *                              del metodo sia eseguita solo dopo che
 *                              è stata applicata la fix 5272 di therm.
 *                              Al prossimo setup questa fix deve essere cancellata.
 * 05406   18/05/2006    BP     Correzione metodo getVisteDaRigenerare()
 * 05666   11/07/2006    PM     Ridefinito metodo endApplyFix per stampare il numero di fix applicata.
 *                              Sostituito metodo getWebRootFile con getFixRootFile
 * 06674   07/02/2007    PM     Se il file viewschemas.txt non viene trovato vengono impostati
 *                              gli schemi THIP e THIPPERS
 * 06863   20/03/2007    PM     Gestione oracle
 * 06997   27/03/2007    PM     Aggiunta gestione della classe EsecutoreOperazioniPostFix. Tale
																classe consente di eseguire operazioni dopo che le fix son state apllicate
																ed è stata fatta la loro commit.
																Tali operazioni non devono MAI modificare la struttura del database ma solo
																il loro contenuto.
 * 07772   28/07/2007    PM     Modifiche per avere un unico fixes.zip per tutte le piattaforme di Panthera
 * 07939   24/09/2007    DF     Modificata rigenerazion viste
 * 08210   08/11/2007    PM     In oracle se chiedi al dizionario dati la
 *                              definizione di una vista che non c'è, viene
 *                              lanciata un'eccezione SQL.
 * 08651   05/02/2008    PM     Alla fine dell'applicazione delle fix vengono
 *                              copiati i jar di primrose sotto la web application
 * 11107   04/08/2009    DM     Aggiunta chiamata a super in endRun()
 * 13108   30/08/2010    ES     Nella 12771 ho sostituito la costante LOG_ERROR con la lettura della risorsa:
 *                                 deve essere fatto anche qui.
 * 13728   10/12/2010    ES     Flag doneSthg= true se ho eseguito TDDML, SQL o classi e quindi devo anche rigenerare viste, rebuild di son/parent folders e reorg/runstats
 *                                           = false in caso contrario e non devo rigenerare le viste ecc.
 * 14086   10/02/2011    ES     Aggiunta supporto a Plex610: -CF2
 * 14190   22/03/2011    ES     Aggiungo GRANT ALL anche alla parte Thip
 * 15026   20/09/2011    ES     Per Thip viene modificata la politica di copia degli RPT: ignoro la versione di Crystal Reports
 *                               indicata  nelle preferenze applic. e copio tutto quanto c'è sotto print.
 * 18070   18/06/2013    MA     aggiunto la descrizione dell'errore su il log file
 * 28999   13/03/2019    PM     Se dopo avere installato un fastupdate viene installata una fix web questa non risulta nel contextinfo.
 * 30787   22/02/2020    PM     Anche per installazioni con DB2 viene fatta commit ad ogni fix. 
*/

public class ThipFixFileProcessor extends FixFileProcessor {

	public static final String VISTE_DA_RIGENERARE_FILE = "VisteDaRigenerare.txt";

	//Fix 04496 PM Inizio
	public static final String VISTE_PERSALIZZATE_DA_RIGEN_FILE = "VistePersonalizzateDaRigenerare.txt";

	//Fix 04496 PM Fine

	// Fix 6674 PM Inizio
	public static final String SCHEMA_THIP = "THIP";
	public static final String SCHEMA_THIP_PERS = "THIPPERS";

	// Fix 6674 PM Fine
	/*
	* Nome del .jar "spia" che fa capire se sono già stati installati i jar e quindi
	* li aggiorno copiandogli sopra quelli nuovi
	*/
	protected static final String BASE_JAR_NAME = "STANDARD01.jar";


//Fix 04668 PM Inizio
	protected String iFixRoot = "";

//Fix 04668 PM Inzio


//Fix 05273 PM Inizio
	protected boolean iFix5272Installata = false;

//Fix 05273 PM Fine

	protected EsecutoreOperazioniPostFix ivOperazioniPostFix = null; // Fix 6997 PM

//Fix 07772 PM Inizio
	protected boolean iNuovaGestioneFix = true;

//Fix 07772 PM Fine

	protected boolean isUniqueFixNumberFlagPresent(String[] args) {
		return true;
	}

//07939 - DF

	protected static final String SQL_VISTE_DA_RIGENERARE_DB2 = "SELECT TEXT, VIEWSCHEMA, VIEWNAME FROM SYSCAT.VIEWS WHERE VIEWNAME = ? AND VIEWSCHEMA = ?";
	protected static final String SQL_VISTE_DA_RIGENERARE_SQL = "select c.text as text from sysusers u inner join sysobjects v on v.uid = u.uid and v.xtype = 'V' inner join syscomments c on v.id = c.id where v.name = ? and u.name = ?";
	protected static final String SQL_VISTE_DA_RIGENERARE_ORACLE = "select dbms_metadata.get_ddl('VIEW', ?, ?) from dual";

	protected static final String SQL_GRANT_ALL_1 = "GRANT ALL ON "; //Mod. 14190
	protected static final String SQL_GRANT_ALL_2 = " TO PUBLIC"; //Mod. 14190

	/**
	 * Ridefinisco il nome della directory sorgente dei file .RPT per CrystalReport 2008
	 * perché è diverso da quello usato in Therm
	 */
	 public static final String NAME_FIX_RPT_CR2008 = NAME_FIX_RPT + File.separator +"CR2008"; //Mod. 15026
	 protected CachedStatement ivUpdateTipoInstallazioneFix = new CachedStatement("UPDATE " + SystemParam.getSchema("THIP") + 
				"FIXES SET TIPO_INSTALLAZIONE = ? WHERE TIPO_INSTALLAZIONE = ? AND FIX = ?");
	 protected CachedStatement ivSelectTipoInstallazioneFix = new CachedStatement("SELECT TIPO_INSTALLAZIONE FROM " 
				+ SystemParam.getSchema("THIP") + "FIXES WHERE FIX = ?"); //35629
	 

	protected String getSQLVisteDaRigenerare() {
		if (Security.getDatabase() instanceof DB2Database &&
				! (Security.getDatabase() instanceof DB2AS400Database) &&
				! (Security.getDatabase() instanceof DB2ToolboxDatabase))
			return SQL_VISTE_DA_RIGENERARE_DB2;
		else if (Security.getDatabase() instanceof SQLServerDatabase)
			return SQL_VISTE_DA_RIGENERARE_SQL;
		else if (Security.getDatabase() instanceof OracleDatabase)
			return SQL_VISTE_DA_RIGENERARE_ORACLE;
		return null;
	}

	protected int getViewNotFoundErrorCode() {
		if (Security.getDatabase() instanceof DB2Database &&
				! (Security.getDatabase() instanceof DB2AS400Database) &&
				! (Security.getDatabase() instanceof DB2ToolboxDatabase))
			return -204;
		else if (Security.getDatabase() instanceof SQLServerDatabase)
			return 3701;
		else if (Security.getDatabase() instanceof OracleDatabase)
			return 942;
		return 0;
	}

	protected void thipDropViews() throws SQLException
	{
			CachedStatement visteDaRigenerareStm = new CachedStatement(getSQLVisteDaRigenerare());
			Iterator i = viewsList.iterator();
			while (i.hasNext())
			{
					String s = (String)i.next();
					String schemaVista = extractSchema(s);
					String nomeVista = extractName(s);

					Database db = ConnectionManager.getCurrentDatabase();
					db.setString(visteDaRigenerareStm.getStatement(), 1, nomeVista);
					db.setString(visteDaRigenerareStm.getStatement(), 2, schemaVista);

//Fix 8210 PM Inizio
					/*
					 ResultSet rs = visteDaRigenerareStm.executeQuery();
					 String viewDefinition = null;
					 if (rs.next())
					 viewDefinition = rs.getString(1).trim();
					 rs.close();
					 */
					ResultSet rs = null;
					String viewDefinition = null;
					try
					{
							rs = visteDaRigenerareStm.executeQuery();
							if (rs.next())
									viewDefinition = rs.getString(1).trim();
							rs.close();
					}
					catch(SQLException e1)
					{
							if (Security.getDatabase() instanceof OracleDatabase)
							{
									if (e1.getErrorCode() == 31603)
											System.out.println("#### Vista " + schemaVista + "." + nomeVista + " non trovata ####");
									else
											throw e1;
							}
							else
									throw e1;
					}
					finally
					{
							if (rs != null)
									rs.close();
					}
//Fix 8210 PM Fine

					if (viewDefinition != null && viewDefinition.length() > 0)
					{
							CachedStatement eliminaVisteStm = new CachedStatement("drop view " + schemaVista + "." + nomeVista);
							try
							{
									eliminaVisteStm.executeUpdate();
									viewsDefinition.add(viewDefinition);
							}
							catch (SQLException sqle)
							{
									if (sqle.getErrorCode() == getViewNotFoundErrorCode())
									{
											System.out.println("#### Vista " + schemaVista + "." + nomeVista + " non trovata ####");
									}
									else
											throw sqle;
							}
							finally
							{
									eliminaVisteStm.free();
							}
					}
			}
	}

	protected void thipRegenerationViews() throws SQLException {
		int numTentativi = 0;
		int numViews = -1;
		while (viewsDefinition != null && viewsDefinition.size() > 0 && viewsDefinition.size() != numViews && numTentativi < 50) {
			numViews = viewsDefinition.size();
			for (int i = 0; i < viewsDefinition.size(); i++) {
				CachedStatement createView = new CachedStatement( (String) viewsDefinition.get(i));
				try {
					createView.execute();
				}
// Fix 18070 inizio
				catch (SQLException sqle) {
					viewsDefinition.set(i, viewsDefinition.get(i) + "\nDetail Error : " + sqle.getMessage());
					continue;
				}
				finally {
					createView.free();
				}
// Fix 18070 fine
					//Mod. 14190//Aggiungo GRANT ALL
					String viewCompleteName = extractNameFromCreateViewStmt((String)viewsDefinition.get(i));
					String grantStmt = SQL_GRANT_ALL_1 +viewCompleteName.trim() + SQL_GRANT_ALL_2;
					CachedStatement grantAllForView = new CachedStatement(grantStmt);
					try{ // Fix 18070
					boolean okGrant = grantAllForView.execute();
					//fine mod. 14190//
				}
				catch (SQLException sqle) {
					viewsDefinition.set(i, viewsDefinition.get(i)+ getLineSeparator() + grantStmt + getLineSeparator() + "Detail Error : " + sqle.getMessage()); // Fix 18070
						continue;
				}
				finally {
//					createView.free(); Fix 18070
					grantAllForView.free(); // Fix 18070
				}
				viewsDefinition.remove(i);
				i--;
			}
			numTentativi++;
		}
	}
// Fix 18070 inizio
	public static final String getLineSeparator() {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		pw.println();
		return sw.toString();
	}
// Fix 18070 fine
	//Mod. 14190 inizio
	protected String extractNameFromCreateViewStmt(String stmt) {
		int posIni = stmt.indexOf("VIEW");
		int posFin = -1;
		if (posIni > -1) {
			posFin = stmt.indexOf(' ', posIni + 7);
			if (posIni > 0 && posFin > 0 && posIni < posFin) {
				return stmt.substring(posIni + 4, posFin);
			}
		}
		return "";
	}
	//fine mod. 14190

	protected void thipPrintViewErrors() {
		System.out.println("");
		System.out.println(ResourceLoader.getString(SETUP_RES, "ErrorsView", new String[] {String.valueOf(viewsDefinition.size()), FixRunner.FixFileReportName})); //Mod. 14190
		output.println("");
		//Mod. 13108//output.println(LOG_ERROR);
		output.println(ResourceLoader.getString(SETUP_RES, "ErrorString"));//Mod. 13108
		for (int i = 0; i < viewsDefinition.size(); i++)
			output.println(ResourceLoader.getString(SETUP_RES, "RegenerationView", new String[] { (String) viewsDefinition.get(i)}));
		output.println("");
	}

	//07939 - Fine

//Fix 04496 PM Inizio

	/*  protected List getVisteDaRigenerare()
		{
			List ret = new ArrayList();
			BufferedReader visteDaRigenerareFile = getFile(VISTE_DA_RIGENERARE_FILE);
			try
			{
				if (visteDaRigenerareFile != null)
				{
					String readL = visteDaRigenerareFile.readLine();
					while (readL != null)
					{
						readL = readL.trim();
						if (!readL.equals("") && !readL.startsWith("#"))
							ret.add(readL);
						readL = visteDaRigenerareFile.readLine();
					}
				}
			}
			catch(IOException e)
			{

			}
			return ret;
		}
	 */

	protected List getVisteDaRigenerare() {
		//Fix 05184 PM Inizio
		//List ret = getVisteDaRigenerareInternal(VISTE_DA_RIGENERARE_FILE);
		//ret.addAll(getVisteDaRigenerareInternal(VISTE_PERSALIZZATE_DA_RIGEN_FILE));
		List ret = getVisteDaRigenerareInternal(getFileVisteDaRigenerare());
		List vistePersonalizzate = getVisteDaRigenerareInternal(getFileVistePersonalizzateDaRigenerare());
		ret.addAll(vistePersonalizzate);
		if (!vistePersonalizzate.isEmpty()) {
			Iterator i = vistePersonalizzate.iterator();
//Fix 18070 inizio
//			output.println("E' stato richiesto di rigenerare le seguenti fix personalizzate:");
//			System.out.println("E' stato richiesto di rigenerare le seguenti fix personalizzate:");
			output.println("E' stato richiesto di rigenerare le seguenti viste personalizzate:");
			System.out.println("E' stato richiesto di rigenerare le seguenti viste personalizzate:");
//Fix 18070 fine
			while (i.hasNext()) {
				//Fix 5406 BP ini...
				Object vista = i.next();
				output.println(vista);
				System.out.println(vista);
				//output.println(i.next());
				//System.out.println(i.next());
				//Fix 5406 BP fine..
			}
		}
		//Fix 05184 PM Fine
		return ret;
	}

	//protected List getVisteDaRigenerareInternal(String file) //Fix 05184 PM
	protected List getVisteDaRigenerareInternal(BufferedReader file) {
		List ret = new ArrayList();
		//BufferedReader visteDaRigenerareFile = getFile(file); //Fix 05184 PM
		try {
			if (file != null) {
				String readL = file.readLine();
				while (readL != null) {
					readL = readL.trim();
					if (!readL.equals("") && !readL.startsWith("#"))
						ret.add(readL);
					readL = file.readLine();
				}
			}
		}
		catch (IOException e) {

		}
		return ret;
	}

//Fix 04496 PM Fine


//Fix 04397 PM Inizio
	public void run(String[] args) throws Exception {
		printAmbienteInstallazioneFix();
		iNuovaGestioneFix = ! ( (new File(args[1] + File.separator + "db2")).exists());

		//Fix 04668 PM Inizio
		iFixRoot = extractFixRoot(args);
		//Fix 04668 PM Fine

		super.run(args);
	}

//Fix 04397 PM Fine

//Fix 04668 PM Inizio
	public void controlServerDirectoryForCopyFiles() throws IOException {
		super.controlServerDirectoryForCopyFiles();
		impostaParametriComeProperties();
		inizializzaEsecutoreOperazionePostFix(); // Fix 6997 PM
	}

//Fix 04668 PM Fine

	protected void printAmbienteInstallazioneFix() {
//		System.out.println("Installazione fix in ambiente DB2"); Fix 18070
		System.out.println("Installazione fix in ambiente " + ConnectionManager.getCurrentDatabase().getDBManagerId());// Fix 18070
	}

//Fix 04668 PM Inizio
	protected void impostaParametriComeProperties() {
		System.setProperty("THIP_FIX_ROOT", iFixRoot == null ? "" : iFixRoot);
		System.setProperty("THIP_LIB_ROOT", getLibRoot() == null ? "" : getLibRoot());
		System.setProperty("THIP_WEB_ROOT", getWebRoot() == null ? "" : getWebRoot());
		System.setProperty("THIP_RPT_ROOT", getRptRoot() == null ? "" : getRptRoot());
		System.setProperty("THIP_PRIM_ROOT", getServerRoot() == null ? "" : getServerRoot());
		System.setProperty("THIP_PRIM_CLIENT_ROOT", getClientRoot() == null ? "" : getClientRoot());
		System.setProperty("THIP_PRIM_REPORT_ROOT", getReportsRoot() == null ? "" : getReportsRoot());
		//Mod. 14086 inizio
		System.setProperty("THIP_SEC_ROOT", getServerRoot2() == null ? "" : getServerRoot2());
		System.setProperty("THIP_SEC_CLIENT_ROOT", getClientRoot2() == null ? "" : getClientRoot2());
		System.setProperty("THIP_SEC_REPORT_ROOT", getReportsRoot2() == null ? "" : getReportsRoot2());
		//fine mod. 14086
		/*      System.out.println("THIP_FIX_ROOT " + System.getProperty("THIP_FIX_ROOT"));
					System.out.println("THIP_LIB_ROOT " + System.getProperty("THIP_LIB_ROOT"));
					System.out.println("THIP_WEB_ROOT " + System.getProperty("THIP_WEB_ROOT"));
					System.out.println("THIP_RPT_ROOT " + System.getProperty("THIP_RPT_ROOT"));
					System.out.println("THIP_PRIM_ROOT " + System.getProperty("THIP_PRIM_ROOT"));
					System.out.println("THIP_PRIM_CLIENT_ROOT " + System.getProperty("THIP_PRIM_CLIENT_ROOT"));
					System.out.println("THIP_PRIM_REPORT_ROOT " + System.getProperty("THIP_PRIM_REPORT_ROOT"));
		 */
	}

//Fix 05184 PM Inizio
	// 07939 - DF
	protected boolean makeViews() throws Exception {
		boolean res = super.makeViews();
		//Mod. 13728//if (onlyCopyFiles)
		if (onlyCopyFiles || !doneSthg) //Mod. 13728
			return res;

		if ( ( (Security.getDatabase() instanceof DB2Database &&
						! (Security.getDatabase() instanceof DB2AS400Database) &&
						! (Security.getDatabase() instanceof DB2ToolboxDatabase)) ||
					Security.getDatabase() instanceof SQLServerDatabase ||
					Security.getDatabase() instanceof OracleDatabase)) {
			System.out.println("");
			System.out.println(ResourceLoader.getString(SETUP_RES, "DoRegenViews"));
			thipDropViews();
			thipRegenerationViews();
			if (viewsDefinition != null && viewsDefinition.size() > 0) {
				rollback();
				thipPrintViewErrors();
				return false;
			}
			else {
				commit();
				System.out.println(ResourceLoader.getString(SETUP_RES, "RegenerationViewOK"));
				System.out.println("");
				output.println("");
				output.println(ResourceLoader.getString(SETUP_RES, "RegenerationViewOK"));
				output.println("");
			}

		}
		return res;
	}

	protected List loadViewSchemas(BufferedReader file) {
		if (file != null)
			return super.loadViewSchemas(file);
		List res = new ArrayList();
		res.add(SCHEMA_THIP);
		res.add(SCHEMA_THIP_PERS);
		return res;
	}

	protected List viewsDefinition = new ArrayList();
	protected List viewsList;

	protected List loadViews(BufferedReader file) {
		List res = super.loadViews(file);
		viewsList = getVisteDaRigenerare();
		for (int i = (viewsList.size() - 1); i >= 0; i--) {
			String fullName = (String) viewsList.get(i);
			try {
				DictView tmp = DictView.elementWithKey(extractName(fullName), DictView.NO_LOCK);
				if (tmp != null) {
					res.add(tmp);
					viewsList.remove(i);
				}
			}
			catch (SQLException e) {
				e.printStackTrace(Trace.excStream);
			}
		}
		return res;
	}

	protected String extractSchema(String fullName) {
		int pos = fullName.indexOf('.');
		if (pos > 0)
			return fullName.substring(0, pos);
		return "";
	}

	protected String extractName(String fullName) {
		int pos = fullName.indexOf('.');
		if (pos > 0)
			return fullName.substring(pos + 1);
		return "";
	}

	//07939 - Fine

	protected BufferedReader getFileVisteDaRigenerare() {
		BufferedReader ret = null;
		try {
			//Fix 05666 PM Inizio
			//File file = new File(getWebRootFile(), VISTE_DA_RIGENERARE_FILE);
			File file = new File(getFixRootFile(), VISTE_DA_RIGENERARE_FILE);
			//Fix 05666 PM Fine

			//System.out.println("file.getAbsolutePath() " + file.getAbsolutePath());
			//System.out.println("file.exists() " + file.exists());
			if (file.exists())
				ret = new BufferedReader(new FileReader(file));
			else
				ret = getFile(VISTE_DA_RIGENERARE_FILE);
		}
		catch (Exception e) {}
		return ret;
	}

	protected BufferedReader getFileVistePersonalizzateDaRigenerare() {
		BufferedReader ret = null;
		try {
			//Fix 05666 PM Inizio
			//File file = new File(getWebRootFile(),  VISTE_PERSALIZZATE_DA_RIGEN_FILE);
			File file = new File(getFixRootFile(), VISTE_PERSALIZZATE_DA_RIGEN_FILE);
			//System.out.println("file.getAbsolutePath() " + file.getAbsolutePath());
			//System.out.println("file.exists() " + file.exists());
			//Fix 05666 PM Fine
			if (file.exists())
				ret = new BufferedReader(new FileReader(file));
			else
				ret = getFile(VISTE_PERSALIZZATE_DA_RIGEN_FILE);
		}
		catch (Exception e) {}
		return ret;
	}

//Fix 05666 PM Inizio
	/*protected File getWebRootFile()
		 {
			return new File(getWebRoot());
		 }*/

	protected File getFixRootFile() {
		return new File(getFixRoot());
	}

//Fix 05666 PM Fine
	//Fix 05184 PM Fine

	protected void controllaPresenzaFix5242() {
		try {
			String selectFix5272Str = "SELECT * FROM " + SystemParam.getFrameworkSchema() + "SOFTWARE_FIX WHERE SOFTWARE_FIX = 5272";
			CachedStatement selectFix5272Stmt = new CachedStatement(selectFix5272Str);
			ResultSet rs = selectFix5272Stmt.executeQuery();
			iFix5272Installata = rs.next();
			rs.close();
			selectFix5272Stmt.free();
		}
		catch (SQLException e) {

		}
	}

//Fix 05273 PM Inizio
	protected void resetAuthorizableFlag() throws Exception {
		if (iFix5272Installata)
			super.resetAuthorizableFlag();
	}

//Fix 05273 PM Fine


//Fix 05666 PM Inizio
	protected boolean endApplyFix() throws Exception {
		boolean ret = super.endApplyFix();
		if(ret) {
			//35629
			ivSelectTipoInstallazioneFix.getStatement().setString(1, fixNrStr);
			ResultSet rs = ivSelectTipoInstallazioneFix.executeQuery();
			String tipoInstallazione = null;
			if(rs.next()) {
				tipoInstallazione = rs.getString(1);
			}
			rs.close();
			
			if(tipoInstallazione != null) {
				String nuovotipoInstallazione = null;
				if(tipoInstallazione.equals("2")) {
					nuovotipoInstallazione = "4";
				}
				if(tipoInstallazione.equals("3")) {
					nuovotipoInstallazione = "5";
				}
				if(tipoInstallazione.equals("1")) {
					nuovotipoInstallazione = "6";
				}
				ivUpdateTipoInstallazioneFix.getStatement().setString(1, nuovotipoInstallazione);
				ivUpdateTipoInstallazioneFix.getStatement().setString(2, tipoInstallazione);
				ivUpdateTipoInstallazioneFix.getStatement().setString(3, fixNrStr);
				
				try{
					ivUpdateTipoInstallazioneFix.executeUpdate();
				}catch(Exception e) {
					e.printStackTrace();
				}
				
				System.out.println("NuovoTipo " + nuovotipoInstallazione + " vecchioTipo " + tipoInstallazione);
			}
			
			System.out.println("E' stata applicata la fix " + fixNrStr);
			output.println("E' stata applicata la fix " + fixNrStr);
		}
		
		return ret;
	}

//Fix 05666 PM Fine

// Fix 6997 PM Inizio
	protected void inizializzaEsecutoreOperazionePostFix() {
		ivOperazioniPostFix = (EsecutoreOperazioniPostFix) Factory.createObject("it.thera.thip.base.util.EsecutoreOperazioniPostFix");
	}

	protected void endRun() {
		try {
			// 11107 DM inizio
			super.endRun();
			// 11107 DM fine
			if (ivOperazioniPostFix != null)
				ivOperazioniPostFix.run();
						
			//Fix 8654 PM Inizio
			//Mod. 14086 inizio
			//Eseguo la copia solo se nella destinazione ci sono già i .jar
			if (findJar()) {
				AggiornaJarPrimrose ajp = new AggiornaJarPrimrose();
				ajp.run(getWebRoot(), getServerRoot());
			}
			//fine mod. 14086
			//Fix 8654 PM Fine
			
		}
		catch (Exception e) {
			System.out.println("Eccezione nel metodo endRun(). Le fix in ogni caso sono state apllicate ");
			e.printStackTrace();
		}

	}

	// Fix 6997 PM Fine

	//Mod. 14086 inizio
	public boolean findJar() {
		boolean res = false;
		//Costruisco il path completo del file da cercare
		String completeBaseJarName = getWebRoot() + File.separator +
																 NAME_APP_WEBINF + File.separator +
																 NAME_APP_WEBLIB + File.separator +
																 BASE_JAR_NAME;

		File file = new File(completeBaseJarName);
		if (file.exists())
			res = true;
		return res;
	}
	//fine mod. 14086

	//Mod. 15026 inizio
	/**
	 * Copia dei file RPT: dalla root della fix + /print (costante NAME_FIX_RPT) ==> /rpt (parametro passato al lancio)
	 * copiando anche eventuali sottocartelle.
	 * @ String fRoot root della fix
	 * @ boolean rpt_present: true se nella fix esiste la directory per i file .RPT per Crystal Reports ver. 8.5
	 * @ boolean rptCR2008_present: true se esiste la directory  per i file .RPT per Crystal Reports ver. 2008
	 *    in Thip questo parametro viene ignorato, quando si copiano i file .rpt per CR85, vengono copiati anche quelli x CR2008
	 * @return boolean
	 */
	public boolean copyRPT(String fRoot, boolean rpt_present, boolean rptCR2008_present) {
		try {
			// Copio i report dalla directory opportuna (la copia comprende eventuali sottocartelle)
			if (copyRpt && rpt_present) {
						output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_RPT, getRptRoot()}));
						resetBackupDir(getRptRoot(), 4);
						FileUtil.copyTree(fRoot + NAME_FIX_RPT, getRptRoot(), "*.*");
			}
		}
		catch (Exception e) {
			output.println(ResourceLoader.getString(SETUP_RES, "ErrorString"));
			output.println(e.getMessage());
			return false;
		}
		return true;
	}
	//fine mod. 15026
	
	
	
	//Fix 28999 PM				
	protected boolean updateAppl(String read) throws Exception
	{
		boolean ret = super.updateAppl(read);
		if (ret)
		{
			try
			{
				AggiornaPantheraLevel.registraFixAggPthLevel();
				AggiornaPantheraLevel.svuotaElencoFixAggPthLevel();				
				commit();
			}
			catch (SQLException e)
			{
				e.printStackTrace();
			}
		}
		return ret;
	}
	//Fix 28999 PM				
	
	//Fix 30787 PM>
	protected boolean isCommitEachFixFlagPresent(String[] args)
    {
        //I flag onlyCopyFile e CommitEachFix insieme sono incompatibili.
        //Questo è quello che fa la classe FixFileProcessor. Il perchè è poco chiaro,
        //in ogni caso per non creare problemi quando c'è onlyCopyFile non imposto il flag
        //CommitEachFix.
        if (isOnlyCopyFilesFlagPresent(args))
            return super.isCommitEachFixFlagPresent(args);
        else
            return true;
    }
	//Fix 30787 PM<
}
