package it.thera.thip.base.util;

import it.thera.thip.base.pthupd.Fix;
import it.thera.thip.crm.news.News;
import it.thera.thip.ws.WrapperJSON;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONArray;

import com.thera.thermfw.base.IniFile;
import com.thera.thermfw.base.SystemParam;
import com.thera.thermfw.persist.CachedStatement;
import com.thera.thermfw.persist.ConnectionDescriptor;
import com.thera.thermfw.persist.ConnectionManager;
import com.thera.thermfw.persist.DB2AS400Database;
import com.thera.thermfw.persist.DB2NetDatabase;
import com.thera.thermfw.persist.Database;
import com.thera.thermfw.persist.PersistentObject;
import com.thera.thermfw.plexweb.Utils;
import com.thera.thermfw.security.Security;
import com.thera.thermfw.util.file.FileUtil;
import com.thera.thermfw.util.file.FileUtilException;


/*
 * Revisions:
 * Number     Date          Owner      Description
 * 33209      28/10/2021    FG         Adattamento per rilevazione errori e warning tramite Installatore Fix
 */
public class ApplicatoreFix 
{
	public static final String URL_APPLICATORE_GRUPPO_FIX = "https://pantherasrl-my.sharepoint.com/:u:/g/personal/condivisione_pantherasrl_onmicrosoft_com/EWvhU1ylrcJPr4ZtBYW6HscB4lFtCTvFE_VpCvXrvEDXDQ?download=1";
	public static final String APPL_GRUPPO_FIX_ZIP_NAME = "ApplicatoreGruppoPacchettiFix.zip";

	String ivNomeDirRepositoryFix = null;
	File ivDirRepositoryFix = null;
	File ivDirectoryTemporanea = null;
	String ivDirFixPanthera = null; 
	File ivDirFixOrder = null;
	File ivDirLog = null;
	List ivRigheFixOrderFix = new ArrayList();
	Map ivInsiemeZipFixDaInstallare = new HashMap();
	Map ivInsiemeZipFixNonInstallabili = new HashMap();
	//    Set<String> ivInsiemeFixDaInstallare = new HashSet<String>();
	//    List<String> ivInsiemeFixDaInstallareOrdinate = new ArrayList<String>();
	//    Set<String> ivInsiemePrequisiti = new HashSet<String>();
	Set<String> ivSetFixClassPath = new HashSet<String>();
	//Set<String> ivDescrizioniFix = new HashSet<String>();
	List<String> ivDescrizioniFix = new ArrayList<String>();
	//List<String> ivRigheFixOrderT = new ArrayList<String>();
	//List<String> ivNewFixOrderLines = new ArrayList<String>();
	//    String ivVersione = "";
	//    String ivRelease = "";
	//    String ivModifica = "";
	//    String ivVRM ="";

	List<String> ivElencoFixDaOrdinare = new ArrayList();
	List<String> ivElencoFixOrdinate = new ArrayList();
	//    Map ivFixUtilizzatePerOrdinamento = new HashMap();
	List<File> ivElencoZipCumulativi = new ArrayList<File>();
	List<File> ivElencoZipFastupdate = new ArrayList<File>();
	List<File> ivElencoZipFixStandard = new ArrayList<File>();
	List<File> ivElencoZipFixStandardOrdinate = new ArrayList<File>();
	List<File> ivElencoZipFixPersonalizzazioni = new ArrayList<File>();
	List<File> ivElencoZipWebApp = new ArrayList<File>();
	List<File> ivElencoZipCheVerranoInstallati = new ArrayList<File>();
	Set<String> ivInsiemeFixContenuteNegliZip = new HashSet<String>();
	CachedStatement iUpdateTipoInstallazione = new CachedStatement("UPDATE " + SystemParam.getSchema("THIP") +
			"FIXES SET TIPO_INSTALLAZIONE = ? WHERE FIX = ? " + "AND TIPO_INSTALLAZIONE = " + Fix.TI_NON_INSTALLATA);

	private final String WARNING_PREFIX = "#WARNING#"; //FG - 33209
	private final String ERROR_PREFIX = "#ERRORE#"; //FG - 33209

	List<String> ivWarning = new ArrayList<String>();
	CachedStatement ivCheckFixInstallata = new CachedStatement("SELECT * FROM " + SystemParam.getSchema("THERA") + "SOFTWARE_FIX WHERE SOFTWARE_FIX = ?");
	
	private boolean ivGestisciFlagForzaFixAnticipate = false;
	private boolean ivForzaFixAnticipate = false;

	public void forzaFixAnticipate() throws Exception{
		
		StringBuilder builder = new StringBuilder();
		boolean primaVolta = true;
		
		for(String fix: ivInsiemeFixContenuteNegliZip) {
			if(!primaVolta) {
				builder.append(",");
			}else {
				primaVolta = false;
			}
			builder.append(fix);	
		}
	
		String qrySelect = "SELECT * FROM " + SystemParam.getSchema("THIP") + "FIXES WHERE FORZA_FIX_ANTICIP = 'Y' AND FIX IN (" 
							+ builder.toString() + ")";
		CachedStatement select = new CachedStatement(qrySelect);
		ResultSet rs = select.executeQuery();
		
		if(rs.next()) {
			ivForzaFixAnticipate = true;
		}
		rs.close();
		select.free();
				
		if(ivForzaFixAnticipate) {		
			String qry = "UPDATE " + SystemParam.getSchema("THERA") + "ADVANCE_FIXES SET STATUS = 'S' WHERE STATUS = 'V'";
			CachedStatement stmt = new CachedStatement(qry);
			System.out.println("Sono state sospese le fix anticipate.");
			stmt.executeUpdate();
			ConnectionManager.commit();
		}
	}


	public boolean run(String[] args) throws Exception
	{
		boolean ret = true;
		ivNomeDirRepositoryFix = args[1];
		ivDirFixPanthera = args[2];


		if(args.length == 4 && args[3].equals("Y")) {
			ivGestisciFlagForzaFixAnticipate = true;
		}
		
		//35629 ini
		String directoryLog = new File(new File(ivNomeDirRepositoryFix).getParentFile() + "/bin/DownloadInstallaFixLog").getName();
		creaDirectoryTemporanee(directoryLog);
		ivDirRepositoryFix = new File(ivNomeDirRepositoryFix);
		//35629 fine
		
		List errori = calcolaFixDaInstallare();
		if (!errori.isEmpty())
		{
			ret = false;
			writeErrorFile(new File(ivDirLog, "Errori.txt"), errori); //FG - 33209
			if (!ivWarning.isEmpty())
				writeWarningFile(new File(ivDirLog, "Warning.txt"), ivWarning); //FG 
		}
		else        	
		{    	
			if (!ivInsiemeZipFixDaInstallare.keySet().isEmpty())
			{	
				Map m = recuperaFixOrderDaPanthera();
				errori = (List<String>)m.get("errors");
				if (errori.isEmpty())
				{
					ivElencoFixOrdinate = (List<String>)m.get("elencoFixOrdinate");
					//Installo solo se le fix ordinate sono le stesse che devo installare. Potrebbe essere che nel fixorder aziendale non sia ancora presente una fix pubblicata
					//Es. è stata pubblicata una fix velocemente e non è ancora girato il batch che aggiorna il fixorder sul Panthera aziendale.
					//if (ivElencoFixOrdinate.size() == ivInsiemeFixContenuteNegliZip.size() && ivElencoFixOrdinate.containsAll(ivInsiemeFixContenuteNegliZip))
					if (ivElencoFixOrdinate.size() == ivElencoFixDaOrdinare.size() && ivElencoFixOrdinate.containsAll(ivElencoFixDaOrdinare))
					{
						errori = controllaPrerequisitiFixDaInstallare();
						if (errori.isEmpty())
						{
							//System.out.println("ivElencoZipCheVerranoInstallati.isEmpty() -> " + ivElencoZipCheVerranoInstallati.isEmpty());
							if (!ivElencoZipCheVerranoInstallati.isEmpty())
							{
								if(ivGestisciFlagForzaFixAnticipate)forzaFixAnticipate();	
								creaFixOrder("FixOrder.txt");
								writeFile(new File(ivDirFixPanthera, "FixDescriptionList.txt"), ivDescrizioniFix);
								setFixClassPath();
								if (!ivWarning.isEmpty())
									//writeFile(new File(ivDirLog, "Warning.txt"), ivWarning);
									writeWarningFile(new File(ivDirLog, "Warning.txt"), ivWarning); //FG - 33209
							}
							else
							{
								ivWarning.add("Non è stata installata nessuna fix.");
								if (!ivWarning.isEmpty())
									writeWarningFile(new File(ivDirLog, "Warning.txt"), ivWarning); //FG 
							}

						}
						else
						{
							ret = false;
							writeErrorFile(new File(ivDirLog, "Errori.txt"), errori); //FG - 33209
							if (!ivWarning.isEmpty())
								writeWarningFile(new File(ivDirLog, "Warning.txt"), ivWarning); //FG 
						}
					}
					else
					{
						//Il batch che popola il fixorder non è ancora girato e la fix non è presente nella tabella del fixorder del Panthera aziendale 
						ret = false;  
						ivElencoFixDaOrdinare.removeAll(ivElencoFixOrdinate);
						boolean primo = true;;
						String s = "";
						for (String fix : ivElencoFixDaOrdinare)
						{
							if (!primo)
								s = s + ", ";
							s = s + fix;
							primo = false;
						}
						errori.add("Le seguenti fix non sono presenti nel FixOrder di Panthera: " + s);
						errori.add("Le fix non vengono installate.");
						writeErrorFile(new File(ivDirLog, "Errori.txt"), errori); //FG - 33209
						if (!ivWarning.isEmpty())
							writeWarningFile(new File(ivDirLog, "Warning.txt"), ivWarning); //FG 
					}
				}
				else
				{
					ret = false;
					writeErrorFile(new File(ivDirLog, "Errori.txt"), errori); //FG - 33209
					if (!ivWarning.isEmpty())
						writeWarningFile(new File(ivDirLog, "Warning.txt"), ivWarning); //FG 
				}


			}
			else
			{
				ivWarning.add("Non è stata installata nessuna fix.");
				if (!ivWarning.isEmpty())	            	
					writeWarningFile(new File(ivDirLog, "Warning.txt"), ivWarning); //FG 
			}
		}

		return ret;
	}
	protected boolean isCumulativo(File f)
	{
		boolean ret = false;
		String name = f.getName();
		//V4R7M0-NT.zip
		if ((name.length() == 13 || name.length() == 14)&& name.endsWith("-NT.zip") && name.charAt(0) == 'V' && name.charAt(2) == 'R' && name.charAt(4) == 'M')
		{
			int versione = 0;
			int release = 0;
			try
			{
				versione = Integer.parseInt(String.valueOf(name.charAt(1)));
			}
			catch(Exception e)
			{
			}

			try			
			{
				release = Integer.parseInt(String.valueOf(name.charAt(3)));
			}
			catch(Exception e)
			{
			}

			if (versione >= 4 && release >= 7)
				ret = true;
			else
				ret = false;
		}
		return ret;
	}

	protected boolean isFastupdate(File f)
	{
		boolean ret = false;
		String name = f.getName();
		//PacchettoFastUpdate-NT-V4R7M7.zip
		if ((name.length() == 33 || name.length() == 34) && name.startsWith("PacchettoFastUpdate-NT-") && name.endsWith(".zip") && name.charAt(23) == 'V' && name.charAt(25) == 'R' && name.charAt(27) == 'M')
		{
			int versione = 0;
			int release = 0;
			try
			{
				versione = Integer.parseInt(String.valueOf(name.charAt(24)));
			}
			catch(Exception e)
			{
			}

			try			
			{
				release = Integer.parseInt(String.valueOf(name.charAt(26)));
			}
			catch(Exception e)
			{
			}

			if (versione >= 4 && release >= 7)
				ret = true;
			else
				ret = false;
		}
		return ret;
	}

	protected boolean isFixStandard(File f)
	{
		boolean ret = false;
		String name = f.getName();
		//V4R7M0-FixXXXXX-NT.zip
		//Solo fix dopo il cumulativo 470

		if (name.length() == 22 && name.endsWith("-NT.zip") && name.charAt(0) == 'V' && name.charAt(2) == 'R' && name.charAt(4) == 'M' && name.indexOf("Fix") != -1)
		{
			int versione = 0;
			int release = 0;
			try
			{
				versione = Integer.parseInt(String.valueOf(name.charAt(1)));
			}
			catch(Exception e)
			{
			}

			try			
			{
				release = Integer.parseInt(String.valueOf(name.charAt(3)));
			}
			catch(Exception e)
			{
			}

			if (versione >= 4 && release >= 7)
				ret = true;
			else
				ret = false;
		}
		return ret;
	}

	protected boolean isFixAggiornaWebApp(File f)
	{
		boolean ret = false;
		try
		{
			String name = f.getName();
			if (name.equals("AggiornamentoMonitorPantheraDB2_SQL.zip"))
				ret = true;
		}
		catch(Exception e)
		{

		}
		return ret;
	}

	protected boolean isFixPersonalizzazioni(File f)
	{
		boolean ret = false;
		try
		{
			String name = f.getName();

			if (isFixAggiornaWebApp(f))
				return  false;

			//Escludo le fix linux
			//V4R7M0-FixXXXXX-LX.zip
			if (name.length() == 22 && name.endsWith("-LX.zip") && name.charAt(0) == 'V' && name.charAt(2) == 'R' && name.charAt(4) == 'M' && name.indexOf("Fix") != -1)
				return false;

			//PacchettoFastUpdate-LX-V4R7M7.zip
			if (name.startsWith("PacchettoFastUpdate-LX-") && name.endsWith(".zip") && name.charAt(23) == 'V' && name.charAt(25) == 'R' && name.charAt(27) == 'M')
				ret = false;

			//Escludo le fix AS
			//V4R7M0-FixXXXXX-AS.zip
			if (name.length() == 22 && name.endsWith("-AS.zip") && name.charAt(0) == 'V' && name.charAt(2) == 'R' && name.charAt(4) == 'M' && name.indexOf("Fix") != -1)
				return false;

			//PacchettoFastUpdate-AS-V4R7M7.zip
			if (name.startsWith("PacchettoFastUpdate-AS-") && name.endsWith(".zip") && name.charAt(23) == 'V' && name.charAt(25) == 'R' && name.charAt(27) == 'M')
				ret = false;

			ZipFile zf = new ZipFile(f.getAbsolutePath());
			Enumeration e = zf.entries();
			ZipEntry entry;
			while(e.hasMoreElements())
			{
				entry = (ZipEntry)e.nextElement();
				String entryName = entry.getName();
				if (entryName.endsWith("fixes.zip"))
				{
					ret = true;
					break;
				}
			}
		}
		catch(Exception e)
		{

		}
		return ret;
	}


	protected List controllaPrerequisitiFixDaInstallare() throws Exception
	{
		List errori = new ArrayList();
		Iterator it = ordinaElencoCumulativi(ivElencoZipCumulativi).iterator();
		while (it.hasNext())
		{
			File fn = (File)it.next();
			controllaPrerequisitiFix(fn);
		}
		it = ordinaElencoFastupdate(ivElencoZipFastupdate).iterator();
		while (it.hasNext())
		{
			File fn = (File)it.next();
			controllaPrerequisitiFix(fn);
		}

		//ordinaElencoZipFixStandard();
		it = ivElencoZipFixStandard.iterator();
		while (it.hasNext())
		{
			File fn = (File)it.next();
			controllaPrerequisitiFix(fn);
		}

		it = ivElencoZipFixPersonalizzazioni.iterator();
		while (it.hasNext())
		{
			File fn = (File)it.next();
			controllaPrerequisitiFix(fn);
		}

		//Controllo che le fix non installabili non siano prerequisito di altre fix che risultavano installabili

		//Set elencoZipNonInstallabili = ivInsiemeZipFixNonInstallabili.keySet();
		//ivElencoZipCheVerranoInstallati
		if (!ivInsiemeZipFixNonInstallabili.keySet().isEmpty())
		{

			List<File> fixDaRimuovere = new ArrayList();
			do
			{
				for (File fn : (Set<File>)ivInsiemeZipFixNonInstallabili.keySet())
				{
					List l = (List)ivInsiemeZipFixDaInstallare.get(fn);
					Set<String> insiemeFixContenuteNelloZip = (Set)l.get(0);
					ivInsiemeFixContenuteNegliZip.removeAll(insiemeFixContenuteNelloZip);			
				}
				fixDaRimuovere = new ArrayList<File>();
				Database db = ConnectionManager.getCurrentDatabase();
				for (File fn : (List<File>)ivElencoZipCheVerranoInstallati)
				{
					List l = (List)ivInsiemeZipFixDaInstallare.get(fn);
					Set prerequisiti = (Set)l.get(1);
					Iterator it1 = prerequisiti.iterator();
					Set prequisitiNonInstallati = new HashSet();
					while (it1.hasNext())
					{
						String fix = (String)it1.next();
						if (!ivInsiemeFixContenuteNegliZip.contains(fix))
						{
							db.setString(ivCheckFixInstallata.getStatement(), 1, fix);
							ResultSet rs = ivCheckFixInstallata.executeQuery();
							boolean installata = rs.next();
							rs.close();
							if (!installata)
								prequisitiNonInstallati.add(fix);
						}					
					}
					if (!prequisitiNonInstallati.isEmpty())
					{
						ivInsiemeZipFixNonInstallabili.put(fn, prequisitiNonInstallati);
						fixDaRimuovere.add(fn);
					}

				}
				for (File fn : fixDaRimuovere)
					ivElencoZipCheVerranoInstallati.remove(fn);
			}
			while(!fixDaRimuovere.isEmpty());

			for (File fn : (Set<File>)ivInsiemeZipFixNonInstallabili.keySet())
			{
				boolean primo = true;
				String s = "";
				for (String fix : (Set<String>)ivInsiemeZipFixNonInstallabili.get(fn))
				{
					if (!primo)
						s = s + ", ";
					s = s + fix;
					primo = false;
				}
				//ivWarning.add("La fix " + fn.getName() + " non può esssere installata per mancanza dei seguenti prequisiti : " + s);		
				errori.add("La fix " + fn.getName() + " non può esssere installata per mancanza dei seguenti prequisiti : " + s);		
			}
		}

		return errori;
	}

	protected List controllaPrerequisitiFix(File fn) throws Exception
	{
		List errori = new ArrayList();
		Database db = ConnectionManager.getCurrentDatabase();
		List l = (List)ivInsiemeZipFixDaInstallare.get(fn);
		Set insiemeFixContenuteNelloZip = (Set)l.get(0);
		Set prerequisiti = (Set)l.get(1);
		Iterator it1 = prerequisiti.iterator();
		Set prequisitiNonInstallati = new HashSet();
		while (it1.hasNext())
		{
			String fix = (String)it1.next();
			if (!ivInsiemeFixContenuteNegliZip.contains(fix))
			{
				db.setString(ivCheckFixInstallata.getStatement(), 1, fix);
				ResultSet rs = ivCheckFixInstallata.executeQuery();
				boolean installata = rs.next();
				rs.close();
				if (!installata)
					prequisitiNonInstallati.add(fix);
			}	
		}
		if (!prequisitiNonInstallati.isEmpty())
		{
			/*boolean primo = true;
			String s = "";
			Iterator it = prequisitiNonInstallati.iterator();
			while (it.hasNext())
			{
				if (!primo)
					s = s + ", ";
				s = s + (String)it.next();
				primo = false;
			}
			ivWarning.add("La fix " + fn.getName() + " non può esssere installata per mancanza dei seguenti prequisiti : " + s);*/
			ivInsiemeZipFixNonInstallabili.put(fn, prequisitiNonInstallati);
		}
		else
		{
			//ivInsiemeFixContenuteNegliZip.addAll(insiemeFixContenuteNelloZip);
			ivElencoZipCheVerranoInstallati.add(fn);
		}		
		return errori;
	}
	
	protected List calcolaFixDaInstallare() throws Exception
	{
		List errori = new ArrayList();
		File[] list= ivDirRepositoryFix.listFiles();
		for (int i = 0; i < list.length; i++)
		{
			File f = list[i];
			String fn = f.getName();
			System.out.println(f.getName());
			System.out.println(f.getAbsolutePath());
			if (f.isFile() && f.getName().endsWith(".zip") && (isCumulativo(f) || isFastupdate(f) || isFixStandard(f) || (isFixAggiornaWebApp(f) && !isAmbienteDiProva()) || isFixPersonalizzazioni(f)))
			{	
				//				System.out.println(f.getAbsolutePath());
				//				System.out.println(ivDirectoryFixWeb + "/" +  f.getName());
				String nDir = Utils.replace(fn, ".zip", ""); 
				unzip(f.getAbsolutePath(), ivDirectoryTemporanea.getAbsolutePath());
				unzip(ivDirectoryTemporanea.getAbsolutePath() + "/" + nDir + "/fixes.zip", ivDirFixPanthera);
				File fixOrder = new File(ivDirFixPanthera + "/FixOrder.txt");

				List<String> righeFixOrder = new ArrayList<String>();
				Set<String> insiemeFixContenuteNelloZip = new HashSet<String>();
				readFile(fixOrder, righeFixOrder);
				Iterator<String> it = righeFixOrder.iterator();
				String ultimaFix = "";
				while (it.hasNext())
				{
					String s = it.next();
					s = s.trim();
					if (!s.equals("") && !s.startsWith("Application") && !s.startsWith("Description") && !s.startsWith("UpdateVRM") && !s.startsWith("#"))
					{
						String numFix = s.substring(0, s.indexOf(":"));
						insiemeFixContenuteNelloZip.add(numFix);
						ultimaFix = numFix;
					}
				}
				fixOrder.delete();

				File fullFixOrder = new File(ivDirFixPanthera + "/FullFixOrder.txt");
				List<String> righeFullFixOrder = new ArrayList<String>();
				Set<String> prerequisiti = new HashSet<String>();
				readFile(fullFixOrder, righeFullFixOrder);
				it = righeFullFixOrder.iterator();
				while (it.hasNext())
				{
					String s = it.next();
					s = s.trim();
					if (!s.equals("") && !s.startsWith("Application") && !s.startsWith("Description") && !s.startsWith("UpdateVRM") && !s.startsWith("#"))
					{
						String numFix = s.substring(0, s.indexOf(":"));
						prerequisiti.add(numFix);
					}
				}
				fullFixOrder.delete();
				prerequisiti.removeAll(insiemeFixContenuteNelloZip);

				File setFixClasspath = new File(ivDirectoryTemporanea.getAbsolutePath() + "/" + nDir + "/setFixClassPath.txt");
				readFile(setFixClasspath, ivSetFixClassPath);
				File fixDescriptionList = new File(ivDirFixPanthera + "/FixDescriptionList.txt");
				readFile(fixDescriptionList, ivDescrizioniFix);
				fixDescriptionList.delete();
				List l = new ArrayList();
				l.add(insiemeFixContenuteNelloZip);
				l.add(prerequisiti);
				l.add(righeFixOrder);
				ivInsiemeFixContenuteNegliZip.addAll(insiemeFixContenuteNelloZip);
				ivInsiemeZipFixDaInstallare.put(f, l);
				
				if (isCumulativo(f)) {
					ivElencoZipCumulativi.add(f);
					aggiornaTipoInstallazioneFastupdate(insiemeFixContenuteNelloZip, ultimaFix);
				}else if (isFastupdate(f)) {
					ivElencoZipFastupdate.add(f);
					aggiornaTipoInstallazioneFastupdate(insiemeFixContenuteNelloZip, ultimaFix);
				}
				else if (isFixStandard(f))
				{
					ivElencoZipFixStandard.add(f);
					ivElencoFixDaOrdinare.addAll(insiemeFixContenuteNelloZip);
				}
				else if (isFixAggiornaWebApp(f))
					ivElencoZipWebApp.add(f);				
				else if (isFixPersonalizzazioni(f))
					ivElencoZipFixPersonalizzazioni.add(f);

			}
			else if (isFixAggiornaWebApp(f) && isAmbienteDiProva())
				ivWarning.add(" L'aggiornamento della webapp non sarà effettuato poiché siamo in ambiente didattico." );
			else if (f.isFile() && !f.getName().equals(APPL_GRUPPO_FIX_ZIP_NAME) && !f.getName().equals("InstallaFix.jar") && !f.getName().equals("InstallaFix.bat") && !f.getName().equals("FixWebPacker.bat"))
				ivWarning.add(" Il file " + f.getName() + " non è una fix e quindi non verrà installata." );

		}
		if (ivElencoZipCumulativi.size() > 1)
			errori.add("Nella directory " + ivDirRepositoryFix.getAbsolutePath() + " sono presenti più di un cumulativo. Ce ne può essere solo uno." );
		if (ivElencoZipFastupdate.size() > 1)
			errori.add("Nella directory " + ivDirRepositoryFix.getAbsolutePath() + " sono presenti più di un fastupdate. Ce ne può essere solo uno." );
		if (ivElencoZipFixPersonalizzazioni.size() > 1)
			errori.add("Nella directory " + ivDirRepositoryFix.getAbsolutePath() + " sono presenti più fix di riallineamento delle personalizzaziono. Ce ne può essere solo una." );

		if (ivInsiemeZipFixDaInstallare.keySet().isEmpty())
			ivWarning.add(" Nella directory " + ivDirRepositoryFix.getAbsolutePath() + " non sono presenti fix da installare." );
		return errori;

	}


	private void aggiornaTipoInstallazioneFastupdate(Set<String> insiemeFixContenuteNelloZip, String ultimaFix) throws SQLException {
		Fix fix = Fix.elementWithKey(ultimaFix, PersistentObject.NO_LOCK);
		for(String ogni_fix: insiemeFixContenuteNelloZip) {
			updateTipoInstallazione(ogni_fix, String.valueOf(fix.getTipoInstallazione()));
		}
	}

	/*    protected void creaFixOrder(String nomeFixOrder) throws Exception
    {
        Iterator<File> i = ivElencoZipCheVerranoInstallati.iterator();
        while (i.hasNext())
        {
        	File f = i.next();
        	List l = (List)ivInsiemeZipFixDaInstallare.get(f);
        	ivRigheFixOrderFix.addAll((List)l.get(2));
        	ivRigheFixOrderFix.add("");
        	ivRigheFixOrderFix.add("");
        }
        writeFile(new File(ivDirFixPanthera, nomeFixOrder), ivRigheFixOrderFix);
        writeFile(new File(ivDirFixOrder, nomeFixOrder), ivRigheFixOrderFix);
    }
	 */    
	
	private void updateTipoInstallazione(String numero_fix, String tipo_installazione) throws SQLException {
		iUpdateTipoInstallazione.getStatement().setString(1, tipo_installazione);
		iUpdateTipoInstallazione.getStatement().setString(2, numero_fix);
		iUpdateTipoInstallazione.executeUpdate();
		ConnectionManager.commit();
	}
	
	protected void creaFixOrder(String nomeFixOrder) throws Exception
	{

		if (!ivElencoZipCumulativi.isEmpty())
		{
			if (ivElencoZipCheVerranoInstallati.contains(ivElencoZipCumulativi.get(0)))
			{
				List l = (List)ivInsiemeZipFixDaInstallare.get(ivElencoZipCumulativi.get(0));
				ivRigheFixOrderFix.addAll((List)l.get(2));
				ivRigheFixOrderFix.add("");
				ivRigheFixOrderFix.add("");
			}
		}
		if (!ivElencoZipFastupdate.isEmpty())
		{
			if (ivElencoZipCheVerranoInstallati.contains(ivElencoZipFastupdate.get(0)))
			{
				List l = (List)ivInsiemeZipFixDaInstallare.get(ivElencoZipFastupdate.get(0));
				ivRigheFixOrderFix.addAll((List)l.get(2));
				ivRigheFixOrderFix.add("");
				ivRigheFixOrderFix.add("");
			}
		}

		List<String> righeFixOrderFix = new ArrayList<String>();
		List<String> righeFixOrderFixTmp = new ArrayList<String>();
		for (Object obj : ivElencoZipFixStandard)
		{
			if (ivElencoZipCheVerranoInstallati.contains(obj))
			{
				List l = (List)ivInsiemeZipFixDaInstallare.get(obj);
				List<String> fixOrderLine = (List)l.get(2);
				righeFixOrderFixTmp.addAll(fixOrderLine);
			}
		}
		String applicationString = "";
		String descriptionString = "";
		String updateVRMString = "";
		String rigaPrecedente = "";
		for (String fix : ivElencoFixOrdinate)
		{
			for (String s : righeFixOrderFixTmp)
			{
				s = s.trim();

				if (s.startsWith("Application"))
					applicationString = s;

				if (s.startsWith("Description"))
					descriptionString = s;

				if (s.startsWith("UpdateVRM"))
					updateVRMString = s;

				if (!s.equals("") && !s.startsWith("Application") && !s.startsWith("Description") && !s.startsWith("UpdateVRM") && !s.startsWith("#"))
				{
					String numFix = s.substring(0, s.indexOf(":"));
					if (numFix.equals(fix))
					{
						if (!rigaPrecedente.startsWith(numFix))
							righeFixOrderFix.add("");
						righeFixOrderFix.add(s);
						rigaPrecedente = s;
					}
				}
			}
		}
		righeFixOrderFix.add(0, "");
		righeFixOrderFix.add(0, descriptionString);
		righeFixOrderFix.add(0, applicationString);

		righeFixOrderFix.add("");
		righeFixOrderFix.add(updateVRMString);
		ivRigheFixOrderFix.addAll(righeFixOrderFix);	
		ivRigheFixOrderFix.add("");
		ivRigheFixOrderFix.add("");	


		if (!ivElencoZipFixPersonalizzazioni.isEmpty())
		{
			if (ivElencoZipCheVerranoInstallati.contains(ivElencoZipFixPersonalizzazioni.get(0)))
			{
				List l = (List)ivInsiemeZipFixDaInstallare.get(ivElencoZipFixPersonalizzazioni.get(0));
				ivRigheFixOrderFix.addAll((List)l.get(2));
				ivRigheFixOrderFix.add("");
				ivRigheFixOrderFix.add("");
			}
		}

		if (!ivElencoZipWebApp.isEmpty())
		{
			if (ivElencoZipCheVerranoInstallati.contains(ivElencoZipWebApp.get(0)))
			{
				List l = (List)ivInsiemeZipFixDaInstallare.get(ivElencoZipWebApp.get(0));
				ivRigheFixOrderFix.addAll((List)l.get(2));
				ivRigheFixOrderFix.add("");
				ivRigheFixOrderFix.add("");
			}
		}

		writeFile(new File(ivDirFixPanthera, nomeFixOrder), ivRigheFixOrderFix);
		writeFile(new File(ivDirFixOrder, nomeFixOrder), ivRigheFixOrderFix);
	}
	protected List ordinaElencoCumulativi(List cumulativi)
	{
		return cumulativi;
	}

	protected List ordinaElencoFastupdate(List fastupdate)
	{
		return fastupdate;
	}

	protected void ordinaElencoZipFixStandard()
	{
		//ivElencoFixStandardOrdinati
		//ivElencoFixStandard
		for (String fix : ivElencoFixOrdinate) 
		{

			for (File f : ivElencoZipFixStandard) 
			{
				List l = (List)ivInsiemeZipFixDaInstallare.get(f);
				List insiemeFixContenuteNelloZip = (List)l.get(0);
				if (insiemeFixContenuteNelloZip.contains(fix))
				{
					if (!ivElencoZipFixStandardOrdinate.contains(f))
						ivElencoZipFixStandardOrdinate.add(f);
				}

			}
		}  	
	}

	private void deleteFile(String s)
	{
		File file = new File(s);
		if(!file.delete())
		{
			if(file.isDirectory())
			{
				String as[] = file.list();
				for(int i = 0; i < as.length; i++)
					deleteFile(String.valueOf(String.valueOf((new StringBuffer(String.valueOf(String.valueOf(s)))).append(File.separator).append(as[i]))));

				if(file.delete())
					file = null;
			}
			if(file != null)
				throw new FileUtilException("Cannot delete:  ".concat(String.valueOf(String.valueOf(s))));
		}
	}
	
	protected void eliminaDirectoryFix()
	{
		File file = new File(ivDirFixPanthera);
		String as[] = file.list();
		for(int i = 0; i < as.length; i++)
		{
			if (!as[i].endsWith("VistePersonalizzateDaRigenerare.txt") && !as[i].endsWith("TDDML.dtd") && !as[i].endsWith("VisteDaRigenerare.txt")) // Fix 06674
				deleteFile(String.valueOf(String.valueOf((new StringBuffer(String.valueOf(String.valueOf(ivDirFixPanthera)))).append(File.separator).append(as[i]))));
		}
		// Fix 05223 PM Fine
		File f = new File(ivDirFixPanthera + File.separator + "lib");
		f.mkdir();
	}

	private Map recuperaFixOrderDaPanthera() 
	{
		Map valori = new HashMap();
		try 
		{
			/*Iterator i = ivElencoZipFixStandard.iterator();
			boolean primo = true;
			String elencoFix = "";
			while (i.hasNext())
			{
				File f = (File)i.next();
				//System.out.println("************************** f.getAbsolutePath() " + f.getAbsolutePath());
				List l = (List)ivInsiemeZipFixDaInstallare.get(f);
				Set<String> insiemeFixContenuteNelloZip = (Set)l.get(0);
				for (String fix : insiemeFixContenuteNelloZip) 
				{
					if (!primo)
						elencoFix = elencoFix + ", ";
					elencoFix = elencoFix +  fix;
					primo = false;
				}
			}*/
			String elencoFix = "";
			boolean primo = true;

			if(ivElencoFixDaOrdinare.isEmpty()) {
				valori.put("elencoFixOrdinate", new ArrayList<String>());
				valori.put("errors", new ArrayList());
				return valori;
			}
			//for (String fix : ivInsiemeFixContenuteNegliZip) 
			for (String fix : ivElencoFixDaOrdinare) 
			{
				if (!primo)
					elencoFix = elencoFix + ", ";
				elencoFix = elencoFix +  fix;
				primo = false;
			}
			//			String query = "SELECT FIX FROM " + SystemParam.getSchema("THIPPERS") + "FIX_ORDER WHERE FIX in (" + elencoFix + ") ORDER BY ID ASC";

			//			String url = "http://93.146.247.188:10101/panth01/it/thera/thip/ws/GenericQuery.jsp";
			//String url = "http://93.146.247.188:10101/panth01/ws?id=YFXOR";

			String remoteUrl = News.getUrlPantheraNews();
			if (remoteUrl == null || remoteUrl.equals(""))
				remoteUrl = "https://erp.panthera.it/panth01/";
			//if(remoteUrl.charAt(remoteUrl.length()-1)=='/')
			//	remoteUrl = remoteUrl+"ws";

			//String url = "https://erp.panthera.it/panth02/ws?id=YWPQ";
			//String url = "https://erp.panthera.it/panth02/it/thr/thip/base/pthupd/ws/WSProtectedQueries.jsp?a=b";


			//String url = remoteUrl + "?id=YWPQ";
			if(remoteUrl.charAt(remoteUrl.length()-1)!='/')
				remoteUrl = remoteUrl + "/";
			String url = remoteUrl + "it/thr/thip/base/pthupd/ws/WSProtectedQueries.jsp?a=b";
			String tokenUID = "FxOrd01ws";
			//String urlParameters = getUrlParameters(tokenUID, query);
			String urlParameters = getUrlParameters(tokenUID, elencoFix);

			Map mm = resultFromJsonResponse(url, urlParameters);

			List records = (List) mm.get("records");
			List errori = (List) mm.get("errors");
			List<String> elencoFixOrdinate = new ArrayList<String>();
			if (records != null && !records.isEmpty()) 
			{
				Iterator iter = records.iterator();
				while (iter.hasNext()) 
				{

					JSONArray record = (JSONArray) iter.next();
					String rigaFO = (String) WrapperJSON.getValue(record, 0);					
					elencoFixOrdinate.add(rigaFO);
				}
			}

			valori.put("elencoFixOrdinate", elencoFixOrdinate);
			valori.put("errors", errori);
		} 
		catch (Throwable t) 
		{
			t.printStackTrace();
		}
		return valori;
	}



	protected String getUrlParameters(String tokenUID, String elencoFix) 
	{
		String params = "";
		//params += "&tokenUID=" + tokenUID /*+ "&asJsonTypes=on&onlyRecords=on"*/ + "&QUERYID=FIXORDER";
		params += "tokenUID=" + tokenUID /*+ "&asJsonTypes=on&onlyRecords=on"*/ + "&QUERYID=FIXORDER";
		if (elencoFix != null) 
		{
			try 
			{
				//params = "Fix=" + URLEncoder.encode(elencoFix, "UTF-8") + params;
				//params = "FIX=" + URLEncoder.encode(elencoFix, "UTF-8") + params;
				params = params + "&FIX=" + URLEncoder.encode(elencoFix, "UTF-8");
			} 
			catch (UnsupportedEncodingException e) 
			{
				e.printStackTrace();
			}
		}
		return params;
	}


	protected void readFile(File f, Collection lines) throws IOException
	{
		BufferedReader input = new BufferedReader(new FileReader(f));
		String line = null;
		while((line = input.readLine()) != null)
			lines.add(line);
		input.close();
	}

	//FG ini - 33209
	protected void writeErrorFile(File f, Collection lines) throws IOException
	{
		PrintWriter output = new PrintWriter(new FileWriter(f));
		Iterator i = lines.iterator();
		while(i.hasNext())
			output.println(ERROR_PREFIX + " " + i.next());
		output.close();
	}

	protected void writeWarningFile(File f, Collection lines) throws IOException
	{
		PrintWriter output = new PrintWriter(new FileWriter(f));
		Iterator i = lines.iterator();
		while(i.hasNext())
			output.println(WARNING_PREFIX + " " + i.next());
		output.close();
	}
	//FG fine - 33209

	protected void writeFile(File f, Collection lines) throws IOException
	{
		PrintWriter output = new PrintWriter(new FileWriter(f));
		Iterator i = lines.iterator();
		while(i.hasNext())
			output.println(i.next());
		output.close();
	}

	protected void popolaTabellaFixOrder() throws Exception
	{
		File fixOrder = new File("T:/thip/4.0/fix/FixOrder.txt");
		List<String> righeFixOrderT = new ArrayList<String>();
		readFile(fixOrder, righeFixOrderT);
		Iterator<String> i = righeFixOrderT.iterator();
		int penultimoUpdateVRM = -1;
		int ultimoUpdateVRM = -1;
		int posizione = 0;
		i = righeFixOrderT.iterator();
		while(i.hasNext())
		{
			String s = i.next();
			if (s.startsWith("UpdateVRM"))
			{
				penultimoUpdateVRM = ultimoUpdateVRM;
				ultimoUpdateVRM = posizione;
			}
			posizione++;
		}
		List newFixOrderLines = righeFixOrderT.subList(penultimoUpdateVRM + 1, ultimoUpdateVRM);
		newFixOrderLines.add(righeFixOrderT.get(ultimoUpdateVRM));
		i = newFixOrderLines.iterator();
		List<String> insiemeFix = new ArrayList<String>();

		while(i.hasNext())
		{
			String s = i.next();
			if (!s.equals("") && !s.startsWith("Application") && !s.startsWith("Description") && !s.startsWith("UpdateVRM") && !s.startsWith("#"))
			{
				String numFix = s.substring(0, s.indexOf(":"));
				if (!insiemeFix.contains(numFix))
					insiemeFix.add(numFix);
			}
		}



		ConnectionDescriptor dbHD = new ConnectionDescriptor("PANTH01", "db2admin", "P4nthS1r10" , new DB2NetDatabase("10.65.20.27", "50001"));
		ConnectionManager.pushConnection(dbHD);

		CachedStatement delete = new CachedStatement("DELETE FROM " + SystemParam.getSchema("THIPPERS") + "FIX_ORDER");
		CachedStatement insert = new CachedStatement("INSERT INTO " + SystemParam.getSchema("THIPPERS") + "FIX_ORDER (ID, FIX) VALUES (?, ?)");
		delete.executeUpdate();
		int id = 0;
		i = insiemeFix.iterator();
		while (i.hasNext()) 
		{
			String s = i.next();
			insert.getStatement().setInt(1, id++);
			insert.getStatement().setInt(2, Integer.parseInt(s));
			int rs = insert.executeUpdate();
			//System.out.println(rs);
		}
		ConnectionManager.commit();
		ConnectionManager.popConnection(dbHD);
	}


	public Map resultFromJsonResponse(String url, String urlParameters)  
	{
		Map m = new HashMap();

		List errori = new ArrayList();
		List r = new ArrayList();
		List e = new ArrayList();

		String responseCode = "";
		String json = "";
		try 
		{
			String[] res = sendGet(url, urlParameters);
			responseCode = res[0];			
			json = res[1];
			if (responseCode.equals(String.valueOf(HttpURLConnection.HTTP_OK))) 
			{
				WrapperJSON wj = WrapperJSON.getInstance();

				r = (List)wj.getObjectFormJSONString(json, "records", List.class);
				e = (List)wj.getObjectFormJSONString(json, "errors", List.class);
				if (e != null) 
					errori.addAll(e);
			}
			else
			{
				errori.add("Errore nella connessione al server panthera.it. Le fix non verranno installate.");
				errori.add(responseCode + ": " + json);
			}
		}
		catch (Throwable t) 
		{
			errori.add(t.getMessage());
			String err =  SimpleDateFormat.getInstance().format(new Date(System.currentTimeMillis())) + " : " + t.getMessage(); 
			System.err.println(err);
			t.printStackTrace();
		}

		m.put("errors", errori);
		m.put("records", r);

		return m;
	}


	public String[] sendGet(String url, String urlParameters) throws Exception 
	{
		String[] result = null;
		try 
		{
			//String murl = url + "?" + urlParameters;
			String murl = url + "&" + urlParameters;
			//URL obj = new URL(murl);
			String u = "";
			if (murl != null)
				u = murl.toUpperCase(); 
			URL obj = null;
			if (u != null && u.indexOf("HTTPS") != -1)
				obj = new URL(null, murl, new sun.net.www.protocol.https.Handler());
			else
				obj = new URL(murl);
			
			//HttpURLConnection con = (HttpURLConnection) obj.openConnection();
			HttpURLConnection con = (HttpURLConnection) openConnection(obj);

			// optional default is GET
			con.setRequestMethod("GET");

			int responseCode = con.getResponseCode();
			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();

			while ((inputLine = in.readLine()) != null) 
				response.append(inputLine);
			in.close();
			result = new String[]{String.valueOf(responseCode), response.toString()};
		}		
		catch (Throwable t) 
		{
			result = new String[] {"400", t.getMessage()};			
			String err =  SimpleDateFormat.getInstance().format(new Date(System.currentTimeMillis())) + " : " + t.getMessage(); 
			System.err.println(err);
		}
		return result;
	}

	protected void creaDirectoryTemporanee(String dir)
	{

		//		String iDirLavoro = null;
		//	    String iDirLavoroTmp = null;
		//	    String iDirFixPanthera = null;
		eliminaDirectoryFix();
		FileUtil.deleteTree(dir + "FixDaInstallareTemp");
		//System.out.println("ivDirectoryTemporanea " + ivDirectoryTemporanea);
		ivDirectoryTemporanea = new File(dir + "/FixDaInstallareTemp");
		ivDirectoryTemporanea.mkdir();
		ivDirFixOrder = new File(ivDirectoryTemporanea, "FixOrder");
		ivDirFixOrder.mkdir();
		ivDirLog = new File(ivDirectoryTemporanea, "log");
		ivDirLog.mkdir();
		//        FileUtil.copyFile(ivNomeDirScriptOrigNT +  "/setFixClasspath.txt", ivDirFixOrder +  "/setFixClasspath.txt");
		//

	}

	public void unzip(String zipFile, String outFolder) throws Exception
	{
		ZipFile zf = new ZipFile(zipFile);
		Enumeration e = zf.entries();
		ZipEntry entry;
		InputStream in = null;
		BufferedOutputStream out = null;
		int BUFFER = 2048;
		byte buffer[] = new byte[BUFFER];
		int i;
		File f;
		String path;
		while(e.hasMoreElements())
		{
			entry = (ZipEntry)e.nextElement();
			String entryName = entry.getName();
			entryName = entryName.replace('\\', File.separatorChar);
			f = new File(outFolder + File.separator + entryName);
			if(entry.isDirectory())
				f.mkdirs();
			else
			{
				f.getParentFile().mkdirs();
				in = zf.getInputStream(entry);
				out = new BufferedOutputStream(new FileOutputStream(f),BUFFER);
				while((i = in.read(buffer,0,buffer.length)) != -1)
				{
					out.write(buffer,0,i);
				}
				in.close();
				out.flush();
				out.close();
				f.setLastModified(entry.getTime());
			}
		}
		zf.close();	
	}



	public void setFixClassPath() throws Exception
	{

		Map fixClassPath = new HashMap();
		Iterator i = ivSetFixClassPath.iterator();    	
		while(i.hasNext())
		{
			String s = (String)i.next();

			if(!s.equals("") && !s.startsWith("#"))
			{
				int indx = s.indexOf("/");
				if (indx != -1)
				{
					StringTokenizer st = new StringTokenizer(s, "/");
					int fix = -1; 

					while (st.hasMoreTokens())
					{
						String s1 = st.nextToken();
						//System.out.println("s1 " + s1);
						try
						{
							fix = Integer.parseInt(s1);
							fixClassPath.put(s1, s);
							break;
						}
						catch (Exception e)
						{}
					}
				}
			}
		}

		List insiemeFixOrdinate = new ArrayList();
		i = ivRigheFixOrderFix.iterator(); 
		while (i.hasNext())
		{
			String s = (String)i.next();
			s = s.trim();
			if (!s.equals("") && !s.startsWith("Application") && !s.startsWith("Description") && !s.startsWith("UpdateVRM") && !s.startsWith("#"))
			{
				String numFix = s.substring(0, s.indexOf(":"));
				if (!insiemeFixOrdinate.contains(numFix))
					insiemeFixOrdinate.add(numFix);
			}
		}

		i = insiemeFixOrdinate.iterator();    	
		while(i.hasNext())
		{
			String fix = (String)i.next();
			String path = (String)fixClassPath.get(fix);

			if (path != null)
				FileUtil.copyTree(ivDirFixPanthera + File.separator + path + File.separator + "lib" , ivDirFixPanthera + File.separator + "lib" , "*.*");
		}
	}

	protected List verificaSeFixèPrerequisitoDiAltreFix(File fileFix)
	{
		List ret = new ArrayList();
		List l = (List)ivInsiemeZipFixDaInstallare.get(fileFix);
		Set insiemeFixContenuteNelloZipDellaFix = (Set)l.get(0);
		Iterator it = ivInsiemeZipFixDaInstallare.keySet().iterator();
		while (it.hasNext())
		{
			File fn = (File)it.next();
			if (fn.equals(fileFix))
				continue;
			l = (List)ivInsiemeZipFixDaInstallare.get(fn);
			Set prerequisiti = (Set)l.get(1);
			Iterator it2 = insiemeFixContenuteNelloZipDellaFix.iterator();
			while (it2.hasNext())
			{
				String f = (String)it2.next();
				if (prerequisiti.contains(f))
				{
					if (!ret.contains(fn))
						ret.add(fn);
					break;
				}
			}
		}
		return ret;
	}

	protected boolean isAmbienteDiProva()
	{   
		String trasmittenteFPValue =  IniFile.getValue("thermfw.ini", "Web", "TrasmittenteFP");
		String nomeDataBase = ConnectionManager.getCurrentConnectionDescriptor().getDBName();

		Database db = ConnectionManager.getCurrentDatabase();
		if (db instanceof DB2AS400Database) {
			nomeDataBase = SystemParam.getFrameworkSchema();
		}

		boolean siamoInAmbienteDiProva = false;

		if (trasmittenteFPValue ==null ||trasmittenteFPValue.equals(""))
			siamoInAmbienteDiProva = true;
		if (nomeDataBase !=null && !nomeDataBase.equals("")  && !nomeDataBase.equals(trasmittenteFPValue))
			siamoInAmbienteDiProva = true;
		return siamoInAmbienteDiProva;
	}

	private static HostnameVerifier createHostnameVerifier() 
	{
		return  new HostnameVerifier() 
		{
			public boolean verify(String hostname, SSLSession session) 
			{
				return true;
			}
		};
	}

	private static SSLSocketFactory createSSLSocketFactory() throws KeyManagementException, NoSuchAlgorithmException 
	{
		TrustManager[] trustManagers = new TrustManager[] {createTrustAllCertificate()};
		KeyManager[] keyManagers = null;
		SSLContext sc = SSLContext.getInstance("TLS");
		sc.init(keyManagers, trustManagers, new java.security.SecureRandom());
		return sc.getSocketFactory();
	}

	private static TrustManager createTrustAllCertificate() 
	{
		return new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() 
			{
				return new java.security.cert.X509Certificate[] {};
			}

			public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			}

			public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			}
		};
	}

	private static HttpURLConnection openConnection(URL url) throws KeyManagementException, NoSuchAlgorithmException, IOException 
	{
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		if (con instanceof HttpsURLConnection) 
		{
			HttpsURLConnection scon = (HttpsURLConnection) con;
			scon.setHostnameVerifier(createHostnameVerifier());
			scon.setSSLSocketFactory(createSSLSocketFactory());
		}
		return con;
	}


	public static void main(String[] args) throws Exception
	{
		//		Database database = new DB2NetDatabase("10.65.20.27", "50001");
		//      ConnectionManager.openMainConnection("PANTH01", "db2admin", "P4nthS1r10", database);

		Security.setCurrentDatabase(args[0], null); 
		Security.openDefaultConnection(); 
		ApplicatoreFix p = new ApplicatoreFix();
		//p.popolaTabellaFixOrder();
		p.run(args);
		Security.closeDefaultConnection(); 

		//        ConnectionManager.closeMainConnection();
	}
}
