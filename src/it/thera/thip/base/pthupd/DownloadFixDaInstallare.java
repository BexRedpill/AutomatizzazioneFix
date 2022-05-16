package it.thera.thip.base.pthupd;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.map.HashedMap;

import com.thera.thermfw.base.SystemParam;
import com.thera.thermfw.base.Trace;
import com.thera.thermfw.persist.CachedStatement;
import com.thera.thermfw.persist.ConnectionManager;
import com.thera.thermfw.persist.PersistentObject;
import com.thera.thermfw.security.Security;
import com.thera.thermfw.setup.FixFileProcessor;
import com.thera.thermfw.util.file.FileUtil;
import com.thera.thermfw.web.LicenceManager;

import it.thera.thip.base.pthupd.servlet.DownloadFixError;
import it.thera.thip.base.util.RicercaPrerequisitiFix;
import sun.net.www.protocol.ftp.FtpURLConnection;

public class DownloadFixDaInstallare {

	private static final String NT = "NT";
	private static final String THERA = "THERA";
	private static final String ZIP = ".zip";
	private static final String THIP = "THIP";
	private static final String ERRORE = "Errore!";
	private static final String COOKIE = "Cookie";
	private static final String LOG = "log";
	private static final String FIX_DA_SCARICARE_TEMP = "DownloadInstallaFixLog\\FixDaScaricareTemp";
	private static final String PREREQUISITI_NON_PUBBLICATI = "Non tutti i prerequisiti delle fix sono pubblicati.";
	private static final String FIX_NON_PUBBLICATE = "Non tutte le fix sono pubblicate.";
	private static final String ERROR_LOGIN = "Il login non è stato effettuato correttamente.";
	private final String ERROR_PREFIX = "#ERRORE#";
	private static final String ERROR_TXT_NAME = "Errori.txt";
	public static final String USER_FTP = "UTRead";
	public static final String PASSWORD_FTP = "fvXPYAg%25S%26";
	public static final String FTP_URL_FASTUPDATE = "ftp://" + USER_FTP + ":" + PASSWORD_FTP + "@ftp.panthera.it/PTH_PANTHERA%2F";
	private static final int ARBITARY_SIZE = 1048;

	private ArrayList<String> ivFixDaInstallare = new ArrayList<String>();
	private ArrayList<String> ivFixDaInstallareInAutomatico = new ArrayList<String>();
	private ArrayList<String> ivFixDaInstallareDaRemoto = new ArrayList<String>();
	private String ivVRM = new String();
	private Map<String, Set <String>> ivMappa_fix_prerequisiti = new HashedMap<String, Set <String>>();
	private Map<String, Set <String>> ivMappa_fix_prerequisiti_daRemoto = new HashedMap<String, Set <String>>();
	private Map<String, Set <String>> ivMappa_fix_prerequisiti_inAutomatico = new HashedMap<String, Set <String>>();
	private File ivDirectoryTemporanea = null;
	private File ivDirLog = null;
	private List<String> ivErrori = new ArrayList<String>();
	private String ivUtente;
	private String ivPassword;
	private String ivCookie = null;
	private String ivRepositoryFix;
	List<DownloadFixError> ivErrorsDownloadFixes = new ArrayList<DownloadFixError>();
	List<Fix> ivFixScaricateCorrettamente = new ArrayList<Fix>();
	List<File> ivFileScaricatiCorrettamente = new ArrayList<File>();
	CachedStatement iUpdateTipoInstallazione = new CachedStatement("UPDATE " + SystemParam.getSchema(THIP) +
			"FIXES SET TIPO_INSTALLAZIONE = ? WHERE FIX = ? " + "AND TIPO_INSTALLAZIONE = " + Fix.TI_NON_INSTALLATA);

	public static void main(String[] args) throws Exception{
		if(args != null && args.length >= 2) {
			Security.setCurrentDatabase(args[0], null); 
			Security.openDefaultConnection(); 
			
			String password = FixFileProcessor.readThermPwd(args[1]);
			Security.openMainSession(args[1],password);
			
			DownloadFixDaInstallare paramFix = new DownloadFixDaInstallare();
			paramFix.run(args);
			Security.closeDefaultConnection(); 
		}else {
			System.out.println(ERRORE);
		}
	}

	public boolean run(String[] args) throws IOException {
		LicenceManager.initLicence(THIP);
		/*
		 * Recuperare dalla tabella THIP.FIXES tutte le fix che hanno il flag 
		 * TIPO_INSTALLAZIONE uguale a 2 (installare da remoto) o a 3 (installare in automatico)
		 */
		getFixDaInstallare();
		/*
		 * Se ci sono fix da installare deve recuperare il cookie di accesso a domino, cioè bisogna fare il login
		 */
		creaDirectoryTemporanee();
		if(ivFixDaInstallare != null && ivFixDaInstallare.size() >= 1){
			ivCookie = login(args);
		} else {
			return true;
		}

		if(ivCookie != null) {
			/*
			 * Per ogni fix da installare deve calcolare i suoi prerequisiti rispetto all’ultimo fastupdate installato sul sistema
			 */
			ivVRM = getVRM();
			
			for (String fix : ivFixDaInstallare) {
				//distinguo fix da installare da remoto e in automatico
				if(ivFixDaInstallareDaRemoto.contains(fix)) {
					aggiungiPrerequisiti(fix, ivMappa_fix_prerequisiti_daRemoto);
				}else if(ivFixDaInstallareInAutomatico.contains(fix)) {
					aggiungiPrerequisiti(fix, ivMappa_fix_prerequisiti_inAutomatico);
				}
			}
			ivMappa_fix_prerequisiti.putAll(ivMappa_fix_prerequisiti_daRemoto);
			ivMappa_fix_prerequisiti.putAll(ivMappa_fix_prerequisiti_inAutomatico);

			/*
			 * Recuperati i prerequisiti deve verificare che questi e la fix stessa siano tutti pubblicati. 
			 * Se la fix o uno dei suoi prerequisiti non sono pubblicati la fix non deve essere scaricata e deve essere compilato il 
			 * file di log notificando che la fix non può essere scaricata perché lei o uno dei suoi prerequisiti non sono più pubblicati.
			 */
			boolean tutteFixPubblicate = false;
			boolean tuttiPrerequisitiPubblicati = false;
			try {
				tutteFixPubblicate = VerificaFixPubblicata.getInstance().areFixPubblicate(ivFixDaInstallare);
				List<String> prerequisitiList = convertPrerequisitiSetToList();
				if(prerequisitiList.size() > 0) {
					tuttiPrerequisitiPubblicati = VerificaFixPubblicata.getInstance().areFixPubblicate(prerequisitiList);
				}else {
					tuttiPrerequisitiPubblicati = true;
				}
				
				//Non va però ad indicare le singole fix che non sono pubblicate
				if(!tutteFixPubblicate){
					ivErrori.add(FIX_NON_PUBBLICATE);
				}
				if(!tuttiPrerequisitiPubblicati){
					ivErrori.add(PREREQUISITI_NON_PUBBLICATI);
				}
			}catch (Exception e) {
				e.printStackTrace();
				ivErrori.add(e.getMessage());
			}

			/*
			 * Se la fix e tutti i suoi prerequisiti sono pubblicati queste devono essere scaricate salvando gli zip nella directory Repository fix.
			 * Inoltre ogni fix scaricata deve essere registrata come scaricata (registrare su Panthera l'avvenuto scaricamento del cliente).
			 */
			if(tutteFixPubblicate && tuttiPrerequisitiPubblicati) {
				try {
					for(Set<String> prerequisiti : ivMappa_fix_prerequisiti.values()) {
						if(ivMappa_fix_prerequisiti_daRemoto.containsValue(prerequisiti)) {
							for(String ogni_prerequisito: prerequisiti) {
								updateTipoInstallazione(ogni_prerequisito, String.valueOf(Fix.TI_INSTALLA_DA_REMOTO));
							}
						}else if(ivMappa_fix_prerequisiti_inAutomatico.containsValue(prerequisiti)){
							for(String ogni_prerequisito: prerequisiti) {
								updateTipoInstallazione(ogni_prerequisito, String.valueOf(Fix.TI_INSTALLA_IN_AUTOMATICO));
							}
						}
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
				
				try {		
					List<String> daScaricare = unioneFixEPrerequisitiInUnUnicoArray();

					for(String fixDaScaricare: daScaricare) {
						Fix f = Fix.elementWithKey(fixDaScaricare, PersistentObject.NO_LOCK);
						if (f != null && f.getPacchetto() == 'M') {
							char tipoInstallazione = f.getTipoInstallazione();
							
							int fixPacchetto = f.getFixPacchetto();
							if (fixPacchetto != 0) {
								f = Fix.elementWithKey(String.valueOf(fixPacchetto), PersistentObject.NO_LOCK);
								if(f.getTipoInstallazione() == Fix.TI_NON_INSTALLATA) {
									f.setTipoInstallazione(tipoInstallazione);
								}
							}
						}
						
						if(f.getPacchetto() == 'P') {
							String fixInPacchetto = f.getFixInPacchetto();
							String[] parts = fixInPacchetto.split("-");
							for(String fix : parts) {
								updateTipoInstallazione(fix, String.valueOf(f.getTipoInstallazione()));
							}
						}

						if (f != null) {
							/*
							 * Ricavare l'URL da cui avviene lo scaricamento
							 */
							String sistemaOperativo = NT; //Ricavare il sistemaOperativo (per ora funziona solo per Windows)
							String urlFix = f.getUrlPerDownload(sistemaOperativo);

							//Se non è un URL di domino devo costruirmi il nome del pacchetto (l'url FTP)
							String nomePacchetto = null;
							if(!isDominoURL(urlFix)) {
								String fixDomain = f.getDomain();

								if(fixDomain != null && f.getTipoImplemento() == Fix.FASTUPDATE) {
									if(fixDomain.startsWith("FAST")) {
										nomePacchetto = buildNomePacchettoFastUpdate(f, sistemaOperativo);
									} else {
										nomePacchetto = buildNomePacchettoCumulativo(f, sistemaOperativo);
									}

									if(nomePacchetto != null) {
										urlFix = FTP_URL_FASTUPDATE + nomePacchetto + ";type=i";
									}
								}
							}
							String key = nomePacchetto != null? nomePacchetto : String.valueOf(f.getFix());

							/*
							 * Download della singola fix con il URL ricavato
							 */
							URL url = new URL(urlFix);

							HttpURLConnection httpCon = null; 
							FtpURLConnection ftpCon = null;

							//In base al tipo di url ricavo la connessione e gestisco eventuali errori di connessione
							if (isDominoURL(urlFix))
							{	
								httpCon = (HttpURLConnection) url.openConnection();
								httpCon.addRequestProperty(COOKIE, ivCookie);
							}
							else {
								ftpCon = (FtpURLConnection) url.openConnection();
							}

							int responseCode = -1;
							Exception exception = null;
							try {
								if(httpCon != null)
									responseCode = httpCon.getResponseCode();
								else {
									responseCode = 0;
								}
							}catch(Exception e) {
								e.printStackTrace(Trace.excStream);
								exception = e;
							}

							if (responseCode >= 400 || responseCode == -1) {
								ivErrorsDownloadFixes.add(new DownloadFixError(responseCode, key, urlFix, exception));
							} else {
								//Se non ci sono errori di connessione al server leggiamo l'inputStream da questa connessione
								InputStream in = null;
								boolean connectionError = false;
								try {
									in = httpCon != null ? httpCon.getInputStream() : ftpCon.getInputStream();
								}catch(Exception e) {
									e.printStackTrace(Trace.excStream);
									connectionError = true;
									exception = e;
								}

								if(connectionError) {
									ivErrorsDownloadFixes.add(new DownloadFixError(responseCode, key, urlFix, exception));
								}else {
									String nomeFile = getNomeFileInBaseAlUrl(urlFix, key);
									
									//Download dei file .zip tramite un output stream nella repository indicata
									FileOutputStream out = downloadFiles(in, ivRepositoryFix, nomeFile);
									
									in.close();
									out.close();

									//salvo le fix andate bene per fare poi la notifica
									ivFileScaricatiCorrettamente.add(new File(nomeFile));
									ivFixScaricateCorrettamente.add(f);
								}
							}

						}
					}
					iUpdateTipoInstallazione.free();

					if(ivErrorsDownloadFixes.size() > 0) {
						ivErrori.add(ivErrorsDownloadFixes.toString());
						
						String errorLogURL = ivErrorsDownloadFixes.get(0).getRemoteURL();
						if(errorLogURL.contains(USER_FTP) || errorLogURL.contains(PASSWORD_FTP)) {
							errorLogURL = hideFTPCredentials(errorLogURL);
						}
						
						String error = "Alert('Impossibile completare il download "; 
						
						if(isDominoURL(errorLogURL))
							error += "della fix ";
						else
							error += "del file ";
						
							error += "\\\"" + ivErrorsDownloadFixes.get(0).getNumFix() + "\\\"";
							
						if(isDominoURL(errorLogURL))
							error += "\\nHTTP ERROR: " + ivErrorsDownloadFixes.get(0).getHttpErrorCode();
								
							 error += "\\nURL: " + errorLogURL;
						
						String exceptionMessage = ivErrorsDownloadFixes.get(0).getExceptionMessage();
						if(exceptionMessage != null && exceptionMessage.contains("\r\n")) {
							error += "\\n\\n" + ivErrorsDownloadFixes.get(0).getExceptionMessage().split("\\r\\n")[0];
						}
						ivErrori.add(error);
					}

					//se il download di una fix non va a buon fine o ci sono altri errori allora cancello tutto quello che ho scaricato
					if(ivErrori.size() != 0) {
						for(File file : ivFileScaricatiCorrettamente) {
							file.delete();
						}
					}else {
						/*
						 * Infine ogni fix scaricata deve essere registrata come scaricata (registrare su Panthera l'avvenuto scaricamento del cliente).
						 */
						for(Fix fix : ivFixScaricateCorrettamente) {
							fix.notificaDownload();
						}
					}

				}catch(SQLException ex) {
					ex.printStackTrace();
				}
			}

		} else {
			ivErrori.add(ERROR_LOGIN);
		}

		if(ivErrori != null && ivErrori.size() >= 1) {
			ivErrori.add(0, "Errore durante il download delle fix. L'operazione viene interrotta a causa dei seguenti problemi: ");
			writeErrorFile(new File(ivDirLog, ERROR_TXT_NAME), ivErrori);
		} 

		return true;
	}

	private void aggiungiPrerequisiti(String fix, Map<String, Set<String>> listaConPrerequisiti) {
		Set prerequisiti = RicercaPrerequisitiFix.getPrerequisiti(fix, ivVRM);
		prerequisiti = RicercaPrerequisitiFix.getPrerequisitiNonInstallati(prerequisiti);
		listaConPrerequisiti.put(fix, prerequisiti);
	}

	private void updateTipoInstallazione(String numero_fix, String tipo_installazione) throws SQLException {
		iUpdateTipoInstallazione.getStatement().setString(1, tipo_installazione);
		iUpdateTipoInstallazione.getStatement().setString(2, numero_fix);
		iUpdateTipoInstallazione.executeUpdate();
		ConnectionManager.commit();
	}

	private String getNomeFileInBaseAlUrl(String urlFix, String key) {
		String nomeFile = null;
		if(isDominoURL(urlFix)) {
			nomeFile = getNomeFileByDominoURL(urlFix, key);
		}

		if(nomeFile == null) {
			nomeFile = key;
		}

		if (!nomeFile.endsWith(ZIP)) {
			nomeFile += ZIP;
		}
		return nomeFile;
	}

	private FileOutputStream downloadFiles(InputStream in, String dir, String nomeFile) throws FileNotFoundException, IOException {
		FileOutputStream out = new FileOutputStream(new File(new File(dir), nomeFile));

		int len;
		byte[] buf = new byte[ARBITARY_SIZE];
		while ((len = in.read(buf)) > 0) {
			if (out != null)
				out.write(buf, 0, len);
		}
		return out;
	}

	private List<String> unioneFixEPrerequisitiInUnUnicoArray() {
		ArrayList<String> daScaricare = new ArrayList<String>();
		daScaricare.addAll(ivMappa_fix_prerequisiti.keySet());
		
		for(Set<String> Setstring: ivMappa_fix_prerequisiti.values()) {
			for(String prereq: Setstring) {
				if(!daScaricare.contains(prereq)) {
					daScaricare.add(prereq);
				}
			}
		}
		
		return daScaricare;
	}

	private List<String> convertPrerequisitiSetToList() {
		List<String> prerequisitiList = new ArrayList<String>();
		for(Set<String> prerequisiti : ivMappa_fix_prerequisiti.values()) {
			for(String prerequisito: prerequisiti) {
				if(!prerequisitiList.contains(prerequisito)) {
					prerequisitiList.add(prerequisito);
				}
			}
		}
		return prerequisitiList;
	}

	private String getVRM() {
		try {
			String qry = "SELECT * FROM " + SystemParam.getSchema(THIP) + "PANTHERA_LEVEL WHERE DESCRIZIONE_FIX LIKE '%riall%' ORDER BY VERSIONE DESC, RELEASE DESC, MODIFICA DESC, TIMESTAMP_CRZ DESC "; 
			
			CachedStatement stmt = new CachedStatement(qry);
			ResultSet result = stmt.executeQuery();

			if (result.next()) {
				ivVRM = result.getString("VERSIONE") + "." + result.getString("RELEASE") + "." + result.getString("MODIFICA"); 
			}

			result.close();
			stmt.free();
		}catch(SQLException e) {
			e.printStackTrace();
		}
		return ivVRM;
	}

	private String login(String[] args) {
		//Se viene dato in args -> database, utenteDatabase, utenteLoginPanthera, passwordLoginPanthera, RepositoryFix
		if(args.length == 5) {
			ivCookie = DominoCookieGetter.getCookieFromLoginDomino(args[2], args[3]);
			ivRepositoryFix = args[4];
		}else {
			//Se si ha solo il db e utente database come args allora si richiedono da inserire gli altri dati dalla tabella presente nel database
			try {
				String qry = "SELECT * FROM " + SystemParam.getSchema(THIP) +"PSN_CATALOGO_FIX";
				CachedStatement stmt = new CachedStatement(qry);
				ResultSet result = stmt.executeQuery();

				if (result.next()) { 
					ivUtente = result.getString("UTENTE_DOMINO");
					ivPassword = result.getString("PWD_UTENTE_DOMINO");
					ivRepositoryFix = result.getString("REPOSITORY_FIX");
				}
				
				ivCookie = DominoCookieGetter.getCookieFromLoginDomino(ivUtente, ivPassword);	
				result.close();
				stmt.free();
			}catch(SQLException e) {
				e.printStackTrace();
			}
		}
		return ivCookie;
	}

	private void getFixDaInstallare() {
		try {
			String qry = "SELECT FIX"
					+ " FROM " + SystemParam.getSchema(THIP) +"FIXES"
					+ " WHERE TIPO_INSTALLAZIONE = ? ";
			
			CachedStatement statement = new CachedStatement(qry);
			//Seleziono le fix da installare da remoto
			statement.getStatement().setString(1, String.valueOf(Fix.TI_INSTALLA_DA_REMOTO));
			ResultSet result = statement.executeQuery();
			
			while(result.next()) {
				ivFixDaInstallareDaRemoto.add(result.getString(1));
			}
			
			//Seleziono le fix da installare in automatico
			statement.getStatement().setString(1, String.valueOf(Fix.TI_INSTALLA_IN_AUTOMATICO));
			result = statement.executeQuery();
			
			while(result.next()) {
				if(!ivFixDaInstallareDaRemoto.contains(result.getString(1))) {
					ivFixDaInstallareInAutomatico.add(result.getString(1));
				}
			}
			
			result.close();
			statement.free();
			
			//Aggiungo all'array generico delle fix da installare quelle da remoto e quelle in automatico che non si ripetono
			ivFixDaInstallare.addAll(ivFixDaInstallareDaRemoto);
			for(String ivFixString: ivFixDaInstallareInAutomatico) {
				if(!ivFixDaInstallare.contains(ivFixString)) {
					ivFixDaInstallare.add(ivFixString);
				}
			}
		}catch(SQLException e) {
			e.printStackTrace();
		}
	}

	protected void creaDirectoryTemporanee(){
		FileUtil.deleteTree(FIX_DA_SCARICARE_TEMP);
		ivDirectoryTemporanea = new File(FIX_DA_SCARICARE_TEMP);
		ivDirectoryTemporanea.mkdirs();
		ivDirLog = new File(ivDirectoryTemporanea, LOG);
		ivDirLog.mkdir();
	}

	protected void writeErrorFile(File f, Collection lines) throws IOException
	{
		PrintWriter output = new PrintWriter(new FileWriter(f));
		Iterator i = lines.iterator();
		while(i.hasNext())
			output.println(ERROR_PREFIX + " " + i.next());
		output.close();
	}

	private boolean isDominoURL(String url) {
		return url.contains("domino");
	}

	private String buildNomePacchettoFastUpdate(Fix f, String os) {
		String prefixPacchetto = "PacchettoFastUpdate-";
		String nomePacchettoFastUpdate = null;
		if(os != null && f != null) {
			short v = f.getVersion();
			short r = f.getRelease();
			short m = f.getModification();
			if(os.equalsIgnoreCase(FixUtils.SO_WINDOWS))
				nomePacchettoFastUpdate = prefixPacchetto + "NT-V" + v + "R" + r + "M" + m +ZIP;
			else if(os.equalsIgnoreCase(FixUtils.SO_AS400))
				nomePacchettoFastUpdate = prefixPacchetto + "AS-V" + v + "R" + r + "M" + m + ZIP;
			else if(os.equalsIgnoreCase(FixUtils.SO_LINUX))
				nomePacchettoFastUpdate = prefixPacchetto + "LX-V" + v + "R" + r + "M" + m + ZIP;
		}
		return nomePacchettoFastUpdate;
	}

	private String buildNomePacchettoCumulativo(Fix f, String os) {
		String nomePacchettoCum = null;
		if(os != null && f != null) {
			short v = f.getVersion();
			short r = f.getRelease();
			short m = f.getModification();
			if(os.equalsIgnoreCase(FixUtils.SO_WINDOWS))
				nomePacchettoCum = "V" + v + "R" + r + "M" + m + "-NT.zip";
			else if(os.equalsIgnoreCase(FixUtils.SO_AS400))
				nomePacchettoCum = "V" + v + "R" + r + "M" + m + "-AS.zip";
			else if(os.equalsIgnoreCase(FixUtils.SO_LINUX))
				nomePacchettoCum = "V" + v + "R" + r + "M" + m + "-LX.zip";
		}
		return nomePacchettoCum;
	}

	private String getNomeFileByDominoURL(String remoteResource, String numFix) {
		String nomeFile = null;
		if (remoteResource != null && remoteResource.contains("/") && remoteResource.contains("domino")) {
			String[] splittedURL = remoteResource.split("/");
			nomeFile = splittedURL[splittedURL.length - 1];
			if (!nomeFile.endsWith(ZIP))
				nomeFile = numFix + ZIP;
		}

		return nomeFile;
	}
	
	private String hideFTPCredentials(String url) {
		url = url.replace(USER_FTP, "user").replace(PASSWORD_FTP, "password");
		return url;
	}
}