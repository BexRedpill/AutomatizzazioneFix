package com.thera.thermfw.setup;


import java.io.*;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;
import java.util.jar.*;

import com.thera.thermfw.base.*;
import com.thera.thermfw.batch.PrintingToolInterface;
import com.thera.thermfw.dict.*;
import com.thera.thermfw.persist.*;
import com.thera.thermfw.pref.*;
import com.thera.thermfw.security.*;
import com.thera.thermfw.setup.gui.*;
import com.thera.thermfw.util.ReorgRunStats;
import com.thera.thermfw.util.file.*;


/**
 * Classe che legge riga per riga il file con l'elenco delle fix da applicare.
 * In questo file CI DEVE ESSERE una riga iniziale che indica nome dell'applicazione,
 * modulo, versione, release e modifica preceduti dalla stringa "Application:":<br>
 *   Application: applicazione, modulo, versione, release, modifica<br>
 * In dettaglio:<br>
 * - Application: stringa per riconoscere il comando (con il separatore ":");<br>
 * - Applicazione: nome dell'applicazione;<br>
 * - Modulo: nome del modulo;<br>
 * - Versione, release, modifica: numeri che indicano V, R e M.<br>
 * + Esempio: <br>
 *   Application:THERM,base,1,1,2 <br>
 * Successivamente ci può essere una riga con le descrizioni dell'applicazione e del modulo
 * precedute dalla stringa "Description:":<br>
 *   Description: descrizione dell'applicazione, descrizione del modulo<br>
 * + Esempio: <br>
 *   Description: Applicazione del framework Therm, Modulo con le tabelle di base<br>
 * Di seguito va inserito l'elenco delle fix nel seguente modo:<br>
 *   numero della fix: classe da istanziare, nome del file, parametro1, parametro2, ecc...<br>
 * In dettaglio:<br>
 * - Numero della fix: il numero a 5 cifre che identifica la fix;<br>
 * - Classe da istanziare: è la classe che processerà il file successivamente specificato;<br>
 * - Nome del file: è il nome del file da lanciare per aggiornare il database;<br>
 * - Parametri: sono facoltativi e sono i parametri da passare alla classe che processerà
 *              il file (possono essere da 0 a n);<br>
 * + Esempio:<br>
 *   00033:com.thera.thermfw.setup.ApplyFix,00033\AlterResources.SQL<br>
 * Attenzione: le righe che appartengono alla stessa fix devono essere consecutive!<br>
 * Alla fine del file PUO' (ma non è obbligatorio) esserci una riga che aggiorna la
 * versione, la release o la modifica. I nuovi valori devono essere preceduti
 * dalla stringa "UpdateApplication:":<br>
 *   UpdateApplication: new versione, new release, new modifica<br>
 * In dettaglio:<br>
 * - UpdateApplication: stringa per riconoscere il comando (con il separatore ":");<br>
 * - New + versione/release/modifica: nuovo numero che indica V/R/M.<br>
 * Attenzione: può essere modificato anche un solo parametro.<br>
 * + Esempio: <br>
 *   UpdateApplication: 1,2,3 <br>
 * Nel file possono anche essere inserite delle righe di commento che devono però essere
 * precedute dal simbolo "#".
 * <br></br><b>Copyright (C) : Thera s.p.a.</b>
 * @author Laura Pezzolini 21/11/2001
 */
/* Revisions:
 * Fix #    Date          Owner      Description
 * 00148    21/11/2001    LP         Prima versione.
 * 00169    25/01/2002    LP         Corretto la compilazione di SonFolders e ParentFolders che non venivano chiamati nel momento giusto (cerca //...MOD 169).
 * 00170    29/01/2002    LP         Aggiunto un parametro per la compilazione delle fix per poter cambiare il percorso delle fix (cerca //...MOD 170 ).
 * 00905    22/10/2003    IT         Aggiunto possibilità di decidere per quale applicazione eseguire le fix.
 * 01315    23/01/2004    IT         Nuova implementazione applicazione fix
 * 01475    19/02/2004    IT         Modifiche alla copia dei file della fix
 * 01672    18/03/2004    IT         Escluso controllo INOPERATIVE VIEW per AS400
 * 01754    01/04/2004    IT         Riporto la descrizione della fix nella tabella SOFTWARE_FIX
 * 02031    25/05/2004    IT         Aggiunta reference package util.file
 * 02149    22/06/2004    IT         Inserita la rigenerazione delle viste
 * 02193    01/07/2004    IT         Ricerca NODB.SQL
 * 02226    08/07/2004    IT         Copia file Utente (Primrose nel caso di Panthera);
 * 02256    15/07/2004    IT         Solo copia dei files
 * 02258    16/07/2004    IT         Copia di server/client/reports per una seconda applicazione (es.primrose)
 * 02295    28/07/2004    ES         Aggiungo la possibilità di non trasformare l'insert in update: aggiunto parametro MI = modalità di inserimento (IO=inputOnly/IU=insert o update)
 * 02468    21/09/2004    IT         Aggiungo la costante NUM_FIX_PARAMS e correggo il numero di parametri fissi
 * 02662    19/10/2004    IT         Modifiche per permettere estensione procedura applicazione fix.
 * 02967    14/12/2004    IT         Modifiche per permettere la copia dei file tramite FTP
 * 03168    19/01/2005    DF         Aggiunta la possibilità di copiare i file ed effettuare commit ad ogni fix applicata
 * 03418    18/03/2005    DF         Verifica univocità assoluta del numero di fix. Aggiunto un parametro di lancio.
 *  3453    29/ 3/2005    Ryo        Usata la factory per la creazione di un nuovo FixFileProcessor
 * 03679    27/04/2005    MM         Modificata la struttura delle fix e predisposta la classe per esecuzione del processo di applicazione distribuita
 * 03913    14/06/2005    DM         Riparazione danni causati da rollback automatico su errore di SQL Server
 * 04401    29/09/2205    MM         Corretto nome della directory web-inf in WEB-INF per compatibilità con Linux
 *  4418    10/10/2005    Ryo        Gestito l'aggiornamento dei file jar
 * 04574    02/11/2005    PM-MM      Rigenerazione delle viste.
 * 04576    02/11/2005    MM         Aggiunto parametro per l'uso della nuova struttura gerarchica delle fix; aggiunti metodi per facilitare ridefinizione
 *  4813    22/12/2005    Ryo        Nell'eliminazione delle viste di DB2 e SQL Server, sostituito execute con executeUpdate nell'esecuzione dello statement di drop della view
 * 04959    25/01/2006    DF         Aggiunto metodo per rimozione autorizzazioni duplicate
 * 05003    03/02/2006    ES         Aggiunto metodo per ricalcolo flag Authorizable sulle entità
 *  5090    23/ 2/2006    Ryo        Corretto l'output dell'aggiornamento dei file jar e cambiato NO_LAST_MODIFIED_SET da ERROR a WARNING
 *  5116     2/ 3/2006    Ryo        Integrata la fix 4897 di Panthera (PM)
 * 05272    05/04/2006    PM         Aggiunto metoto resetAuthorizableFlag()
 * 05629    26/09/2006    DM         Aggiunto utilizzo di OracleCommandHistory e rigenerazione viste Oracle
 * 06239    02/11/2006    DM         Gestione fix anticipate
 * 06943    19/03/2007    DM         Aggiunto metodo per acquisire DDL delle viste Oracle.
 * 07281    10/05/2007    DM         Stampa istruzioni per cercare anche warning e righe duplicate
 * 07935    15/09/2007    DF         Cambiata modalità rigenerazione viste
 * 07997    09/10/2007    DF         Compatibilità SQLServer
 *  8892    19/ 3/2008    Ryo        Cambiata la signature di primroseJarBlender e il valore di ritorno di copyFixFile_WithFileSystem
 * 09044    14/04/2008    CB         Aggiunto test sull'esistenza delle cartelle di copia file per primrose
 *  9428    25/ 6/2008    Ryo        userPwd non viene più ricavata dai parametri passati ma dalla tabella dell'utente
 *  9802    19/ 9/2008    Ryo        Nella copia dei file .java, FixFileProcessor copia anche i file .tfml
 * 09634    30/07/2008    ES         Sostituisco le stringhe cablate con stringhe lette da file risorse
 * 10936    24/07/2009    DM         Assegnamento delle nuove tabelle al gruppo DEFAULT.
 * 11234    04/08/2009    ES         Copia file RPT da dir diverse a seconda della versione di Crystal Reports
 * 11347    04/08/2009    ES         Copia file RPT da dir diverse a seconda della versione di Crystal Reports
 * 11592    09/11/2009    DM         Inserito test che disattiva 10936 su piattaforma AS
 * 11998    20/01/2010    ES         Modifica alla distribuzione dei file RPT
 * 12355     9/ 4/2010    Ryo        Sistema per l'applicazione di fix speciali, tipicamente per caricamenti di alti volumi di dati, dopo la commit del cumulativo
 * 12771    03/06/2010    ES         Storicizzo SQLFileReport.txt + lettura risorse non messa in una static final
 * 12828    18/06/2010    ES         Evito che con -COMMIT e senza -CF cerchi comunque di copiare gli RPT
 * 12958    03/08/2010    ES         Modifico politica per storicizzare il file di log: il nome dell'ultimo log resta SQLFileReport, ma il precedente è rinominato aggiungendo il timestamp
 * 12816     5/ 8/2010    Ryo        Tracce che consentono di monitorare la fase di rimozione delle viste e la fase di creazione di ogni singola vista
 *                                   + ES: correzione alla 12958/12771
 * 13666    09/12/2010    ES         Nei messaggi di errore uso il nome del file di log corretto
 * 13667    10/12/2010    ES         Flag doneSthg= true se ho eseguito TDDML, SQL o classi e quindi devo anche rigenerare viste, rebuild di son/parent folders e reorg/runstats
 *                                                = false in caso contrario e non devo rigenerare le viste ecc.
 * 13978    10/02/2011    ES         Aggiunta supporto a Plex610: -CF2
 *                                    non ho implementato la gestioe di backupFiles e della copia FTP perché ritenute obsolete
 * 14160    16/11/2011    ES         Rigenero le viste con GRANT ALL
 * 14621    09/06/2011    ES         Aggiornamento politica esecuzione -CF2: se .... non devo più copiare server610 e serverdiff610 anche in WEB-INF/lib
 *                                                                     semplifico copia dei report
 *                                   Aggiungo nei parametri il path x Primrose.ini x 610
 * 14328    20/04/2011    ES         Evito eccezioni nel caso di rigenerazione viste errata ma senza eccezioni
 * 14420    22/04/2011    ES         Mostrare l'esistenza di fix anticipate anche a video e ordinare la lista delle fix già applicate
 * 15040    20/09/2011    ES         Rendo personalizzabile la copia degli RPT.
 * 16172    11/04/2012    ES         Per le fix A&C controllo che A&C sia spenta, se è accesa blocco l'applicazione fix
 * 16317    02/05/2012    ES         Evitare che con "Commit ad ogni fix" si saltino le righe di FixOrder di una fix successive ad una riga commentata
 * 19051    16/01/2014    CO         Rigenerare tutte le viste definite nella tabella dict_view
 * 18069    17/06/2013    MA         aggiunto nel report file i nomi delle schema dei viste rigenerati
 * 22098    30/09/2015    FM         eliminare file nls di primrose se necessario
 * 24495    10/10/2016    PM         Controllo livello middleware
 * 28999    13/03/2019    PM         Se dopo avere installato un fastupdate viene installata una fix web questa non risulta nel contextinfo.
 * 28381    10/06/2019    Mz         gestione sessione aperta
 * 31129    21/04/2020    HED        semplificazione del processo di installare fix
 * 32242    17/11/2020    AJ         correzione la commit del softwarefix
 * 33045    02/03/2021    HED
 * 33557    09/05/2021    PM         Fix tecnica
 */
/*********************************************************************
 * ATTENZIONE!
 * DALLA FIX 3913
 * CHIUNQUE ABBIA BISOGNO DI CHIAMARE COMMIT() O ROLLBACK() DEVE USARE
 * I METODI DI QUESTA CLASSE E NON QUELLI DEL CONNECTION MANAGER
 *********************************************************************/

public class FixFileProcessor implements FixRunnable {
	
	
	public boolean iAggiornaTimestampFixCopyOnlyFile = true;  	//Fix 33557 PM 


	/**
	 * File di risorse.
	 */
	public static final String SETUP_RES = "com.thera.thermfw.setup.resources.Setup";

	/**
	 * Stringa da inserire nei log in caso d'errore.
	 */
	/* Revisions:
	 * Fix nr   Date          Owner      Description
	 *  4418    10/10/2005    Ryo        Prima versione
	 */
	//Mod. 12771//public static String LOG_ERROR = ResourceLoader.getString(SETUP_RES, "ErrorString");//Mod. 9634//"********** #ERRORE# **********";

	/**
	 * Stringa da inserire nei log in caso di warning.
	 */
	/* Revisions:
	 * Fix nr   Date          Owner      Description
	 *  5090    23/ 2/2006    Ryo        Prima versione
	 */
	//Mod. 12771//public static String LOG_WARNING = ResourceLoader.getString(SETUP_RES, "WarningString");//Mod. 9634//"********** #WARNING# **********";

	//Mod. 14420 inizio
	/**
	* Numero di fix "già applicate" stampate per ogni riga.
	*/
	protected int MAX_APPL_FIX_X_LINE = 8;
	//fine mod. 14420

	/**Fix 2468
	 * Numero parametri fissi.
	 */
	//Mod.2976 - inizio
	//Potrebbero essere 7 nel caso in cui la copia dei file avvenga tramite FTP
	//public static final int NUM_FIX_PARAMS = 3;
	public static int NUM_FIX_PARAMS = 3;

	//Mod.2976 - fine

	/**
	 * BufferedReader per il file con l'elenco delle fix da applicare.
	 */
	protected BufferedReader fixFile;

	/**
	 * Stringa che rappresenta il nome dell'applicazione.
	 */
	protected String application;

	/**
	 * Stringa che rappresenta la descrizione dell'applicazione.
	 */
	protected String applDescription = "";

	/**
	 * Stringa che rappresenta il nome del modulo.
	 */
	protected String module;

	/**
	 * Stringa che rappresenta la desrizione del modulo.
	 */
	protected String modDescription = "";

	/**
	 * Stringa che rappresenta la versione.
	 */
	protected String versionStr;

	/**
	 * Stringa che rappresenta la release.
	 */
	protected String releaseStr;

	/**
	 * Stringa che rappresenta la modifica.
	 */
	protected String modificationStr;

	/**
	 * Numero che rappresenta la fix da applicare.
	 * Visto che è una stringa il numero sarà quello di 5 cifre con
	 * gli zeri in testa (es. fix 00022, oppure fix 00112, ecc.).
	 */
	protected String fixNrStr;

	/**
	 * Nome della classe che dovrà testare il file passato come parametro.
	 * Questa classe dovrà estendere FixRunner.
	 * @see com.thera.thermfw.setup.FixRunner
	 */
	protected String className;

	/**
	 * Array di stringhe da passare come parametro alla classe sopra indicata.
	 */
	protected String[] parameters;

	/**
	 * HashSet con l'elenco dei numeri di fix presenti nel file.
	 */
	protected HashSet fixVector = new HashSet();

	/**
	 * HashSet con l'elenco dei numeri di fix che sono già state applicate.
	 */
	protected HashSet oldFixVector = new HashSet(); //...MOD 170 (LP)

	/**
	 * Vettore che raccoglie i numeri delle fix applicate.
	 * Serve solo per contare quante fix sono state applicate rispetto a quelle
	 * totali presenti nel file e per raccogliere i numeri delle fix da salvare.
	 */
	protected Vector fixApplicated = new Vector();
	protected Vector fixApplicatedRoot = new Vector(); //Mod.2193
	protected String pathForCopy = ""; //Mod.2193

	/**
	 * Booleano che indica se la fix che si sta processando è stata pllicata correttamente.
	 */
	protected boolean ok = false;

	/**
	 * Stringa che rappresenta il tipo di piattaforma (da passare come parametro alla classe).
	 */
	protected String platformType;

	/**
	 * Stringa che rappresenta il percorso delle fix (da passare come parametro alla classe).
	 */
	protected String fixRoot; //...MOD 170 (LP)

	/**Mod.1315
	 * Stringa che rappresenta il percorso di dove sono installate le lib (da passare come parametro alla classe).
	 */
	protected String libRoot;

	/**Mod.1315
	 * Stringa che rappresenta il percorso di dove sono installate le jsp (da passare come parametro alla classe).
	 */
	protected String webRoot;

	/**Mod.1315
	 * Stringa che rappresenta il percorso di dove sono installati i report (da passare come parametro alla classe).
	 */
	protected String rptRoot;

	/**Mod.2226
	 * Stringa che rappresenta il percorso di dove copiare i file utente (da passare come parametro alla classe).
	 */
	protected String userRoot;

	/**Mod.2226
	 * Stringa con il nome della cartella da cui copiare (da passare come parametro alla classe).
	 */
	protected String nameUserDir;

	/**Mod.2258
	 * Stringa che rappresenta il percorso di dove è installato il server (da passare come parametro alla classe).
	 */
	protected String serverRoot;

	/**Mod.2258
	 * Stringa che rappresenta il percorso di dove è installato il client (da passare come parametro alla classe).
	 */
	protected String clientRoot;

	//Mod. 13978 inizio
	/**
	 * Stringa che rappresenta x -CF2 il percorso di dove è installato il server (da passare come parametro alla classe).
	 */
	protected String serverRoot2;

	/**
	 * Stringa che rappresenta il percorso x -CF2 di dove è installato il client (da passare come parametro alla classe).
	 */
	protected String clientRoot2;

	/**
	 * Stringa che rappresenta il percorso x -CF2 di dove sono installati i reports (da passare come parametro alla classe).
	 */
	protected String reportsRoot2;
	//fine mod. 13978

	/**
	 * Fix 4418
	 * Stringa che rappresenta il percorso di dove è installato il serverdiff (da passare come parametro alla classe).
	 */
	/* Revisions:
	 * Fix nr   Date          Owner      Description
	 *  4418    10/10/2005    Ryo        Prima versione
	 */
	protected String serverdiffRoot;

	/**Mod.2258
	 * Stringa che rappresenta il percorso di dove sono installati i reports (da passare come parametro alla classe).
	 */
	protected String reportsRoot;

	/**Mod.2967
	 * Stringa che rappresenta il nome del server FTP.
	 */
	protected String ftpServerHost;

	/**Mod.2967
	 * numero della porta del Server FTP.
	 */
	protected int ftpServerPort;

	/**Mod.2967
	 * Stringa con il nome FTP User.
	 */
	protected String ftpUserName;

	/**Mod.2967
	 * Stringa con la password Server FTP.
	 */
	protected String ftpUserPassword;

	/**Mod.1315
	 * File di log su cui scrivere il risultato dei test ai file.
	 */
	protected FileOutputStream writer;

	/**Mod.12771
	 * Nome del file di log su cui scrivere il risultato dei test ai file.
	 */
	String newName = "";

	/**Mod.1315
	 * Output per il setup.
	 */
	protected PrintWriter output;

	/**Mod.1754
	 * BufferedReader per il file con le descrizioni delle fix.
	 */
	protected BufferedReader fixDescriptionFile;

	/**Mod.1754
	 * Vettore che raccoglie le descrizioni delle fix applicate.
	 */
	protected Hashtable fixDescription = new Hashtable();

	/**Mod.1754
	 * Indicano se la copia deve essere fatta o meno.
	 */
	protected boolean copyLib = false;
	protected boolean copyWeb = false;
	protected boolean copyRpt = false; //E' la destinazione unica per tutte le versioni di CR
	protected boolean copyUser = false; //Mod.2226

	/**Mod.2258
	 * Indicano se la copia deve essere fatta o meno per la copia1 - Primrose.
	 */
	protected boolean copyServer = false;
	protected boolean copyClient = false;
	protected boolean copyReports = false;

	//Mod. 13978 inizio
	/**
	 * Indicano se la copia deve essere fatta o meno per la copia2 - Plex 610.
	 */
	protected boolean copyServer2 = false;
	protected boolean copyClient2 = false;
	protected boolean copyReports2 = false;
	//fine mod. 13978

	/**
	 * Versione di CrystalReports impostata sulle preferenze applicazione.
	 */
	char crystalVer; //Mod. 11234

	//Fix 03679 MM inizio
	/**
	 * Timestamp di inizio del processo di applicazione delle fix.
	 */
	protected Timestamp startingTimestamp;

	/**
	 * Nome della directory contenente i files specifici per la piattaforma corrente.
	 */
	protected String platformDir;

	/**
	 * Nome della directory contenente i files specifici per la il database corrente.
	 */
	protected String dbDir;

	/**
	 * Booleano che indica l'esistenza della directory contenente i files specifici per
	 * la piattaforma corrente.
	 */
	protected Boolean existsDBPlatformDir;

	/**
	 * Booleano che indica l'esistenza della directory contenente i files specifici per il database corrente.
	 */
	protected Boolean existsDBDir;

	//Mod. 13667 inizio
	/**
	 * Booleano che indica se si sono eseguiti: file di tipo TDDML, SQL, 0 ApplyMainFix
	 */
	protected boolean doneSthg = false;
	//Mod. 13667 fine

	/**
	 * Costante indicante il nome della directory contenente i files standard delle fix.
	 */
	public static final String STD_DIR = "std";
	//Fix 03679 MM fine

	//Fix 04576 MM inizio
	protected boolean hierarchic;

	public static final String HIERARCHIC_FLAG = "-H";
	//Fix 04576 MM fine
	/**
	 * Costanti simboliche che rappresentano i caratteri di riconoscimento dei
	 * parametri nel file con l'elenco delle fix da applicare.
	 */
	//...carattere per riconoscere i commenti
	public static final String COMMENT = "#";

	//...carattere separatore tra titolo della riga e valori
	public static final String SEPARATOR = ":";

	//...stringhe di riconoscimento
	public static final String APPLICATION = "Application";
	public static final String DESCRIPTION = "Description";
	public static final String UPDATE_APPL = "UpdateVRM";

	public static final String START_ADDITIONAL = "StartAdditional";	// Fix 12355 Ryo
	public static final String END_ADDITIONAL = "EndAdditional";	// Fix 12355 Ryo

	//...nome del file con l'elenco delle fix da applicare
	public static final String FILE_NAME = "FixOrder.txt";

	//...utente di therm e password di default
	public static final String DEF_USER = "ADMIN";

	//...nome della classe per testare la versione
	public static final String CLASS_VERSION_NAME =
		"com.thera.thermfw.setup.Version";

	//...MOD 170 (LP)
	public static final String APPLY_FIX_NAME =
		"com.thera.thermfw.setup.gui.ApplyFix";

	//...tipi di piattaforme disponibili
	public static final String NT = "NT";
	public static final String AS = "AS";
	public static final String LX = "LX";

	//...nome cartelle di default (Mod.1315)
	public static final String NAME_FIX_LIB = "lib";
	public static final String NAME_FIX_WEB = "websrc";
	public static final String NAME_FIX_RPT = "print";
	/**
	 * Nome della directory con i file .RPT per CrystalReport 2008 da copiare nella destinazione.
	 */
	public static final String NAME_FIX_RPT_CR2008 = "print2008";//Mod. 11234
	//Fix 04401 MM
	//  public static final String NAME_APP_WEBINF = "web-inf";
	public static final String NAME_APP_WEBINF = "WEB-INF";
	public static final String NAME_APP_WEBCLASSES = "classes";
	public static final String NAME_APP_WEBLIB = "lib";
	public static final String NAME_APP_RESOURCES = "primrose"; //Mod. 13978
	public static final String NAME_APP_REPORTS = "FINANCE"; //Mod. 13978
	public static final String NAME_APP_REPORTS_BIS = "cr2008"; //Mod. 14621

	//...nome cartelle di default1 (Mod.2258)
	public static final String NAME_FIX_SERVER = "primrose" + File.separator + "server";
	public static final String NAME_FIX_CLIENT = "primrose" + File.separator + "client";
	public static final String NAME_FIX_REPORTS = "primrose" + File.separator + "reports";

	//Mod. 13978 inizio
	//...nome cartelle di default2 dove trovo i file da copiare nella destinazione (Mod.13978)
	public static final String NAME_FIX_SERVER_2 = "primrose" + File.separator + "server610";
	public static final String NAME_FIX_SERVERDIFF_2 = "primrose" + File.separator + "serverdiff610";
	public static final String NAME_FIX_CLIENT_2 = "primrose" + File.separator + "client610";
	public static final String NAME_FIX_REPORTS_2 = "primrose" + File.separator + "reports";
	public static final String NAME_FIX_REPORTS_CR2008_2 = "primrose" + File.separator + "reports2008";

	public static final String NAME_FIX_WEBSRC_2 = "primrose" + File.separator + "websrc";
	public static final String NAME_FIX_CLIENTWEB_2 = "primrose" + File.separator + "clientweb";
	public static final String NAME_FIX_CLIENTWEBDIFF_2 = "primrose" + File.separator + "clientwebdiff";
	public static final String NAME_FIX_RESOURCES_2 = "primrose" + File.separator + "resources";
	//fine mod. 13978
	/**
	 * Stringa che rappresenta la direcrory serverdiff nella struttura della fix.
	 */
	/* Revisions:
	 * Fix nr   Date          Owner      Description
	 *  4418    11/10/2005    Ryo        Prima versione
	 */
	public static final String NAME_FIX_SERVER_DIFF = "primrose" + File.separator + "serverdiff"; // Fix 4418 Ryo

	public static final String NAME_BACKUPS_DIR = "backups"; //Mod.2967

	//Mod.2662
	public static final String NODB_FILE = "NODB.SQL";

	// Mod. 905 - ini
	// Flag UserName
	public static final String USER_FLAG = "-U";

	// Flag Password
	public static final String PWD_FLAG = "-P";

	// Flag Platform Type
	public static final String PTYPE_FLAG = "-PT";

	// Flag Applications
	public static final String APPS_FLAG = "-A";

	// Flag CopyFile - Mod.1315
	public static final String COPY_FLAG = "-CF";

	// Flag CopyFile - Mod.2258
	public static final String COPY_FLAG1 = "-CF1";

	// Mod.13978 inizio
	// Flag CopyFile per plex610
	public static final String COPY_FLAG2 = "-CF2";

	/**
	 * Flag che indica se l'eventuale flag -CF2 è presente ma non ha tutti i parametri necessari
	 * mi serve per "ricordarmi" quando è aperto il file di log e segnalare che ho ignorato il flag -CF2
	 * true = mancano dei parametri: ignoro completamente il -CF2 e lo segnalo
	 * false = o il parametro non c'è o è completo
	 */
	boolean incompleteCF2Param = false;

	/**
	 * Flag che indica se l'eventuale flag -CF2 è presente ma non è presente il flag -CF1
	 * true = manca -CF1: ignoro completamente il -CF2 e lo segnalo
	 * false = c'è -CF1 (o non cè neppure il -CF2)
	 */
	boolean CF2MissesCF = false;
	//fine mod. 13978

	// Flag OnlyCopyFile - Mod.2256
	public static final String ONLY_COPY_FLAG = "-OCF";

	// Flag Directory File Utente - Mod.2226
	public static final String USR_DIR_FLAG = "-D";

	// Flag Modalità di inserimento record - Mod.2295
	public static final String INSERT_MODE = "-MI";

	// Flag Copia FTP - Mod.2967
	public static final String FTP_COPY_FLAG = "-FTP";
	public static final String BACKUPS_FLAG = "-BKPS";

	// Fix 3168
	public static final String COMMIT_FLAG = "-COMMIT";
	//Fine fix 3168

	// Fix 3418
	public static final String UNIQUE_FIX_NUMBER = "-UFN";
	// Fine fix 3418

	// Array con i Parametri di tipo non Path - Mod.2967
	//  public static final String[] NO_PATH_PARAMS = new String[] {
	//      USER_FLAG, PWD_FLAG, APPS_FLAG, PTYPE_FLAG, USR_DIR_FLAG, INSERT_MODE};
	//Fix 04576 MM
	public static final String[] NO_PATH_PARAMS = new String[] {
		USER_FLAG, PWD_FLAG, APPS_FLAG, PTYPE_FLAG, USR_DIR_FLAG, INSERT_MODE,
		COPY_FLAG, COPY_FLAG1, COPY_FLAG2, ONLY_COPY_FLAG, FTP_COPY_FLAG, BACKUPS_FLAG,
		COMMIT_FLAG, UNIQUE_FIX_NUMBER, HIERARCHIC_FLAG}; //Mod. 13978

	// File con le Applications disponibili (installate)
	public static final String APPS_FILE = "applications.txt";

	// All opzione del flag -A
	public static final String ALL_NAME = "all";

	// Array Applications Setup
	public static Vector apps = new Vector();

	// Array Applications disponibili (quelle del file applications.txt)
	public static Vector avaApps = new Vector();

	// Opzioni del flag -MI - Mod. 2295
	//   IO= insert only
	//   IU= insert, ma se riga duplicata provo update (default)
	public static final String INSERT_ONLY_MODE = "IO";
	public static final String INSERT_UPDATE_MODE = "IU";

	//fine mod. 2295

	// Mod. 2149 Vettore istruzioni viste
	//07935 - DF Lista di DictView
	// protected Vector vViews = new Vector();

	// File con i nomi degli schemi per le viste da rigenerare
	public static final String VIEWSCHEMA_FILE = "viewschemas.txt";
	//07935 - DF Lista di DictView
	//public Vector vViewSchemas = new Vector();

	// BufferedReader per il file con gli schemi delle viste.
	// 07935 - DF
	// protected BufferedReader viewSchemasFile;

	// Indica se copiare o meno i file della fix
	protected static boolean copyFiles = false;

	// Indica se copiare o meno i file della fix - Mod.2258
	protected static boolean copyFiles1 = false;

	// Indica se copiare o meno i file della fix per supporto Plex 610- Mod.13978
	protected static boolean copyFiles2 = false;

	// Indica se si è impostato la sola copia dei file - Mod.2256
	protected static boolean onlyCopyFiles = false;

	// Indica se la copia dei file deve avvenire tramite FTP - Mod.2967
	protected static boolean ftpCopyFiles = false;

	// Indica se fare il backups dei files - Mod.2967
	protected static boolean backupsFiles = false;

	//Flags creazione backups - Mod.2967
	protected static String[] genBackupDir = new String[] {"Y", "Y", "Y", "Y", "Y", "Y", "Y", "Y", "Y"};

	// Indica se fare o meno insert/update
	protected static boolean tryUpdate = true;

	// Fix 3168
	protected static boolean commitEachFix = false;
	// Fine fix 3168

	// Fix 3418
	protected static boolean uniqueFixNumber = false;
	// Fine fix 3418

 //Mod. 14328// private List viewsExceptions = new ArrayList();	// Fix 12319 Ryo
	private HashMap viewsExceptionsMap = new HashMap();	// Fix 14328

	/**
	 * File di risorse.
	 */
	//Mod.9634//public static final String SETUP_RES =
	//Mod.9634//	"com.thera.thermfw.setup.resources.Setup";

	//Mod. 16172 inizio
	/*
	 * Nomi dei .jar e dll da testare per verificare che ammistrazione e controllo sia spenta.
	 */
	public static final String fileToCheck1 = "BBANAGR01"; //path in: "PrimoroseRoot"
	public static final String fileToCheck2 = "BBANAGR01"; //path in: "PrimoroseRoot610"
	public static final String jarExt = ".jar"; //path in: "PrimoroseRoot610"
	public static final String fileToCheck3 = "ob510lc"; // path in: "PrimoroseRootClient"
	public static final String fileToCheck4 = "ob600lc"; // pathin: "PrimroseRootClient610"
	public static final String dllExt = ".dll"; //path in: "PrimoroseRoot610"
	/*
	 * Suffisso aggiunto per rinominare i file da testare.
	 */
	public static final String suffixToCheck = "_XTH";
	//fine mod. 16172

	// 6943 DM inizio
	protected static CachedStatement cvGetOracleDDLForViewStmt;
	// 6943 DM fine
	
	//28381 inizio
	protected boolean standalone = true;

	public void setStandalone(boolean standalone) {
		this.standalone = standalone;
	}
	
	public boolean isStandalone() {
		return this.standalone;
	}
	//28381 fine
	
	/**
	 * Metodo per settare il tipo di piattaforma.
	 * @param String platType.
	 */
	public void setPlatformType(String platType) {
		platformType = platType;
	}

	/**
	 * Metodo che restituisce il tipo di piattaforma.
	 * @return string il tipo di piattaforma.
	 */
	public String getPlatformType() {
		return platformType;
	}

	//...MOD 170 (LP)...inizio
	/**
	 * Metodo per settare il tipo di piattaforma.
	 * @param String platType.
	 */
	public void setFixRoot(String fRoot) {
		fixRoot = fRoot;
	}

	/**
	 * Metodo che restituisce il tipo di piattaforma.
	 * @return string il tipo di piattaforma.
	 */
	public String getFixRoot() {
		return fixRoot;
	}

	//...MOD 170 (LP)...fine

	//...MOD 1315 (IT)...inizio
	/**
	 * Metodo per settare libRoot.
	 * @param String libRoot.
	 */
	public void setLibRoot(String lRoot) {
		libRoot = lRoot;
	}

	/**
	 * Metodo che restituisce libRoot.
	 * @return string.
	 */
	public String getLibRoot() {
		return libRoot;
	}

	/**
	 * Metodo per settare webRoot.
	 * @param String webRoot.
	 */
	public void setWebRoot(String wRoot) {
		webRoot = wRoot;
	}

	/**
	 * Metodo che restituisce webRoot.
	 * @return string.
	 */
	public String getWebRoot() {
		return webRoot;
	}

	/**
	 * Metodo per settare rptRoot.
	 * @param String rptRoot.
	 */
	public void setRptRoot(String rRoot) {
		rptRoot = rRoot;
	}

	/**
	 * Metodo che restituisce rptRoot.
	 * @return string.
	 */
	public String getRptRoot() {
		return rptRoot;
	}

	//...MOD 1315 (IT)...fine

	//...MOD 2258 (IT)...inizio
	/**
	 * Metodo per settare serverRoot.
	 * @param String serverRoot.
	 */
	public void setServerRoot(String sRoot) {
		serverRoot = sRoot;
	}

	/**
	 * Metodo che restituisce serverRoot.
	 * @return string.
	 */
	public String getServerRoot() {
		return serverRoot;
	}

	/**
	 * Metodo per settare clientRoot.
	 * @param String clientRoot.
	 */
	public void setClientRoot(String cRoot) {
		clientRoot = cRoot;
	}

	/**
	 * Metodo che restituisce clientRoot.
	 * @return string.
	 */
	public String getClientRoot() {
		return clientRoot;
	}

	/**
	 * Metodo per settare reportsRoot.
	 * @param String reportsRoot.
	 */
	public void setReportsRoot(String rRoot) {
		reportsRoot = rRoot;
	}

	/**
	 * Metodo che restituisce reportsRoot.
	 * @return string.
	 */
	public String getReportsRoot() {
		return reportsRoot;
	}

	//...MOD 2258 (IT)...fine

	//Mod. 13978 inizio
	/**
	 * Metodo per settare serverRoot2.
	 * @param String serverRoot.
	 */
	public void setServerRoot2(String sRoot) {
		serverRoot2 = sRoot;
	}

	/**
	 * Metodo che restituisce serverRoot2.
	 * @return string.
	 */
	public String getServerRoot2() {
		return serverRoot2;
	}

	/**
	 * Metodo per settare clientRoot2.
	 * @param String clientRoot2.
	 */
	public void setClientRoot2(String cRoot) {
		clientRoot2 = cRoot;
	}

	/**
	 * Metodo che restituisce clientRoot2.
	 * @return string.
	 */
	public String getClientRoot2() {
		return clientRoot2;
	}

	/**
	 * Metodo per settare reportsRoot2.
	 * @param String reportsRoot.
	 */
	public void setReportsRoot2(String rRoot) {
		reportsRoot2 = rRoot;
	}

	/**
	 * Metodo che restituisce reportsRoot2.
	 * @return string.
	 */
	public String getReportsRoot2() {
		return reportsRoot2;
	}
	//fine mod 13978

	//Mod.2226 - ini
	/**
	 * Metodo per settare userRoot.
	 * @param String userRoot.
	 */
	public void setUserRoot(String uRoot) {
		userRoot = uRoot;
	}

	/**
	 * Metodo che restituisce userRoot.
	 * @return string.
	 */
	public String getUserRoot() {
		return userRoot;
	}

	/**
	 * Metodo per settare nameRoot.
	 * @param String nUserDir.
	 */
	public void setNameUserDir(String nUserDir) {
		nameUserDir = nUserDir;
	}

	/**
	 * Metodo che restituisce userRoot.
	 * @return string.
	 */
	public String getNameUserDir() {
		return nameUserDir;
	}

	//Mod.2226 - fin

	//Mod.2967 - inizio
	/**
	 * Metodo per settare ftpServerHost.
	 * @param String ftpHost.
	 */
	public void setFtpServerHost(String ftpHost) {
		ftpServerHost = ftpHost;
	}

	/**
	 * Metodo che restituisce FtpServerHost.
	 * @return string.
	 */
	public String getFtpServerHost() {
		return ftpServerHost;
	}

	/**
	 * Metodo per settare ftpServerPort.
	 * @param int ftpPort.
	 */
	public void setFtpServerPort(int ftpPort) {
		ftpServerPort = ftpPort;
	}

	/**
	 * Metodo che restituisce ftpServerPort.
	 * @return int.
	 */
	public int getFtpServerPort() {
		return ftpServerPort;
	}

	/**
	 * Metodo per settare ftpUserName.
	 * @param String ftpUserName.
	 */
	public void setFtpUserName(String ftpUserName) {
		this.ftpUserName = ftpUserName;
	}

	/**
	 * Metodo che restituisce ftpUserName.
	 * @return string.
	 */
	public String getFtpUserName() {
		return ftpUserName;
	}

	/**
	 * Metodo per settare ftpUserPassword.
	 * @param String ftpUserPassword.
	 */
	public void setFtpUserPassword(String ftpUserPassword) {
		this.ftpUserPassword = ftpUserPassword;
	}

	/**
	 * Metodo che restituisce ftpUserPassword.
	 * @return string.
	 */
	public String getFtpUserPassword() {
		return ftpUserPassword;
	}

	//Mod.2967 - fine

	// ANUBIAN CODE ->

	private String read;
	private String additionalRead;
	private boolean additionalActived;

	/**
	 * Formatto i valori iniziali per l'inizio dell'applicazione delle fix
	 */
	/* Revisions:
	 * Fix #    Date          Owner      Description
	 * 12355     9/ 4/2010    Ryo        Prima versione
	 */
	private void formatApplication(String read)
	{
		// Svuoto i vettori nel caso ci fossero applicazioni diverse nello stesso file.
		fixApplicated.clear();
		fixApplicatedRoot.clear();
		fixVector.clear();
		oldFixVector.clear();
		String[] values = extractApplicationValues(read);
		application = values[0];
		module = values[1];
		versionStr = values[2];
		releaseStr = values[3];
		modificationStr = values[4];
		temp = read;
	}

	String temp;

	/**
	 * Formatto i valori delle descrizioni
	 */
	/* Revisions:
	 * Fix #    Date          Owner      Description
	 * 12355     9/ 4/2010    Ryo        Prima versione
	 */
	private void formatDescription(String read)
	{
		String[] values = extractApplicationValues(read);
		applDescription = values[0];
		modDescription = values[1];
	}

	/* Revisions:
	 * Fix #    Date          Owner      Description
	 * 12355     9/ 4/2010    Ryo        Prima versione
	 */
	private void fixSave(SoftwareLevel swLev, int i) throws Exception
	{
		int nr = ((Integer) fixApplicated.elementAt(i)).intValue();
		SoftwareFix swf = new SoftwareFix();
		swf.setSoftwareLevel(swLev);
		swf.setSoftwareFix(nr);
		String description = (String) fixDescription.get(new Integer(nr));
		swf.setFixDescription(description);
		swf.save();
		AdvanceFixesManager.closeAdvanceFixesWaitingFor(nr);
	}

	/* Revisions:
	 * Fix #    Date          Owner      Description
	 * 12355     9/ 4/2010    Ryo        Prima versione
	 */
	private boolean copyFixFiles() throws Exception
	{
		boolean righto = true;
		System.out.println("");
		for(int i = 0; i < fixApplicated.size(); i++)
		{
			int nr = ((Integer) fixApplicated.elementAt(i)).intValue();
			String mask = "00000" + String.valueOf(nr);
			String nFix = mask.substring(mask.length() - 5, mask.length());
			String pathFix = (String) fixApplicatedRoot.elementAt(i);
			if(copyFixFile(nFix, pathFix)){
				System.out.println(ResourceLoader.getString(SETUP_RES, "OkCopyFile", new String[] {nFix}));
			}
			else
			{
				rollback();
				output.println(ResourceLoader.getString(SETUP_RES, "ErrorString"));//Mod. 12771
				output.println(ResourceLoader.getString(SETUP_RES, "ErrorsCopyFile", new String[] {nFix, FixRunner.FixFileReportName})); //Mod. 13666//newName}));
				//Mod.13666//closeLogFile();
				System.out.println(ResourceLoader.getString(SETUP_RES, "ErrorsCopyFile", new String[] {nFix, FixRunner.FixFileReportName})); //Mod. 13666//newName}));
				righto = false;
			}
		}
		return righto;
	}

	/* Revisions:
	 * Fix #    Date          Owner      Description
	 * 12355     9/ 4/2010    Ryo        Prima versione
	 */
	private boolean updateParams(SoftwareLevel swLev, String ver, String rel, String mod) throws Exception
	{
		boolean righto = true;

		// Indico quante fix (su quante) sono state applicate e aggiorno i parametri
		System.out.println(" "); //Mod. 13978
		System.out.println(ResourceLoader.getString(SETUP_RES, "Update", new String[] {"" + fixApplicated.size(), "" + fixVector.size()}));

		// Inserisco un nuovo livello di software (salvo e faccio commit)
		swLev = new SoftwareLevel();
		if(ver != null && !ver.equals("") && rel != null && !rel.equals("") && mod != null && !mod.equals(""))
		{
			swLev.setKey(application + KeyHelper.KEY_SEPARATOR + module + KeyHelper.KEY_SEPARATOR + ver + KeyHelper.KEY_SEPARATOR + rel + KeyHelper.KEY_SEPARATOR + mod);
		}
		else
		{
			System.out.println(ResourceLoader.getString(SETUP_RES, "VRMNull", new String[] {ver, rel, mod}));
			closeLogFile();
			righto = false;
		}
		if(righto)
		{
			if(!swLev.retrieve())
			{
				swLev.save();
			}
			commit();
			System.out.println(ResourceLoader.getString(SETUP_RES, "VRMUpdate", new String[] {versionStr, releaseStr, modificationStr, ver, rel, mod}));
			//Mod. 14420 inizio
			System.out.println(" ");
			List sortedFixVector = new ArrayList(oldFixVector);
			Collections.sort(sortedFixVector);
			String fixList = "";
			int countFix = 0;
			System.out.println(ResourceLoader.getString(SETUP_RES, "AppliedFixes"));
			for (int i = 0; i < sortedFixVector.size(); i++) {
				if (countFix > MAX_APPL_FIX_X_LINE) {
					System.out.println(fixList);
					fixList = "";
					countFix = 0;
				}
				if (fixList.length() > 0)
					fixList += ",  ";
				fixList += sortedFixVector.get(i).toString();
				countFix++;
			}
			System.out.println(fixList);
			/*
			Object[] it = oldFixVector.toArray();
			for (int i = 0; i < it.length; i++)
			{
				System.out.println(ResourceLoader.getString(SETUP_RES, "FixAlreadyRun", new String[] {it[i].toString()}));
			}
			*/
			//fine mod. 14420
		}
		return righto;
	}

	/**
	 * Imposto i nuovi valori riguardanti il livello di fix raggiunto dall'applicazione
	 */
	/* Revisions:
	 * Fix #    Date          Owner      Description
	 * 12355     9/ 4/2010    Ryo        Prima versione
	 */
	//private boolean updateAppl(String read) throws Exception  //Fix 28999 PM
	protected boolean updateAppl(String read) throws Exception //Fix 28999 PM
	{
		boolean righto = true;
		fixNrStr = null;
		String[] values = extractApplicationValues(read);
		String ver = values[0];
		String rel = values[1];
		String mod = values[2];
		SoftwareLevel swLev = new SoftwareLevel();
		if(!onlyCopyFiles)
		{
			swLev = createSwLevel();
			// Prima di aggiornare i valori dell'applicazione, salvo le fix (solo se non ho già salvato fix singolarmente)
			if(!commitEachFix)
			{
				for(int i = 0; i < fixApplicated.size(); i++)
				{
					fixSave(swLev, i);
				}
			}
			commit();
		}
		// Lo faccio solo se non ho già salvato fix singolarmente
		if(!commitEachFix)
		{
			if(copyFiles || copyFiles1 || copyFiles2) //Mod. 13978
			{
				righto = copyFixFiles();
			}
		}
		if(righto)
		{
			if(!onlyCopyFiles)
			{
				righto = updateParams(swLev, ver, rel, mod);
			}
			else
			{
				// Indico quante fix (su quante) sono state riapplicate
				System.out.println("ONLY COPY: " + ResourceLoader.getString(SETUP_RES, "Update", new String[] {"" + fixApplicated.size(), "" + fixVector.size()}));
				//Fix 33557 PM >
//				if (fixApplicated.size() > 0)
//				{
//					boolean primo = true;
//					StringBuffer update = new StringBuffer();
//					update.append("UPDATE ").append(SystemParam.getFrameworkSchema()).append("SOFTWARE_FIX SET TIMESTAMP = ? WHERE SOFTWARE_FIX IN (");
//					for (Iterator<Integer> iterator = fixApplicated.iterator(); iterator.hasNext();) 
//					{
//						Integer fix = (Integer) iterator.next();
//						if (primo)
//						{
//							update.append(fix);
//							primo = false;
//						}
//						else 
//							update.append(",").append(fix);
//						
//					}
//					update.append(")");
//					CachedStatement upd = new CachedStatement(update.toString());
//					upd.getStatement().setTimestamp(1, TimeUtils.getCurrentTimestamp());
//					upd.executeUpdate();
//				}
				
				if (isAggiornaTimestampFixCopyOnlyFile() && fixApplicated.size() > 0)
					aggiornaTimestampFixCopyOnlyFile(fixApplicated);
				//Fix 33557 PM <
				
			}
			com.thera.thermfw.log.LogTask.startLogTaskForced("SoftwareFix", onlyCopyFiles ? "UPDATE" : "NEW", true, onlyCopyFiles ? "CopyOnlyFile" : "");//33045
		}
		return righto;
	}
	
	
	//Fix 33557 PM >
	protected void aggiornaTimestampFixCopyOnlyFile(List fixApplicated) throws SQLException
	{
		boolean primo = true;
		StringBuffer update = new StringBuffer();
		update.append("UPDATE ").append(SystemParam.getFrameworkSchema()).append("SOFTWARE_FIX SET TIMESTAMP = ? WHERE SOFTWARE_FIX IN (");
		for (Iterator<Integer> iterator = fixApplicated.iterator(); iterator.hasNext();) 
		{
			Integer fix = (Integer) iterator.next();
			if (primo)
			{
				update.append(fix);
				primo = false;
			}
			else 
				update.append(",").append(fix);
			
		}
		update.append(")");
		CachedStatement upd = new CachedStatement(update.toString());
		upd.getStatement().setTimestamp(1, TimeUtils.getCurrentTimestamp());
		upd.executeUpdate();
	}
	//Fix 33557 PM <

	/**
	 * Classe che rappresenta lo stato di una fix
	 */
	/* Revisions:
	 * Fix #    Date          Owner      Description
	 * 12355     9/ 4/2010    Ryo        Prima versione
	 */
	private final class VerRelModFix
	{

		public VerRelModFix(boolean fixCommitEveryRow, boolean fixIsAlreadyApplied, short version, short release, short modification, int fixNr)
		{
			this.fixIsAlreadyApplied = fixIsAlreadyApplied;
			this.fixCommitEveryRow = fixCommitEveryRow;
			this.version = version;
			this.release = release;
			this.modification = modification;
			this.fixNr = fixNr;
		}

		// Booleano che indica se la fix è già stata applicata
		public boolean fixIsAlreadyApplied;

		// Booleano che indica se, applicando la fix, deve essere fatto commit ad ogni riga
		public boolean fixCommitEveryRow;

		// Numeri che rappresentano la versione, la release, la modifica e il numero della fix da applicare
		public short version;
		public short release;
		public short modification;
		public int fixNr;

	}

	/* Revisions:
	 * Fix #    Date          Owner      Description
	 * 12355     9/ 4/2010    Ryo        Prima versione
	 */
	//private boolean commitAndCopyEachFix(VerRelModFix vrmf) throws Exception //35629
	protected boolean commitAndCopyEachFix(VerRelModFix vrmf) throws Exception //35629
	{
		boolean righto = true;
		//32242 inizio 
	    /*	
	    SoftwareLevel swLev = createSwLevel();
		SoftwareFix swf = new SoftwareFix();
		swf.setSoftwareLevel(swLev);
		swf.setSoftwareFix(vrmf.fixNr);
		String description = (String) fixDescription.get(new Integer(vrmf.fixNr));
		swf.setFixDescription(description);
		swf.save();
		AdvanceFixesManager.closeAdvanceFixesWaitingFor(vrmf.fixNr);
		commit();
		*/
		//32242 fine 
		String mask = "00000" + String.valueOf(vrmf.fixNr);
		String nFix = mask.substring(mask.length() - 5, mask.length());
		if(!copyFixFile(nFix, pathForCopy))
		{
		    //rollback(); //32242
			output.println(ResourceLoader.getString(SETUP_RES, "ErrorString"));//Mod. 12771
			output.println(ResourceLoader.getString(SETUP_RES, "ErrorsCopyFile", new String[] {nFix, FixRunner.FixFileReportName})); //Mod. 13666//newName}));
			closeLogFile();
			System.out.println(ResourceLoader.getString(SETUP_RES, "ErrorsCopyFile", new String[] {nFix, FixRunner.FixFileReportName})); //Mod. 13666//newName}));
			righto = false;
		}
		//32242 inizio
		else {
			SoftwareLevel swLev = createSwLevel();
			SoftwareFix swf = new SoftwareFix();
			swf.setSoftwareLevel(swLev);
			swf.setSoftwareFix(vrmf.fixNr);
			String description = (String) fixDescription.get(new Integer(vrmf.fixNr));
			swf.setFixDescription(description);
			if(swf.save() < 0) {
				rollback();
				righto = false;
			}
			else if(AdvanceFixesManager.closeAdvanceFixesWaitingFor(vrmf.fixNr) < 0){
				rollback();
				righto = false;
			}
			else {
				commit();
			}
		}
		//32242 fine
		return righto;
	}

	/* Revisions:
	 * Fix #    Date          Owner      Description
	 * 12355     9/ 4/2010    Ryo        Prima versione
	 */
	private boolean applyTheFix(VerRelModFix vrmf) throws Exception
	{
		boolean righto = true;

		// Istanzio la classe passata come primo parametro tramite reflection
		Class fixApplyClass = Class.forName(className);
		//Mod. 13667 inizio//La prima volta che faccio qualcosa (cioè applicare una classe oppure un file != NODB.SQL) metto il flag a true
		//Mod. 15040 Corretto la stringa ApplyClassMainFixWithParams
		if (!doneSthg &&
				(fixApplyClass.getName().endsWith("ApplyClassMainFix") ||
				 fixApplyClass.getName().endsWith("ApplyClassMainFixWithParams") ||
				 (fixApplyClass.getName().endsWith("ApplyFix") && !parameters[3].endsWith("NODB.SQL")) ) )
		{
			doneSthg = true;
		}
		//fine mod. 13667
		// Imposto un oggetto (istanza della classe)
		Object fixApplyObj = fixApplyClass.newInstance();
		// Tramite reflection chiamo il metodo setParameters sull'oggetto fixApplyObj
		Method fixApplyMethod = fixApplyClass.getMethod("setParameters", new Class[] {String[].class});
		fixApplyMethod.invoke(fixApplyObj, new Object[] {parameters});
		// Tramite reflection chiamo il metodo setFixFileReport sull'oggetto fixApplyObj - Mod.1315
		fixApplyMethod = fixApplyClass.getMethod("setFixFileReport", new Class[] {PrintWriter.class});
		fixApplyMethod.invoke(fixApplyObj, new Object[] {output});
		// Tramite reflection chiamo il metodo run sull'oggetto fixApplyObj
		fixApplyMethod = fixApplyClass.getMethod("run", new Class[] {});
		if(ConnectionManager.getCurrentDatabase() instanceof OracleDatabase)
		{
			OracleCommandHistory.setupOrContinue(vrmf.fixNr);
		}
		ok = ((Boolean)fixApplyMethod.invoke(fixApplyObj, new Object[]{})).booleanValue();
		if(ok && vrmf.fixCommitEveryRow)
		{
			commit();
//			System.out.println("Commit eseguito sulla singola riga");
		}

		// Leggo la riga successiva per sapere lo prossimo numero di fix.
		// Se la fix successiva ha lo stesso numero di questa, non salvo ancora la fix, la salvo solo alla fine (altrimenti non la applico più!)
		read = fixFile.readLine();
		//Mod. 16317//if(read.equals(""))
		if(read.equals("") || read.startsWith(COMMENT)) //Mod. 16317
		{
			read = fixFile.readLine();
		}

		// Se la fix è stata applicata correttamente e totalmente salvo un nuovo record nella tabella delle fix e faccio commit
		if(ok)
		{
			TagProcessor tagProcessor = new TagProcessor(null);
			String newLine = tagProcessor.replaceTags(read);
			if(!read.startsWith(fixNrStr) || !tagProcessor.isRunnable())
			{
				fixApplicated.addElement(new Integer(vrmf.fixNr));
				fixApplicatedRoot.addElement(pathForCopy);
				if(endApplyFix())
				{
					// Se richiesto eseguo copia e commit ad ogni fix
					if(commitEachFix)
					{
						righto = commitAndCopyEachFix(vrmf);
					}
				}
				else
				{
					rollback();
					System.out.println(ResourceLoader.getString(SETUP_RES, "ErrorsGenerated" , new String[]{fixNrStr}));
					closeLogFile();
					righto = false;
				}
			}
		}
		else
		{
			// Altrimenti esco dal metodo e faccio rollback - Errore Fix Database.
			rollback();
			if(additionalActived)
			{
				System.out.println("\n" + ResourceLoader.getString(SETUP_RES, "AdditionalFixError", new String[] {fixNrStr}));
				closeFix();
			}
			else
			{
				System.out.println("\n" + ResourceLoader.getString(SETUP_RES, "ErrorsGenerated", new String[] {fixNrStr}));
			}
			closeLogFile();
			righto = false;
		}
		return righto;
	}

	/**
	 * Provo ad applicare la fix
	 */
	/* Revisions:
	 * Fix #    Date          Owner      Description
	 * 12355     9/ 4/2010    Ryo        Prima versione
	 */
	private boolean try2ApplyTheFix(boolean fixCommitEveryRow) throws Exception
	{
		boolean righto = true;

		// Inizializzo vrmf
		VerRelModFix vrmf = new VerRelModFix(false, false, (short)0, (short)0 , (short)0, 0);

		// Controllo che nessuno dei parametri sia nullo
		if(application == null || application.equals("") || module == null || module.equals("") || versionStr == null || versionStr.equals("") || releaseStr == null || releaseStr.equals("") || modificationStr == null || modificationStr.equals(""))
		{
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoCorrectParameters"));
			closeLogFile();
			righto = false;
		}
		else
		{
			// Se il numero di fix è nuovo (non è presente nel vettore) lo aggiungo
			fixVector.add(fixNrStr);

			// Valorizzo vrmf
			vrmf.fixCommitEveryRow = fixCommitEveryRow;
			vrmf.version = new Short(versionStr).shortValue();
			vrmf.release = new Short(releaseStr).shortValue();
			vrmf.modification = new Short(modificationStr).shortValue();
			vrmf.fixNr = new Integer(fixNrStr).intValue();

			// Controllo se la fix è già stata applicata
			Vector swFix = fixIsApplied(application, module, vrmf.version, vrmf.release, vrmf.modification, vrmf.fixNr);
			// Se esiste già una fix con questa chiave ed è la prima volta che la trovo nel file, vuol dire che la fix è già stata applicata
			if(swFix.size() != 0)
			{
				vrmf.fixIsAlreadyApplied = true;
			}
			else
			{
				vrmf.fixIsAlreadyApplied = false;
			}

			// Se la fix non e mai stata applicata, la applico!
			if(!vrmf.fixIsAlreadyApplied && !onlyCopyFiles)
			{
				righto = applyTheFix(vrmf);
			}

			// Se la fix invece era già stata applicata passo alla riga successiva
			else
			{
				read = fixFile.readLine();
				if(onlyCopyFiles)
				{
					if(read.equals(""))
					{
						read = fixFile.readLine();
					}
					TagProcessor tagProcessor = new TagProcessor(null);
					if(!read.startsWith(fixNrStr) || !tagProcessor.isRunnable())
					{
						fixApplicated.addElement(new Integer(vrmf.fixNr));
						fixApplicatedRoot.addElement(pathForCopy);
					}
				}
				else
				{
					oldFixVector.add(fixNrStr);
				}
			}
		}
		return righto;
	}

	/**
	 *
	 */
	/* Revisions:
	 * Fix #    Date          Owner      Description
	 * 12355    14/ 4/2010    Ryo        Prima stesura
	 */
	private boolean processFix(boolean commitEveryRow) throws Exception
	{
		boolean righto = true;

		// Vettore che contiene i parametri
		Vector parVect = new Vector();

		// Controllo se la fix da applicare o meno per l'applicazione corrente
		TagProcessor tagProcessor = new TagProcessor(null);
		String newLine = tagProcessor.replaceTags(read);
		if(!tagProcessor.isRunnable())
		{
			read = fixFile.readLine();
			righto = false;
		}
		if(righto)
		{
			read = newLine.trim();

			// Stringa con tutti i valori della fix che suddivido in singole stringhe che aggiungo al vettore parVect.
			String value = extractFixValues(read);
			StringTokenizer valueTokenizer = new StringTokenizer(value, ",");
			int i = 0;
			while(valueTokenizer.hasMoreTokens())
			{
				String resValue = valueTokenizer.nextToken();
				parVect.addElement(resValue);
				i++;
			}

			// Il nome della classe è il primo parametro
			className = (String) parVect.elementAt(0);

			// Imposto l'Array si stringhe da passare alla classe
			parameters = new String[parVect.size() + NUM_FIX_PARAMS]; //Mod. 2295

			// I primi tre parametri sono utente di therm, la sua password  e il tipo di db
			parameters[0] = Security.getCurrentUser().getId();
			parameters[1] = Security.getCurrentUser().getPwd();
			parameters[2] = platformType;

			if(className.equalsIgnoreCase(APPLY_FIX_NAME))
			{
				if(hierarchic)
				{
					parameters[3] = findFixFile(fixRoot, (String) parVect.elementAt(1));
				}
				else
				{
					parameters[3] = fixRoot + File.separator + (String) parVect.elementAt(1);
				}
			}
			else
			{
				parameters[3] = (String) parVect.elementAt(1);
			}
			String mask = "00000" + String.valueOf(fixNrStr);
			String nFix = mask.substring(mask.length() - 5, mask.length());
			if(hierarchic)
			{
				pathForCopy = fixRoot + File.separator + STD_DIR + File.separator + nFix;
			}
			else
			{
				pathForCopy = parameters[3].substring(0, parameters[3].lastIndexOf(nFix) + 5);
			}
			if(parameters[3].indexOf(NODB_FILE) != -1)
			{
				if(className.equalsIgnoreCase(APPLY_FIX_NAME))
				{
					if(hierarchic)
					{
						parameters[3] = fixRoot + File.separator + FixFileProcessor.STD_DIR + File.separator + NODB_FILE;
					}
					else
					{
						parameters[3] = fixRoot + File.separator + NODB_FILE;
					}
				}
				else
				{
					parameters[3] = NODB_FILE;
				}
			}
			parameters[4] = (new Boolean(tryUpdate)).toString();

			if(NUM_FIX_PARAMS == 7)
			{
				parameters[5] = getFtpServerHost();
				parameters[6] = String.valueOf(getFtpServerPort());
				parameters[7] = getFtpUserName();
				parameters[8] = getFtpUserPassword();
			}
			// Non so quanti altri parametri ci siano, quindi faccio un ciclo (con j parto da 2 perchè gli elementi in 0 e 1 li ho già considerati!)
			for(int j = 2; j < parVect.size(); j++)
			{
				String p1 = (String) parVect.elementAt(j);
				if(p1.length() == 9 && p1.substring(0, 8).equalsIgnoreCase("PRIMROSE"))
				{
					parameters[j + NUM_FIX_PARAMS] = p1 + PersistentObject.KEY_SEPARATOR + getClientRoot();
					//Fix. 14621 inizio: se c'è -CF2 imposto anche i dati per aggiornare Primrose.ini x 610
					if (copyFiles2)
						parameters[j + NUM_FIX_PARAMS] = parameters[j + NUM_FIX_PARAMS]  +
																						 PersistentObject.KEY_SEPARATOR + getClientRoot2();
					//fine mod. 14621
				}
				else
				{
					parameters[j + NUM_FIX_PARAMS] = p1;
				}
			}
		}
		return righto;
	}

	/**
	 * Metodo che legge riga per riga il file con l'elenco delle fix da applicare.
	 * @return boolean true se tutte le fix sono state applicate correttamente e i
	 * parametri del file erano tutti corretti.
	 */
	/* Revisions:
	 * Fix #    Date          Owner      Description
	 *  5116     2/ 3/2006    Ryo        Integrata la fix 4897 di Panthera (PM)
	 * 12355     9/ 4/2010    Ryo        Sistema per l'applicazione di fix speciali, tipicamente per caricamenti di alti volumi di dati, dopo la commit del cumulativo
	 */
	public boolean process()
	{
		boolean righto = true;
		try
		{
			// Carico i vettori per la ricerca dei Tag delle applicazioni
			TagProcessor.loadAvaApplicationsTags(avaApps);
			TagProcessor.loadApplicationsTags(apps);

			// Cerco il file nel classpath
			fixFile = getFixOrderFile();
			if(fixFile == null)
			{
				righto = false;
			}
			else
			{
				// Apro il file di log
				if(!openLogFile())
				{
					righto = false;
				}
				else
				{
					//Fix 24495 PM >
					InfoMiddlewarePerFix imf = (InfoMiddlewarePerFix)Factory.createObject(InfoMiddlewarePerFix.class);
					
					if (!imf.isMiddlewareSupportato())
					{
						righto = false;
						
						System.out.println(ResourceLoader.getString(SETUP_RES, "CheckMiddleware"));
						System.out.println("----------------------------------------------------------");
						output.println(ResourceLoader.getString(SETUP_RES, "CheckMiddleware"));
						output.println("----------------------------------------------------------");
					}
					else
					{
					    //Fix 24495 PM <

						//33045 inizio
					    Set<Integer> notInstalledPrerequisites = listNotInstalledPrerequisites();
					    if (notInstalledPrerequisites.size() > 0) {
					    	logNotInstalledPrerequisites(notInstalledPrerequisites);
					    	return false;
					    }
						//33045 fine	
					
					//Mod. 16172 inizio - Controllo se c'è almeno una fix di Amministrazione e Controllo (Finance)
//					System.out.println("----------------------------------------------------------"); Fix 18069
//					output.println("----------------------------------------------------------"); Fix 18069
					if (thereIsAFinanceFix() && !isACOff()) {
						System.out.println(ResourceLoader.getString(SETUP_RES, "AppOn1")); //"*** ATTENZIONE *** Ci sono fix di Amministrazione e Controllo, ma l'applicazione non è stata spenta"
						System.out.println(ResourceLoader.getString(SETUP_RES, "AppOn2")); //"*** APPLICAZIONE DELLE FIX NON EFFETTUATA - Spegnere Amministrazione e controllo e riapplicare le fix");
						System.out.println("----------------------------------------------------------");
						output.println(ResourceLoader.getString(SETUP_RES, "AppOn1"));
						output.println(ResourceLoader.getString(SETUP_RES, "AppOn2"));
						output.println("----------------------------------------------------------");
						righto = false;
					}
					//Altrimenti proseguo
					else {
						System.out.println("----------------------------------------------------------");
						output.println("----------------------------------------------------------");
						// fine mod. 16172
						Map m = AdvanceFixesManager.getMissingClosingFixes();
						if (m == null)
						{
							logAdvanceFixesTableNotFound();
						}
						else if (!m.isEmpty())
						{
							logAdvanceFixesError(m);
							righto = false;
						}
						else
						{
							// Cerco il file nel classpath
							fixDescriptionFile = getFixDescriptionListFile();

							// Se il file è stato trovato carico le descrizioni
							if (fixDescriptionFile != null)
							{
								loadFixDescription();
							}

							// Scorro il file con l'elenco delle fix
							read = fixFile.readLine();
							StringBuffer additionalFixFile = new StringBuffer(); // Fix 12355 Ryo
							additionalActived = false;
							while (righto && read != null)
							{
								read = read.trim();
								// Non considero le righe vuote e quelle di commento
								if ((!read.startsWith(COMMENT)) && (!read.equals("")))
								{
									// Se la riga inizia con "Application" imposto i valori degli attributi
									if (read.startsWith(APPLICATION))
									{
										fixNrStr = null;
										formatApplication(read);
										read = fixFile.readLine();
									}
									// se la riga inizia con "Description" imposto i valori delle descrizioni
									else if (read.startsWith(DESCRIPTION))
									{
										fixNrStr = null;
										formatDescription(read);
										read = fixFile.readLine();
									}
									// Se la riga inizia con "UpdateApplication" imposto i nuovi attributi
									else if (read.startsWith(UPDATE_APPL))
									{
										if (updateAppl(read))
										{
											read = fixFile.readLine();
										}
										else
										{
											return false;
										}
									}

									// Fix 12355 Ryo ->
									else if (read.startsWith(START_ADDITIONAL))
									{
										System.out.println("\n" + ResourceLoader.getString(SETUP_RES, "StartAdditionalFix") + "\n");
										formatApplication(temp);
										additionalActived = true;
										read = fixFile.readLine();
									}
									else if (additionalActived)
									{
										if (read.startsWith(END_ADDITIONAL))
										{
											additionalActived = false;
											if (updateAppl(read))
											{
												read = fixFile.readLine();
											}
											else
											{
												return false;
											}
										}
										else if (!processFix(true))
										{
											continue;
										}
										else
										{
											additionalFixFile.append(read).append(System.getProperty("line.separator"));
										}
									}

									// Se la riga non fa parte degli altri casi appartiene a una fix
									else
									{
										if (!processFix(false))
										{
											continue;
										}
									}
									if (fixNrStr != null && !fixNrStr.equals(""))
									{
										// Provo ad applicare la fix
										if (!try2ApplyTheFix(additionalActived))
										{
											righto = false;
										}
									}
								}
								// Se le riga era un commento o era vuota, leggo la successiva
								else
								{
									read = fixFile.readLine();
								}
							}
							if (righto)
							{
								closeFix();
							}
						}
					} //Mod. 16172
					}//Fix 24495 PM 
					
				}
			}
		}
		catch(Exception e)
		{
			e.printStackTrace(Trace.excStream);
			righto = false;
		}
		finally
		{
			closeLogFile();
		}
		return righto;
	}

	//Mod. 16172 inizio
	/*
	 * Verifica se nel FixOrder corrente c'è almeno una fix di Amministrazione e Controllo (Finance)
	 * @return boolean true se c'è almeno una fix di A&C; false in caso contrario
	 */
	private boolean thereIsAFinanceFix() throws Exception {
		boolean resp = true;
		BufferedReader curFixFile = getFixOrderFile();
		if(fixFile != null)
			{
				resp = false;
		read = curFixFile.readLine();
		while (!resp && read != null) {
			read = read.trim();
			//Considero solo le righe da processare
			if ((!read.startsWith(COMMENT)) && (!read.equals("")) &&
					!read.startsWith(DESCRIPTION) &&
					!read.startsWith(UPDATE_APPL) &&
					!read.startsWith(START_ADDITIONAL) &&
					!read.startsWith(END_ADDITIONAL)) {
				//Verifico se è una fix Finance
				if (read.indexOf("ApplyFixPrimroseIni")>0) {
					resp = true;
				}
			}
			//Passo alla riga successiva
			read = curFixFile.readLine();
		}
			}
		return resp;
	}

	/*
	 *Verifico se l'applicazione Amministrazione e controllo è stata chiusa prima di lanciare l'applicazione delle fix.
	 * @return boolean true se Amministrazione e controllo è stata spenta, false in caso contrario
	 */
	private boolean isACOff() {
		boolean acOff = true;
		//Per verificare se Amministrazione e controllo è stata chiusa provo a rinominare alcuni oggetti
		//Se la rename non riesce significa che l'oggetto è in uso e quindi che l'applicazione è aperta
		//Al primo oggetto non rinominabile esco con false
		//Se sono tutti rinominabili esco con true

		String originalCompleteName;
		String renamedCompleteName;

		//Test su un oggetto (jar) server 510
		originalCompleteName = getServerRoot() + File.separator + fileToCheck1 + jarExt;
		renamedCompleteName = getServerRoot() + File.separator + fileToCheck1 + suffixToCheck + jarExt;
		if (renameAndRestoreFileName(originalCompleteName, renamedCompleteName)) {

			//Test su un oggetto (jar) server 610
			originalCompleteName = getServerRoot2() + File.separator + fileToCheck2 + jarExt;
			renamedCompleteName = getServerRoot2() + File.separator + fileToCheck2 + suffixToCheck + jarExt;
			if (renameAndRestoreFileName(originalCompleteName, renamedCompleteName)) {

				//Test sull'oggeto (dll) client 510
				originalCompleteName = getClientRoot() + File.separator + fileToCheck3 + dllExt;
				renamedCompleteName = getClientRoot() + File.separator + fileToCheck3 + suffixToCheck + dllExt;
				if (renameAndRestoreFileName(originalCompleteName, renamedCompleteName)) {

					//Test sull'oggeto (dll) client 610
					originalCompleteName = getClientRoot2() + File.separator + fileToCheck4 + dllExt;
					renamedCompleteName = getClientRoot2() + File.separator + fileToCheck4 + suffixToCheck + dllExt;
					if (renameAndRestoreFileName(originalCompleteName, renamedCompleteName)) {
						System.out.println(ResourceLoader.getString(SETUP_RES, "AppOff")); //("Amministrazione e controllo risulta correttamente chiusa.");
						output.println(ResourceLoader.getString(SETUP_RES, "AppOff")); //("Amministrazione e controllo risulta correttamente chiusa.");
					}
					else {
						System.out.println(ResourceLoader.getString(SETUP_RES, "ObjInUse", new String[] {originalCompleteName})); //("L'oggetto " + originalCompleteName + " risulta in uso.");
						output.println(ResourceLoader.getString(SETUP_RES, "ObjInUse", new String[] {originalCompleteName})); //("L'oggetto " + originalCompleteName + " risulta in uso.");
						acOff = false;
					}
				}
				else {
					System.out.println(ResourceLoader.getString(SETUP_RES, "ObjInUse", new String[] {originalCompleteName})); //("L'oggetto " + originalCompleteName + " risulta in uso.");
					output.println(ResourceLoader.getString(SETUP_RES, "ObjInUse", new String[] {originalCompleteName})); //("L'oggetto " + originalCompleteName + " risulta in uso.");
					acOff = false;
				}

			}
			else {
				System.out.println(ResourceLoader.getString(SETUP_RES, "ObjInUse", new String[] {originalCompleteName})); //("L'oggetto " + originalCompleteName + " risulta in uso.");
				output.println(ResourceLoader.getString(SETUP_RES, "ObjInUse", new String[] {originalCompleteName})); //("L'oggetto " + originalCompleteName + " risulta in uso.");
				acOff = false;
			}
		}
		else {
			System.out.println(ResourceLoader.getString(SETUP_RES, "ObjInUse", new String[] {originalCompleteName})); //("L'oggetto " + originalCompleteName + " risulta in uso.");
			output.println(ResourceLoader.getString(SETUP_RES, "ObjInUse", new String[] {originalCompleteName})); //("L'oggetto " + originalCompleteName + " risulta in uso.");
			acOff = false;
		}
		return acOff;
	}

	/*
	 * Rinomino e ripristino il nome di un file per verificare se è in uso
	 */
	public boolean renameAndRestoreFileName(String originalCompleteName, String renamedCompleteName) {
		boolean rr = true;
		File testFile = new File(originalCompleteName);
		try {
			if (testFile.exists()) {
				File testRnFile = new File(renamedCompleteName);
				boolean resRen = testFile.renameTo(testRnFile);

				//Se la rename è andata bene ripristino il nome originale
				if (resRen) {
					File tmpFile1 = new File(renamedCompleteName);
					if (tmpFile1.exists()) {
						resRen = tmpFile1.renameTo(new File(originalCompleteName));
						if (!resRen) {
							rr = false;
							//("*** ATTENZIONE *** Rinominare manualmente l'oggetto " + renamedCompleteName + ripristinando il nome originale " + originalCompleteName);
							System.out.println(ResourceLoader.getString(SETUP_RES, "RenameObj", new String[] {renamedCompleteName, originalCompleteName}));
							output.println(ResourceLoader.getString(SETUP_RES, "RenameObj", new String[] {renamedCompleteName, originalCompleteName}));
						}
					}
				}
				//Altrimenti risultato false
				else {
					rr = false;
				}

			}
		}
		catch (Exception e) {
			rr = false;
			e.printStackTrace(Trace.excStream);
			return rr;
		}
		return rr;
	}
	//fine mod. 15172

	/* Revisions:
	 * Fix #    Date          Owner      Description
	 * 12355    19/ 4/2010    Ryo        Prima stesura
	 */
	private void closeFix() throws Exception
	{
		//AdvanceFixesManager.resumeSuspendedAdvanceFixes();//31129
		// A questo punto tutto è finito bene e faccio commit
		commit();

		// Cerco il file viewschemas.txt nel classpath
		makeViews();
		TagProcessor.clearVectorsApps();
	}

	// ANUBIAN CODE <-

	/**
	 * Metodo che estrae i valori di applicazione, modulo, versione, release e modifica.
	 * @param String line Stringa che corrisponde alla riga considerata
	 * @return String[] array formato da tutti i parametri nel seguente ordine:<br>
	 * nome dell'applicazione, modulo, versione, release e modifica.
	 */
	public String[] extractApplicationValues(String line) {
		int pos = line.indexOf(SEPARATOR);
		String value = line.substring(pos + 1).trim();
		String[] result = new String[5];
		StringTokenizer valueTokenizer = new StringTokenizer(value, ",");
		int i = 0;
		while (valueTokenizer.hasMoreTokens()) {
			String resValue = valueTokenizer.nextToken();
			result[i] = resValue;
			i++;
		}
		return result;
	}

	/**
	 * Estrae la stringa che corrisponde a tutti i parametri della fix.
	 * La suddivisione in singoli parametri verrà fatta da chi chiama questo metodo.
	 * @param String line Stringa che corrisponde alla riga considerata
	 * @return String Stringa formaao da tutti i parametri della fix nel seguente ordine:<br>
	 * nome della classe, nome del file, eventuali parametri (separati dalla virgola).
	 */
	public String extractFixValues(String line) {
		int pos = line.indexOf(SEPARATOR);
		fixNrStr = line.substring(0, pos).trim();
		String value = line.substring(pos + 1).trim();
		return value;
	}

	/**Fix 2662 reso pubblico e statico
	 * Cerca il file con l'elenco delle fix nel classpath e nei file Jar.
	 * @param String fixFileName nome del file con l'elenco delle fix.
	 * @return BufferedReader
	 */
	public static BufferedReader getFile(String fixFileName) {
		try {
			File file = new File(fixFileName);
			String[] paths = SystemParam.getClassPath();
			for (int i = 0; i < paths.length; i++) {
				if(SystemParam.isJVMJava2Compatible()) {
					JarFile jar = getJarFile(paths[i]);
					//...Se nel classpath c'è un jar controllo le JarEntry passando il nome del file
					if(jar != null) {
						JarEntry entry = jar.getJarEntry(fixFileName);
						//...Se esiste una JarEntry vuol dire che ho trovato il file
						if(entry != null) {
							InputStream stream = jar.getInputStream(entry);
							return new BufferedReader(new InputStreamReader(stream));
						}
					}
				}
				//...Se il file non è in un jar controllo nel classpath
				String s = paths[i] + fixFileName;
				file = new File(s);
				//...Se esiste nel path che sto considerando vuol dire che ho trovato il file
				if(file.exists())
					return new BufferedReader(new FileReader(file));
			}
			//...Altrimenti il file non esiste nel classpath e ritorno null.
			System.out.println(ResourceLoader.getString(SETUP_RES, "FixFileNotFound", //Fix 03679
					new String[] {fixFileName}));
			return null;
		}
		catch (Exception e) {
			e.printStackTrace(Trace.excStream);
			return null;
		}
	}

	/**
	 * Metodo statico che trova un file Jar.
	 * @param String fileName nome del file jar.
	 * @return l'oggetto JarFile corrispondente o null se non viene trovato.
	 */
	public static JarFile getJarFile(String fileName) {
		try {
			return new JarFile(fileName);
		}
		catch (Exception e) {
			return null;
		}
	}

	/**Mod.1315
	 * openLogFile().
	 * @return boolean
	 */
	protected boolean openLogFile() {
		try {
			//Mod. 12771 inizio
			//Mod. 12771//writer = new FileOutputStream(new File(FixRunner.FixFileReportName));
			//Gestione dell'history del file di log:
			// - rinomino l'eventuale file di log che non ha la data nel nome
			//  - tengo gli n file più recenti e cancello gli altri
			System.out.println("==========================================================");
			int logFileOk = manageLogHistory(FixRunner.FixFileReportName);
			System.out.println(ResourceLoader.getString(SETUP_RES, "DelOldLogFileNo", new Object[]{String.valueOf(logFileOk)})); //"Cancellati n file
			//Creo il nuovo file di log:
			//Mod. 12958////Costruisco il nome del file di log aggiungendo a quello "standard" il timestamp corrente
			//Mod. 12958//String newLogFileName = addTimestampToFileName(FixRunner.FixFileReportName, new Timestamp(System.currentTimeMillis()));
			//Mod. 12958//System.out.println(ResourceLoader.getString(SETUP_RES, "LogFileNameIs")+newLogFileName); //"NOME DEL FILE DI LOG: ");
			//Mod. 12958//File logFile = new File(newLogFileName);
			File logFile = new File(FixRunner.FixFileReportName);//Mod. 12958//
			//mod. 12771 fine
			writer = new FileOutputStream(logFile);
		}
		catch (IOException exc1) {
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoLogFileCreationErr"));//Mod. 9634//"ERRORE: impossibile creare il file di log.");
			return false;
		}
		System.out.println("==========================================================");
		//fine mod. 12771

		output = new PrintWriter(writer);
		output.println("*******************************************************");
		output.println(ResourceLoader.getString(SETUP_RES, "LookForErrorStr"));//Mod. 9634//"PER TROVARE GLI ERRORI, CERCARE LA STRINGA #ERRORE#");
		// 7281 DM inizio
		output.println(ResourceLoader.getString(SETUP_RES, "LookForWaningStr"));//Mod. 9634//"PER TROVARE I WARNING, CERCARE LA STRINGA #WARNING#");
		output.println(ResourceLoader.getString(SETUP_RES, "LookForDuplicStr"));//Mod. 9634//"PER TROVARE I DUPLICATI, CERCARE LA STRINGA #DUPLICATO#");
		// 7281 DM fine
		output.println("*******************************************************");
		return true;
	}

	/**Mod.1315
	 * closeLogFile().
	 */
	protected void closeLogFile() {
		if (output != null) // Fix 18069
		output.close();
	}

	/**Mod.12771 inizio
	 * manageLogHistory() "storicizzo" il file di log:
	 * - rinomino l'eventuale file SQLFileReport.txt aggiungendo al nome la sua data di ultima modifica
	 * - se ci sono più di n file di log (n è letto da MaxNrOfFixLogFileToKeep di Setup.properties)
	 *    cancello i più vecchi sino ad averne n
	 * @return boolean ritorno true se è andato tutto bene; false se c'è stato qualche erore bloccante
	 */
	public int manageLogHistory(String logName) {
		int deletedFile = 0;
		//Creo un File con il nome "standard" del file di log:
		File logFile = new File(logName);
		//Se esiste lo rinomino aggiungendo il timestamp di ultima modifica
		//(la data di creazione non è disponibile)
		String filenameRoot = "";
		String extName = "";
		if (logFile.exists()) {
			newName = addTimestampToFileName(logName, new Timestamp(logFile.lastModified()));
			//Rinomino il file
			boolean resRen = logFile.renameTo(new File(newName));
		}

		//CONTO I FILE DI LOG: Cerco nella stessa dir tutti i file che iniziano con il nome "standard" del log
		String dirName = ""; //Nome della dir assoluta che contiene i file di log
		int indx = logFile.getAbsolutePath().indexOf(logName);
		if (indx > 1)
			dirName = logFile.getAbsolutePath().substring(0, indx - 1);
		File dirFile = new File(dirName);
		String crfFileName = logName.substring(0,logName.indexOf("."));//12816.ES Nome del file di log senza l'estensione
		if (dirFile.isDirectory()) {
			File[] files = dirFile.listFiles(); //elenco dei file nella dir
			List tsl = new ArrayList(); //Lista dei timestamp aggiunti al nome dei file
			int fileNr = 0; //Numero dei file di tipo SQLFileReport trovati
			//Esamino i file nella directory
			if (files != null) {
				for (int i = 0; i < files.length; i++) {
					File curFile = files[i]; //i-mo file esaminato
					String curFileName = curFile.getName(); //nome senza path del file esaminato
					//E' un file di log delle fix (inizia per
					if (curFileName.startsWith(crfFileName)) { //12816.ES
						int dateIndx = curFileName.indexOf("_");
						int extensIndx = curFileName.indexOf(".");
						if (dateIndx > 0 && extensIndx > 0 && dateIndx + 1 < extensIndx) {
							tsl.add(curFileName.substring(dateIndx + 1, extensIndx));
							fileNr++;
						}
					}
				}
			}

			//SE NE HO PIU' DEL MASSIMO PREVISTO, LI ORDINO E CANCELLO I PIU' VECCHI
			int maxLogNr = Integer.parseInt(ResourceLoader.getString(SETUP_RES, "MaxNrOfFixLogFileToKeep"));
			System.out.println(ResourceLoader.getString(SETUP_RES, "FoundFileMaxFile", new Object[]{String.valueOf(fileNr), String.valueOf(maxLogNr)})); //"TROVATI  n FILE, tenerne al massimo m
			if (fileNr > maxLogNr && tsl != null && !tsl.isEmpty()) {
				Collections.sort(tsl);
				//Separo il nome e l'estensione
				int extIndx = logName.indexOf("."); //posizione di inizio del'estensione nel nome del file
				if (extIndx > -1) {
					filenameRoot = logName.substring(0, extIndx);
					extName = logName.substring(extIndx, logName.length());
				}
				for (int jj = 0; jj < fileNr - maxLogNr; jj++) {
					File delFile = new File(filenameRoot + "_" + tsl.get(jj) + extName);
					if (delFile.delete()) {
						deletedFile++;
						System.out.println(ResourceLoader.getString(SETUP_RES, "DeletedOldLogFile", new Object[]{delFile.getAbsolutePath()}));//"File " + delFile.getAbsolutePath() + " cancellato con successo");
					}
					else
						System.out.println(ResourceLoader.getString(SETUP_RES, "NotDeletedOldLogFile", new Object[]{delFile.getAbsolutePath()}));//"File " + delFile.getAbsolutePath() + " cancellato con successo");
				}
			}
		}
		return deletedFile;
	}

	/**
	 * addTimestampToFileName()
	 * Riceve la stringa con il nome del file: vecchioNome.vecchiaEstensione e un timestamp
	 * Restituisce il nome del file con inserito il timestamp secondo lo schema:
	 *       VecchioNome_AAAA-MM-GG-hh-mm-ss-ff.vecchiaEstensione
	 * In caso di errore restituisce una stringa vuota
	 * @param String nome del file da modificare.
	 * @param Timestamp timestamp da aggiungere al nome file.
	 * @return String il nome con il timestamp aggiunto
	 */
	public String addTimestampToFileName(String plainName, Timestamp ts) {
		String nameWithTS = "";
		String filenameRoot = "";
		String extName = "";
		//Separo il nome e l'estensione
		int extIndx = plainName.indexOf("."); //posizione di inizio del'estensione nel nome del file
		if (extIndx > -1) {
			filenameRoot = plainName.substring(0, extIndx);
			extName = plainName.substring(extIndx, plainName.length());
			File logFile = new File(plainName);
			//
			nameWithTS = filenameRoot + "_" + normalizeDate(ts) + extName;
		}
		return nameWithTS;
	}

	/**
	 * normalizeDate()
	 * Riceve un Timestamp e la trasformo in una stringa nel formato:
	 *       AAAA-MM-GG-hh-mm-ss-ff
	 * @param Timestamp timestamp da trasformare.
	 * @return String con il Timestamp trasformato in stringa formattata
	 */
	public String normalizeDate(Timestamp ts) {
		String stringDate = ts.toString();
		stringDate = stringDate.replace(' ', '-');
		stringDate = stringDate.replace(':', '-');
		stringDate = stringDate.replace('.', '-');
		return stringDate;
	}
	//fine mod. 12771

	/**
	 * Main per il lancio della classe.
	 * Richiede l'inserimento di un solo parametro obbligatorio, ovvero il nome
	 * del database a cui connettersi. Successivamente possono essere inseriti
	 * l'utente di therm, la password dell'utente di therm e il tipo di piattaforma.<br>
	 * Istanzia un oggetto della classe <code>FixFileProcessor<\code>.<br>
	 * @param args parametri di lancio:<br>
	 * - nome del database;<br>
	 * - nome dell'utente di therm;<br>
	 * - password dell'utente di therm;<br>
	 * - tipo di piattaforma (NT, AS o LX).
	 * - applications
	 * @throws Exception.
	 */
	public static void main(String[] args) throws Exception
	{
	 // Fix 12355 Ryo ->
		if(args == null || args.length == 0)
		{
			args = new String[] {
					"THERMOS",
					"T:\\ThermFw\\2.0\\fix\\std",
					"-U",
					"ADMIN",
					"-P",
					"ADMIN",
					"-CF",
					"W:\\ThermFw\\2.0\\lib",
					"W:\\ThermFw\\2.0\\websrc",
					"W:\\ThermFw\\2.0\\print",
					"null"
				};
		}
		// Fix 12355 Ryo <-

//		FixFileProcessor fixFileProcessor = new FixFileProcessor();   // Fix 3453 Ryo
		Security.setCurrentDatabase(args[0], null); // Fix 3453 Ryo
		Security.openDefaultConnection(); // Fix 3453 Ryo
		FixFileProcessor fixFileProcessor = (FixFileProcessor)Factory.createObject("com.thera.thermfw.setup.FixFileProcessor"); // Fix 3453 Ryo
		fixFileProcessor.run(args);
		//Fix 03679 MM
		Security.closeDefaultConnection();
	}

	/* Revisions:
	 * Fix #    Date          Owner      Description
	 *  9428    25/ 6/2008    Ryo        userPwd non viene più ricavata dai parametri passati ma dalla tabella dell'utente
	 */
	public void run(String[] args) throws Exception {
		// Mod.905 - ini
		//Modificato da MM nell'ambito della fix 905
		//    if(args == null || args.length <= 2) {
		if(args == null || args.length < 2) {
			System.out.println(""); //Fix 03679
			System.out.println(ResourceLoader.getString(SETUP_RES, "ParamNumberErr"));//Mod. 9634//"ATTENZIONE: numero errato di parametri."); //Fix 03679
			printHelp();
		}
		// Mod.905 - fin

		String dbName = args[0];
		//Fix 4576 PM-MM
		String fixRoot = extractFixRoot(args);
		String userName = DEF_USER;
		String userPwd = DEF_USER;
		String platType = NT;
		String userDir = "user";
		String insertMode = INSERT_UPDATE_MODE; //Mod. 2295

		// Mod.905 - ini - Options
		boolean parametriErrati = false;
		// Extract UserName
		int userNamePos = getFlagPosition(USER_FLAG, args);
		if(userNamePos >= 2 && userNamePos != args.length) {
			userName = extractParam(userNamePos, args);
			if(userName == null)
				parametriErrati = true;
		}
		// Extract Password
		int passwordPos = getFlagPosition(PWD_FLAG, args);
		if(passwordPos >= 2 && passwordPos != args.length)
		{
			//			userPwd = extractParam(passwordPos, args);	// Fix 9428 Ryo
			userPwd = readThermPwd(/*dbName, */userName);	// Fix 9428 Ryo
			if(userPwd == null)
				parametriErrati = true;
		}
		// Extract Paltform Type
		int platformTypePos = getFlagPosition(PTYPE_FLAG, args);
		if(platformTypePos >= 2 && platformTypePos != args.length) {
			platType = extractParam(platformTypePos, args);
			if(platType == null)
				parametriErrati = true;
		}
		// Extract Applications
		int appsPos = getFlagPosition(APPS_FLAG, args);
		extractAllAvaApplications();
		if(!extractApplications(appsPos, args))
			parametriErrati = true;

		// Extract Paths for Copy
		int copyPos = getFlagPosition(COPY_FLAG, args);
		if(copyPos >= 2 && copyPos != args.length) {
			if(extractPaths(copyPos, args))
				copyFiles = true;
			else
				parametriErrati = true;
		}

		// Extract Paths for Copy1
		int copyPos1 = getFlagPosition(COPY_FLAG1, args);
		if(copyPos1 >= 2 && copyPos1 != args.length) {
			if(extractPaths1(copyPos1, args))
				copyFiles1 = true;
			else
				parametriErrati = true;
		}

		//Mod. 13978 inizio
		// Extract Paths for Copy2
		int copyPos2 = getFlagPosition(COPY_FLAG2, args);
		if(copyPos2 >= 2 && copyPos2 != args.length) {
			if(extractPaths2(copyPos2, args)) {
				copyFiles2 = true;
				if (!copyFiles) {
					CF2MissesCF = true;//il flag mi servirà per scrivere il messaggio anche nel file di log
				}
			}
			else {
				incompleteCF2Param = true; //il flag mi servirà per scrivere il messaggio anche nel file di log
			}
		}
		//fine mod. 13978

		// Mod.2226 - Extract Directory name for User's files
		int usrDirPos = getFlagPosition(USR_DIR_FLAG, args);
		if(usrDirPos >= 2 && usrDirPos != args.length) {
			userDir = extractParam(usrDirPos, args);
			if(userDir == null)
				parametriErrati = true;
			else
				setNameUserDir(userDir);
		}

		//Mod. 2295
		// Extract insert mode
		int insertModePos = getFlagPosition(INSERT_MODE, args);
		if(insertModePos >= 2 && insertModePos != args.length) {
			insertMode = extractParam(insertModePos, args);
			if(insertMode.equals(INSERT_ONLY_MODE))
				tryUpdate = false;
			else if(!insertMode.equals(INSERT_UPDATE_MODE))
				parametriErrati = true;
		}
		//Mod. 2295

		// Mod.2256 - IVAN
		//Fix 4576 MM
		//onlyCopyFiles = getFlagPresent(ONLY_COPY_FLAG, args);
		onlyCopyFiles = isOnlyCopyFilesFlagPresent(args);

		// Mod.2967 - inizio
		// Extract Paths for Copy FTP
		int copyFTPPos = getFlagPosition(FTP_COPY_FLAG, args);
		if(copyFTPPos >= 2 && copyFTPPos != args.length) {
			if(extractFTPParams(copyFTPPos, args)) {
				ftpCopyFiles = true;
				NUM_FIX_PARAMS = 7;
			}
			else
				parametriErrati = true;
		}
		// Backups files
		//Fix 4576 MM
		//    backupsFiles = getFlagPresent(BACKUPS_FLAG, args);
		backupsFiles = isBackupFilesFlagPresent(args);
		// Mod.2967 - fine

		// Fix 3168
		//Fix 4576 MM
		//    commitEachFix = getFlagPresent(COMMIT_FLAG, args);
		commitEachFix = isCommitEachFixFlagPresent(args);
		if(commitEachFix && onlyCopyFiles) {
			System.out.println(ResourceLoader.getString(SETUP_RES, "ConflictErr"));//Mod. 9634//"Le modalità 'Solo copia file' e 'Commit ad ogni fix' non possono essere selezionate contemporaneamente"); //Fix 03679
			parametriErrati = true;
		}
		if(commitEachFix)
			System.out.println(ResourceLoader.getString(SETUP_RES, "CommitAtEachFix"));//Mod. 9634//"Viene effettuata commit ad ogni fix");
		// Fine fix 3168

		// Fix 3418
		//    uniqueFixNumber = getFlagPresent(UNIQUE_FIX_NUMBER, args);
		uniqueFixNumber = isUniqueFixNumberFlagPresent(args);
		if(uniqueFixNumber)
			System.out.println(ResourceLoader.getString(SETUP_RES, "AbsoluteUniqueFixNo"));//Mod. 9634+11347//"Viene considerata l'univocità assoluta del numero di fix");
		//Mod. 11347//System.out.println(ResourceLoader.getString(SETUP_RES, "CommitAtEachFix"));//Mod. 9634//"Viene considerata l'univocità assoluta del numero di fix");
		// Fine fix 3418
		// Fix 04576 inizio
		//    hierarchic = getFlagPresent(HIERARCHIC_FLAG, args);
		hierarchic = getHierarchicFlagPresent(args);
		if(hierarchic)
			System.out.println(ResourceLoader.getString(SETUP_RES, "FixHierarchicStruct"));//Mod. 9634//"Viene utilizzata la struttura gerarchica delle fix");
		// Fix 04576 fine
		if(parametriErrati) {
			System.out.println(""); //Fix 03679
			System.out.println(ResourceLoader.getString(SETUP_RES, "WrongParamErr"));//Mod. 9634//"ATTENZIONE: parametri errati."); //Fix 03679
			printHelp();
		}
		// Mod.905 - fin

		// Mod.1315 - 1754
		controlServerDirectoryForCopyFiles();

		//Guardo nelle preferenze dell'applicazione quale versione di Crystal Reports è stata impostata
		crystalVer = Preferences.getInstance().getApplPref().getCrystalRVer(); //11234

//		Security.setCurrentDatabase(dbName, null);   // Fix 3453 Ryo
//		Security.openDefaultConnection();   // Fix 3453 Ryo
		
		//28381 inizio
		//Security.openSession(userName, userPwd);
		if(isStandalone()) {
			Security.openSession(userName, userPwd);
		}
		//28381 fine
		
		setPlatformType(platType);
		setFixRoot(fixRoot); //...MOD 170 (LP)
		boolean ok = process(); //...MOD 169 (LP)

		if(!ok && !additionalActived)
		{
			//28381 inizio
			//System.exit( -1);
			if(isStandalone()) {
				System.exit(-1);
			}
			//28381 fine
		}
		//...MOD 169 (LP)...inizio

		else
		{
			//Mod. 13667// Solo se sono stati eseguiti .SQL, .TDDML o classi
			if (doneSthg)
			{
				//...chiamo i metodi rebuildSonFolders e rebuildParentFolders nel caso ci siano
				//...state modifiche alla struttura dell'applicazione
				int rc = SonFolders.rebuildSonFolders();
				System.out.println(ResourceLoader.getString(SETUP_RES, "SonFolderRecostr", new String[] {String.valueOf(rc)})); //Mod. 9634//"Ricostruzione di SonFolders: generati " + rc + //Fix 03679
				//Mod. 9634//" elementi");
				// 3913 DM inizio
				// ConnectionManager.commit();
				commit();
				// 3913 DM fine

				int rc1 = ParentFolders.rebuildParentFolders();
				System.out.println(ResourceLoader.getString(SETUP_RES, "ParentFolderRecostr", new String[] {String.valueOf(rc1)})); //Mod. 9634//"Ricostruzione di ParentFolders: generati " + rc1 + //Fix 03679
				//Mod. 9634//" elementi");

				// 04959 - DF
				int rc2 = Authority.cleanDuplications();
				switch (rc2) {
					case -1:
						System.out.println(ResourceLoader.getString(SETUP_RES, "DuplAuthRemovalErr")); //Mod. 9634//"Rimozione duplicazioni su Authority fallita.");
						break;
					case 0:
						System.out.println(ResourceLoader.getString(SETUP_RES, "NoDuplAuth")); //Mod. 9634//"Non sono state individuate duplicazioni su Authority.");
						break;
					default:
						System.out.println(ResourceLoader.getString(SETUP_RES, "DuplAuthNr", new String[] {String.valueOf(rc2)})); //Mod. 9634//"Sono stati rimossi " + rc2 + " elementi duplicati su Authority.");
				}
				// 04959 - Fine

				// Fix 5272 PM Inizio
				//Mod. 5003
				resetAuthorizableFlag();
				//fine mod. 5003
				// Fix 5272 PM Fine

				// 3913 DM inizio
				// ConnectionManager.commit();
				commit();
				// 3913 DM fine

				//...MOD 169 (LP)...fine

				//Fix 04576 PM-MM
				endRun();
			} //Mod. 13667
			
			//28381 inizio 
			//Security.closeSession();
			if(isStandalone()) {
				Security.closeSession();
			}
			//28381 fine
			
			//    Fix 03679 MM
			//      Security.closeDefaultConnection();
			//      System.exit(0);
		}
	}

	/*
	 * Controllo presenza directory di copia sul server
	 */
	public void controlServerDirectoryForCopyFiles() throws IOException {
		// Mod.1315 - 1754
		if(copyFiles) {
			copyLib = !getLibRoot().equalsIgnoreCase("NULL");
			copyWeb = !getWebRoot().equalsIgnoreCase("NULL");
			copyRpt = !getRptRoot().equalsIgnoreCase("NULL");
			copyUser = !getUserRoot().equalsIgnoreCase("NULL"); //Mod.2226
		}
		//Mod.2258 - ini
		if(copyFiles1) {
			copyServer = !getServerRoot().equalsIgnoreCase("NULL");
			copyClient = !getClientRoot().equalsIgnoreCase("NULL");
			copyReports = !getReportsRoot().equalsIgnoreCase("NULL");
		}
		//Mod.13978 inizio
		if(copyFiles2) {
			copyServer2 = !getServerRoot2().equalsIgnoreCase("NULL");
			copyClient2 = !getClientRoot2().equalsIgnoreCase("NULL");
			copyReports2 = !getReportsRoot2().equalsIgnoreCase("NULL");
		}
		//fine mod. 13978

		//Mod.2967 - inizio
		//Mod.2258 - fin
		if(copyFiles || copyFiles1) {
			boolean controlDirectories = true;
			if(!ftpCopyFiles)
				controlDirectories = controlDirectories_FileSystem();
			else
				controlDirectories = controlDirectories_FTP();
			if(!controlDirectories)
				System.exit( -1);
			//System.exit(0);
		}

		//Mod.2967 - fine
	}

	//Mod.2967 - inizio
	//Controlla l'esistenza delle directory di copia
	public boolean controlDirectories_FileSystem() throws IOException {
		if(copyLib && !FileUtil.exists(getLibRoot())) {
			System.out.println(""); //Fix 03679
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoLibRoot",  new String[] {getLibRoot()}));//Mod. 9634//"ATTENZIONE: LibRoot inesistente: " + getLibRoot()); //Fix 03679
			return false;
		}
		if(copyWeb && !FileUtil.exists(getWebRoot())) {
			System.out.println(""); //Fix 03679
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoWebRoot",  new String[] {getWebRoot()}));//Mod. 9634//"ATTENZIONE: WebRoot inesistente: " + getWebRoot()); //Fix 03679
			return false;
		}
		if(copyRpt && !FileUtil.exists(getRptRoot())) {
			System.out.println(""); //Fix 03679
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoRptRoot",  new String[] {getRptRoot()}));//Mod. 9634//"ATTENZIONE: RptRoot inesistente: " + getRptRoot()); //Fix 03679
			return false;
		}
		//Mod.2226
		if(copyUser && !FileUtil.exists(getUserRoot())) {
			System.out.println("");
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoUserRoot",  new String[] {getUserRoot()}));//Mod. 9634//"ATTENZIONE: RptRoot inesistente: " + getUserRoot()); //Fix 03679
			return false;
		}
		//Mod.2258 - ini
		if(copyServer && !FileUtil.exists(getServerRoot())) {
			System.out.println(""); //Fix 03679
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoServerRoot",  new String[] {getServerRoot()}));//Mod. 9634//"ATTENZIONE: ServerRoot inesistente: " + getServerRoot()); //Fix 03679
			return false;
		}
		if(copyClient && !FileUtil.exists(getClientRoot())) {
			System.out.println(""); //Fix 03679
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoClientRoot",  new String[] {getClientRoot()}));//Mod. 9634//"ATTENZIONE: ClientRoot inesistente: " + getClientRoot()); //Fix 03679
			return false;
		}
		if(copyReports && !FileUtil.exists(getReportsRoot())) {
			System.out.println(""); //Fix 03679
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoReportsRoot",  new String[] {getReportsRoot()}));//Mod. 9634//"ATTENZIONE: ReportsRoot inesistente: " + //Fix 03679
			//Mod. 9634//getReportsRoot());
			return false;
		}
		//Mod.2258 - fin

		return true;
	}

	//Controlla l'esistenza delle directory di copia FTP
	public boolean controlDirectories_FTP() throws IOException {
		FTPConnection ftp = openFTPConnection();
		ftp.changeDirectory(ftp.getRoot());
		if(copyLib && !ftp.changeDirectory(getLibRoot())) {
			System.out.println(""); //Fix 03679
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoLibRoot",  new String[] {getLibRoot()}));//Mod. 9634//"ATTENZIONE: LibRoot inesistente: " + getLibRoot()); //Fix 03679
			closeFTPConnection(ftp);
			return false;
		}
		ftp.changeDirectory(ftp.getRoot());
		if(copyWeb && !ftp.changeDirectory(getWebRoot())) {
			System.out.println(""); //Fix 03679
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoWebRoot",  new String[] {getWebRoot()}));//Mod. 9634//"ATTENZIONE: WebRoot inesistente: " + getWebRoot()); //Fix 03679
			closeFTPConnection(ftp);
			return false;
		}
		ftp.changeDirectory(ftp.getRoot());
		if(copyRpt && !ftp.changeDirectory(getRptRoot())) {
			System.out.println(""); //Fix 03679
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoRptRoot",  new String[] {getRptRoot()}));//Mod. 9634//"ATTENZIONE: RptRoot inesistente: " + getRptRoot()); //Fix 03679
			closeFTPConnection(ftp);
			return false;
		}
		ftp.changeDirectory(ftp.getRoot());
		if(copyUser && !ftp.changeDirectory(getUserRoot())) {
			System.out.println(""); //Fix 03679
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoUserRoot",  new String[] {getUserRoot()}));//Mod. 9634//"ATTENZIONE: RptRoot inesistente: " + getUserRoot()); //Fix 03679
			closeFTPConnection(ftp);
			return false;
		}
		ftp.changeDirectory(ftp.getRoot());
		if(copyServer && !ftp.changeDirectory(getServerRoot())) {
			System.out.println(""); //Fix 03679
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoServerRoot",  new String[] {getServerRoot()}));//Mod. 9634//"ATTENZIONE: ServerRoot inesistente: " + getServerRoot()); //Fix 03679
			closeFTPConnection(ftp);
			return false;
		}
		ftp.changeDirectory(ftp.getRoot());
		if(copyClient && !ftp.changeDirectory(getClientRoot())) {
			System.out.println(""); //Fix 03679
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoClientRoot",  new String[] {getClientRoot()}));//Mod. 9634//"ATTENZIONE: ClientRoot inesistente: " + getClientRoot()); //Fix 03679
			closeFTPConnection(ftp);
			return false;
		}
		ftp.changeDirectory(ftp.getRoot());
		if(copyReports && !ftp.changeDirectory(getReportsRoot())) {
			System.out.println(""); //Fix 03679
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoReportsRoot",  new String[] {getReportsRoot()}));//Mod. 9634//"ATTENZIONE: ReportsRoot inesistente: " + //Fix 03679
			closeFTPConnection(ftp);
			return false;
		}

		closeFTPConnection(ftp);
		return true;
	}

	//Mod.2967 - fine

	/**Mod. 905
	 * getFlagPosition(String flag, String[] params).
	 * @param String flag
	 * @return int
	 */
	//Fix 03679 MM
	//  public int getFlagPosition(String flag, String[] params) {
	public static int getFlagPosition(String flag, String[] params) {
		for (int i = 2; i < params.length; i++) {
			if(params[i].equals(flag))
				return i;
		}
		return params.length;
	}

	/**Mod. 905
	 * extractParam(int pos, String[] params).
	 * @param int pos
	 * @return String
	 */
	//Fix 03679 MM
	public static String extractParam(int pos, String[] params) {
		if(pos < params.length) {
			if(pos + 1 < params.length)
				return params[pos + 1];
		}
		return null;
	}

	/**
	 * Legge da Thermfw.ini la password per l'utente del database
	 * @param dbName
	 * @return
	 * @throws SQLException
	 */
	/* Revisions:
	 * Fix #    Date          Owner      Description
	 *  9428    24/ 6/2008    Ryo        Prima versione
	 */
	public static String readThermPwd(/*String dbName, */String thermUserName) throws SQLException
	{
		//		Security.setCurrentDatabase(dbName, null);
		//		Security.openDefaultConnection();
		User u = (User)Factory.createObject(User.class);
		u.setKey(thermUserName);
		u.retrieve();
		String thermPwd = u.getPwd();
		return thermPwd;
	}

	/**
	 * Metohd: extractAllAvaApplications().
	 * Mod. 905
	 */
	public void extractAllAvaApplications() {
		// Controllo esistenza file
		BufferedReader appsFile = checkAppsFilePresence();
		if(appsFile != null) {
			try {
				// Carico righe del file in avaApps
				avaApps.removeAllElements();
				String line = appsFile.readLine();
				while (line != null) {
					line = line.trim();
					if(!line.startsWith(FileProcessor.COMMENT_TAG) && (!line.equals("")))
						avaApps.addElement(line);
					line = appsFile.readLine();
				}
				appsFile.close();
			}
			catch (IOException e2) {
				System.out.println(ResourceLoader.getString(SETUP_RES, "noReadFromApplFile"));//Mod. 9634//"Impossibile leggere dal file applicazioni"); //Fix 03679
				System.out.println(e2.getMessage()); //Fix 03679
			}
		}
	}

	/**Mod.1315 - 2226
	 * extractPaths(int pos, String[] params).
	 * @param int pos
	 * @return boolean
	 */
	public boolean extractPaths(int pos, String[] params) {
		boolean ret = false;
		if(pos < params.length) {
			if(pos + 4 < params.length) {
				if(ret = isAPathParam(params[pos + 1])) {
					setLibRoot(params[pos + 1]);
					if(ret = isAPathParam(params[pos + 2])) {
						setWebRoot(params[pos + 2]);
						if(ret = isAPathParam(params[pos + 3])) {
							setRptRoot(params[pos + 3]);
							if(ret = isAPathParam(params[pos + 4]))
								setUserRoot(params[pos + 4]);
						}
					}
				}
			}
		}
		return ret;
	}

	/**Mod.2258
	 * extractPaths1(int pos, String[] params).
	 * @param int pos
	 * @return boolean
	 */
	public boolean extractPaths1(int pos, String[] params) {
		boolean ret = false;
		if(pos < params.length) {
			if(pos + 3 < params.length) {
				if(ret = isAPathParam(params[pos + 1])) {
					setServerRoot(params[pos + 1]);
					if(ret = isAPathParam(params[pos + 2])) {
						setClientRoot(params[pos + 2]);
						if(ret = isAPathParam(params[pos + 3])) {
							setReportsRoot(params[pos + 3]);
						}
					}
				}
			}
		}
		return ret;
	}

	//Mod. 13978 inizio - Faccio metodo separato per rendere indipendenti i gruppi di parametri
	/**Mod.2258
	 * extractPaths2(int pos, String[] params).
	 * @param int pos
	 * @return boolean
	 */
	public boolean extractPaths2(int pos, String[] params) {
		boolean ret = false;
		if(pos < params.length) {
			if(pos + 3 < params.length) {
				if(ret = isAPathParam(params[pos + 1])) {
					setServerRoot2(params[pos + 1]);
					if(ret = isAPathParam(params[pos + 2])) {
						setClientRoot2(params[pos + 2]);
						if(ret = isAPathParam(params[pos + 3])) {
							setReportsRoot2(params[pos + 3]);
						}
					}
				}
			}
		}
		return ret;
	}
	//fine mod. 13978

	/**Mod.2967
	 * extractFTPParams(int pos, String[] params).
	 * @param int pos
	 * @return boolean
	 */
	public boolean extractFTPParams(int pos, String[] params) {
		boolean ret = false;
		if(pos < params.length) {
			if(pos + 4 < params.length) {
				if(ret = isAPathParam(params[pos + 1])) {
					setFtpServerHost(params[pos + 1]);
					if(ret = isAPathParam(params[pos + 2])) {
						setFtpServerPort(Integer.parseInt(params[pos + 2]));
						if(ret = isAPathParam(params[pos + 3])) {
							setFtpUserName(params[pos + 3]);
							if(ret = isAPathParam(params[pos + 4]))
								setFtpUserPassword(params[pos + 4]);
						}
					}
				}
			}
		}
		return ret;
	}

	//Mod.2967
	//Fix 03679 MM
	//private boolean isAPathParam(String s) {
	public static boolean isAPathParam(String s) {
		for (int i = 0; i < NO_PATH_PARAMS.length; i++)
			if(s.equals(NO_PATH_PARAMS[i]))
				return false;
		return true;
	}

	/**
	 * checkAppsFilePresence().
	 * @return LineNumberReader
	 */
	public BufferedReader checkAppsFilePresence() {
		BufferedReader appsFile = null;
		//07935
		//String appsFileName = APPS_FILE;
		//appsFile = getFile(appsFileName);
		appsFile = getApplicationFile();
		return appsFile;
	}

	/**
	 * extractApplications(int appsPos).
	 * @param int appsPos
	 * @return boolean
	 * Mod.905
	 */
	public boolean extractApplications(int appsPos, String[] params) {
		apps.removeAllElements();

		// Se il parametro -A non c'è non carico le applicazioni
		if(appsPos == params.length)
			return true;

		// Carico applicazioni per cui eseguire il setup
		Vector tmp = new Vector();
		for (int i = appsPos + 1; i < params.length; i++)
			tmp.addElement(params[i]);

		// Se il parametro è -A all carico tutte le applicazioni disponibili
		if(tmp.contains(ALL_NAME)) {
			apps.addAll(avaApps);
		}
		else {
			// Carico le applicazioni indicate come parametri
			for (int i = 0; i < tmp.size(); i++) {
				String app = (String) tmp.elementAt(i);
				if(avaApps.contains(app))
					apps.add(app);
			}
			// Nessuna applicazione indicata come parametro
			if(!(apps.size() > 0)) {
				System.out.println(ResourceLoader.getString(SETUP_RES, "NoApplSet"));//Mod. 9634//"Nessuna applicazione è stata impostata"); //Fix 03679
				return false;
			}
		}
		return true;
	}

	/**
	 * Mod.2967
	 * CopyFixFile: copia i file contenuti nella fix.
	 * @return boolean
	 */
	public boolean copyFixFile(String nrFix, String pathFix) {
		if(ftpCopyFiles)
			return copyFixFile_WithFTP(nrFix, pathFix);
		else
			return copyFixFile_WithFileSystem(nrFix, pathFix);
	}

	//Mod. 15040 inizio
	/**
		* Copia dei file RPT
		* @param String fRoot root della fix
		* @param boolean rpt_present: true se nella fix esiste la directory per i file .RPT per Crystal Reports ver. 8.5
		* @param boolean rptCR2008_present: true se esiste la directory  per i file .RPT per Crystal Reports ver. 2008
		*    in Thip questo parametro viene ignorato, quando si copiano i file .rpt per CR85, vengono copiati anche quelli x CR2008
		* @return boolean
	 */
		public boolean copyRPT(String fRoot, boolean rpt_present, boolean rptCR2008_present) {
			 try {
			 // Copio i report dalla directory opportuna in funzione della versione di Crystal R.
			 //Mod. 11998 inizio
			 //Se è in uso la versione 8.5 di CrystalR
			 if (copyRpt && (rpt_present || rptCR2008_present)) { //Mod. 12828
				 if (crystalVer == ApplicationPreferences.CRYSTALR_VER_8_5) {
					 //Se non esiste la directory specifica per la 8.5 (CR85_PREFIX)==> la destinazione è la dir root (print, RPT, ...)
					 if (!FileUtil.exists(this.getRptRoot() + "/" + PrintingToolInterface.CR85_PREFIX)) {
						 output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_RPT, getRptRoot()}));
						 resetBackupDir(getRptRoot(), 4);

						 FileUtil.copyTree(fRoot + NAME_FIX_RPT, getRptRoot(), "*.*");
					 }
					 //Se esiste la directory specifica per la 8.5 (CR85_PREFIX) ==>  le destinazioni sono:
					 // - la root specifica CR85_PREFIX per i file RPT di tipo CrystalReport 8.5
					 // - la root specifica CR2008_PREFIX, se esiste, per i file RPT di tipo CrystalReport 2008
					 else {
						 output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_RPT,
								 getRptRoot() + "/" + PrintingToolInterface.CR85_PREFIX}));
						 resetBackupDir(getRptRoot() + "/" + PrintingToolInterface.CR85_PREFIX, 4);

						 //
						 FileUtil.copyTree(fRoot + NAME_FIX_RPT, getRptRoot() + "/" + PrintingToolInterface.CR85_PREFIX, "*.*");

						 if (FileUtil.exists(getRptRoot() + "/" + PrintingToolInterface.CR2008_PREFIX)) {
							 output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_RPT_CR2008,
									 getRptRoot() + "/" + PrintingToolInterface.CR2008_PREFIX}));
							 resetBackupDir(getRptRoot() + "/" + PrintingToolInterface.CR2008_PREFIX, 4);
							 FileUtil.copyTree(fRoot + NAME_FIX_RPT_CR2008, getRptRoot() + "/" + PrintingToolInterface.CR2008_PREFIX, "*.*");
						 }
					 }
				 }
				 //Se è in uso la versione 2008 di CrystalR
				 else if (crystalVer == ApplicationPreferences.CRYSTALR_VER_2008) {
					 if (!FileUtil.exists(getRptRoot() + "/" + PrintingToolInterface.CR2008_PREFIX)) {
						 output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {NAME_FIX_RPT_CR2008, getRptRoot()}));
						 resetBackupDir(getRptRoot(), 4);
						 FileUtil.copyTree(fRoot + NAME_FIX_RPT_CR2008, getRptRoot(), "*.*");
					 }
					 else {
						 output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_RPT_CR2008,
								 getRptRoot() + "/" + PrintingToolInterface.CR2008_PREFIX}));
						 resetBackupDir(getRptRoot() + "/" + PrintingToolInterface.CR2008_PREFIX, 4);
						 FileUtil.copyTree(fRoot + NAME_FIX_RPT_CR2008, getRptRoot() + "/" + PrintingToolInterface.CR2008_PREFIX, "*.*");
						 if (FileUtil.exists(getRptRoot() + "/" + PrintingToolInterface.CR85_PREFIX)) {
							 output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
									 new String[] {fRoot +
									 NAME_FIX_RPT, getRptRoot() + "/" + PrintingToolInterface.CR85_PREFIX}));
							 resetBackupDir(getRptRoot() + "/" + PrintingToolInterface.CR85_PREFIX, 4);
							 FileUtil.copyTree(fRoot + NAME_FIX_RPT, getRptRoot() + "/" + PrintingToolInterface.CR85_PREFIX, "*.*");
						 }
					 }

				 }
			 } //Mod. 12828

			 } catch (Exception e) {
//      output.println("********** #ERRORE# **********");	// Fix 4418 Ryo
				 output.println(ResourceLoader.getString(SETUP_RES, "ErrorString")); // Fix 4418 Ryo//Mod. 12771
				 output.println(e.getMessage());
				 return false;
			 }
			 return true;
			 }
	//fine mod. 15040

	/**
	 * Copia i file contenuti nella fix.
	 * @return boolean
	 */
	/* Revisions:
	 * Fix nr   Date          Owner      Description
	 *  4418    10/10/2005    Ryo        Gestito l'upgrade dei jar di primrose
	 *  5090    23/ 2/2006    Ryo        Corretto l'output dell'aggiornamento dei file jar
	 *  8892    19/ 3/2008    Ryo        Modificato il valore di ritorno
	 *  9802    19/ 9/2008    Ryo        Nella copia dei file .java, FixFileProcessor copia anche i file .tfml
	 */
	public boolean copyFixFile_WithFileSystem(String nrFix, String pathFix) {
		boolean righto = true;	// Fix 8892 Ryo
		try {
			//String fRoot = getFixRoot()+File.separator+nrFix+File.separator;
			String fRoot = pathFix + File.separator; //Mod.2193
			boolean lib_present = FileUtil.exists(fRoot + NAME_FIX_LIB);
			boolean web_present = FileUtil.exists(fRoot + NAME_FIX_WEB);
			boolean rpt_present = FileUtil.exists(fRoot + NAME_FIX_RPT);
			boolean rptCR2008_present = FileUtil.exists(fRoot + NAME_FIX_RPT_CR2008);//Mod. 11234
			//Mod.2258 - ini
			boolean server_present = FileUtil.exists(fRoot + NAME_FIX_SERVER);
			boolean client_present = FileUtil.exists(fRoot + NAME_FIX_CLIENT);
			boolean reports_present = FileUtil.exists(fRoot + NAME_FIX_REPORTS);
			boolean serverdiff_present = FileUtil.exists(fRoot + NAME_FIX_SERVER_DIFF); // Fix 4418 Ryo

			//Mod.2258 - fin

			//Mod.2226 - ini
			boolean usr_present = false;
			if(nameUserDir != null && !nameUserDir.equals(""))
				usr_present = FileUtil.exists(fRoot + nameUserDir);
			//Mod.2226 - fin

			if((copyLib && lib_present) || (copyWeb && web_present) ||
					(copyRpt && (rpt_present || rptCR2008_present)) || (copyUser && usr_present) ||
					(copyServer && server_present) || (copyClient && client_present) ||
					(copyReports && reports_present)) { //Mod.2258 + 11234
				FileUtil.setBackups(backupsFiles);
				output.println("");
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFiles",
						new String[] {nrFix}));
			}
			// Copio la cartella lib - class/properties/img
			if(copyLib && lib_present) {
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
						new String[] {fRoot +
						NAME_FIX_LIB, getLibRoot()}));
				resetBackupDir(getLibRoot(), 0);
				FileUtil.copyTree(fRoot + NAME_FIX_LIB, getLibRoot(), "*.*");
			}
			// Copio la cartella web - tranne i file TFML
			if(copyWeb) {
				if(web_present) {
					output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
							new String[] {fRoot +
							NAME_FIX_WEB, getWebRoot()}));
					resetBackupDir(getWebRoot(), 1);

					// Fix 9802 Ryo ->
					//					FileUtil.copyTreeExcPattern(fRoot + NAME_FIX_WEB, getWebRoot(), "*.tfml");
					if(copyUser && usr_present)
					{
						FileUtil.copyTree(fRoot + NAME_FIX_WEB, getWebRoot(), "*.*");
					}
					else
					{
						FileUtil.copyTreeExcPattern(fRoot + NAME_FIX_WEB, getWebRoot(), "*.tfml");
					}
					// Fix 9802 Ryo <-

				}
				// Copio le classi in web-inf/classes
				if(lib_present) {
					String webclassesroot = getWebRoot() + File.separator +
					NAME_APP_WEBINF + File.separator + NAME_APP_WEBCLASSES;
					output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
							new String[] {fRoot +
							NAME_FIX_LIB, webclassesroot}));
					resetBackupDir(webclassesroot, 2);
					FileUtil.copyTree(fRoot + NAME_FIX_LIB, webclassesroot, "*.*");
				}
				// Copio le librerie in web-inf/lib
				if(lib_present) {
					String weblibroot = getWebRoot() + File.separator + NAME_APP_WEBINF +
					File.separator + NAME_APP_WEBLIB;
					output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
							new String[] {fRoot +
							NAME_FIX_LIB, weblibroot}));
					resetBackupDir(weblibroot, 3);
					String[] s = {
							"*.jar", "*.zip"};
					FileUtil.copyFiles(fRoot + NAME_FIX_LIB, weblibroot, s);
				}
			}
			// Copio i report dalla directory opportuna in funzione della versione di Crystal R.
			//Mod. 11998 inizio
			//Se è in uso la versione 8.5 di CrystalR
			/* //Mod. 15040 inizio (estraggo questa parte e la metto nel metodo copyRPT
			if (copyRpt && (rpt_present || rptCR2008_present)) { //Mod. 12828
				if (crystalVer == ApplicationPreferences.CRYSTALR_VER_8_5) {
					//Se non esiste la directory specifica per la 8.5 (CR85_PREFIX)==> la destinazione è la dir root (print, RPT, ...)
					if (!FileUtil.exists(this.getRptRoot() + "/" + PrintingToolInterface.CR85_PREFIX)) {
						output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_RPT, getRptRoot()}));
						resetBackupDir(getRptRoot(), 4);

						FileUtil.copyTree(fRoot + NAME_FIX_RPT, getRptRoot(), "*.*");
					}
					//Se esiste la directory specifica per la 8.5 (CR85_PREFIX) ==>  le destinazioni sono:
					// - la root specifica CR85_PREFIX per i file RPT di tipo CrystalReport 8.5
					// - la root specifica CR2008_PREFIX, se esiste, per i file RPT di tipo CrystalReport 2008
					else {
						output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_RPT,                getRptRoot() + "/" + PrintingToolInterface.CR85_PREFIX}));
						resetBackupDir(getRptRoot() + "/" + PrintingToolInterface.CR85_PREFIX, 4);

						//
						FileUtil.copyTree(fRoot + NAME_FIX_RPT, getRptRoot() + "/" + PrintingToolInterface.CR85_PREFIX, "*.*");

						if (FileUtil.exists(getRptRoot() + "/" + PrintingToolInterface.CR2008_PREFIX)) {
							output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_RPT_CR2008,                  getRptRoot() + "/" + PrintingToolInterface.CR2008_PREFIX}));
							resetBackupDir(getRptRoot() + "/" + PrintingToolInterface.CR2008_PREFIX, 4);
							FileUtil.copyTree(fRoot + NAME_FIX_RPT_CR2008, getRptRoot() + "/" + PrintingToolInterface.CR2008_PREFIX, "*.*");
						}
					}
				}
				//Se è in uso la versione 2008 di CrystalR
				else if (crystalVer == ApplicationPreferences.CRYSTALR_VER_2008) {
					if (!FileUtil.exists(getRptRoot() + "/" + PrintingToolInterface.CR2008_PREFIX)) {
						output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {NAME_FIX_RPT_CR2008, getRptRoot()}));
						resetBackupDir(getRptRoot(), 4);
						FileUtil.copyTree(fRoot + NAME_FIX_RPT_CR2008, getRptRoot(), "*.*");
					}
					else {
						output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_RPT_CR2008,                getRptRoot() + "/" + PrintingToolInterface.CR2008_PREFIX}));
						resetBackupDir(getRptRoot() + "/" + PrintingToolInterface.CR2008_PREFIX, 4);
						FileUtil.copyTree(fRoot + NAME_FIX_RPT_CR2008, getRptRoot() + "/" + PrintingToolInterface.CR2008_PREFIX, "*.*");
						if (FileUtil.exists(getRptRoot() + "/" + PrintingToolInterface.CR85_PREFIX)) {
							output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
									new String[] {fRoot +
									NAME_FIX_RPT, getRptRoot() + "/" + PrintingToolInterface.CR85_PREFIX}));
							resetBackupDir(getRptRoot() + "/" + PrintingToolInterface.CR85_PREFIX, 4);
							FileUtil.copyTree(fRoot + NAME_FIX_RPT, getRptRoot() + "/" + PrintingToolInterface.CR85_PREFIX, "*.*");
						}
					}

				}
			} //Mod. 12828
			*/
			if (!copyRPT(fRoot, rpt_present, rptCR2008_present))
				return false;
			//fine mod. 15040

			//Mod.2258 - ini
			// Copio la cartella Server
			if(copyServer && server_present) {
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
						new String[] {fRoot +
						NAME_FIX_SERVER, getServerRoot()}));
				resetBackupDir(getServerRoot(), 5);
				FileUtil.copyTree(fRoot + NAME_FIX_SERVER, getServerRoot(), "*.*");
			}
			// Copio la cartella Client
			if(copyClient && client_present) {
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
						new String[] {fRoot +
						NAME_FIX_CLIENT, getClientRoot()}));
				resetBackupDir(getClientRoot(), 6);
				FileUtil.copyTree(fRoot + NAME_FIX_CLIENT, getClientRoot(), "*.*");
			}

			// Fix 4418 Ryo ->

			// Copio la directory serverdiff
			if(copyServer && serverdiff_present) {
				//				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_SERVER_DIFF, getClientRoot()}));	// Fix 5090 Ryo
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] { fRoot + NAME_FIX_SERVER_DIFF, getServerRoot() }));	// Fix 5090 Ryo
				resetBackupDir(getServerRoot(), 6);
				String[] files = FileUtil.list(fRoot + NAME_FIX_SERVER_DIFF, "*.zip");
				//				primroseJarBlender(files, fRoot, getServerRoot(), true);
				righto = primroseJarBlender(files, fRoot, getServerRoot(), true);
			}

			// Fix 4418 Ryo <-

			// Copio la cartella Reports
			if(copyReports && reports_present) {
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
						new String[] {fRoot +
						NAME_FIX_REPORTS,
						getReportsRoot()}));
				resetBackupDir(getReportsRoot(), 7);
				FileUtil.copyTree(fRoot + NAME_FIX_REPORTS, getReportsRoot(), "*.*");
			}
			//Mod.2258 - fin


			// Mod.2226 - Copio i file utente
			if(copyUser && usr_present) {
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
						new String[] {fRoot +
						nameUserDir, getUserRoot()}));
				resetBackupDir(getUserRoot(), 8);
				FileUtil.copyTree(fRoot + nameUserDir, getUserRoot(), "*.*");
			}

			//9044 CB inizio
			if (copyFiles1) { //Mod. 13978
			if(!copyClient && client_present){
				output.println(ResourceLoader.getString(SETUP_RES, "ErrorString"));//Mod. 12771
				output.println(ResourceLoader.getString(SETUP_RES, "CopyPrimroseFolders",
						new String[] {NAME_FIX_CLIENT}));
				return false;
			}
			if(!copyServer && server_present){
				output.println(ResourceLoader.getString(SETUP_RES, "ErrorString"));//Mod. 12771
				output.println(ResourceLoader.getString(SETUP_RES, "CopyPrimroseFolders",
						new String[] {NAME_FIX_SERVER}));
				return false;
			}
			if(!copyServer && serverdiff_present){
				output.println(ResourceLoader.getString(SETUP_RES, "ErrorString"));//Mod. 12771
				output.println(ResourceLoader.getString(SETUP_RES, "CopyPrimroseFolders",
						new String[] {NAME_FIX_SERVER_DIFF}));
				return false;
			}
			if(!copyReports && reports_present){
				output.println(ResourceLoader.getString(SETUP_RES, "ErrorString"));//Mod. 12771
				output.println(ResourceLoader.getString(SETUP_RES, "CopyPrimroseFolders",
						new String[] {NAME_FIX_REPORTS}));
				return false;
			}
			} //Mod. 13978

			//Mod. 13978 inizio
			//Eseguo le copie per il parametro -CF2
				righto = copy_CF2(righto, fRoot, pathFix, nrFix);
			//fine mod. 13978
		}
		catch (Exception e) {
			//      output.println("********** #ERRORE# **********");	// Fix 4418 Ryo
			output.println(ResourceLoader.getString(SETUP_RES, "ErrorString")); // Fix 4418 Ryo//Mod. 12771
			output.println(e.getMessage());
			return false;
		}
		//		return true;


		return righto;
	}

	// Fix 4418 Ryo ->

	/**
	 * Dato un nome di file, lo restituisce senza estensione.
	 */
	/* Revisions:
	 * Fix nr   Date          Owner      Description
	 *  4418    10/10/2005    Ryo        Prima versione
	 */
	private String getFileNameWithoutExtension(String fileNameWithExtension) {
		final String DOT = ".";
		StringTokenizer fileOrigin = new StringTokenizer(fileNameWithExtension, DOT);
		String fileNameWithoutExtension = "";
		for (int token = 1; token <= fileOrigin.countTokens(); token++) {
			fileNameWithoutExtension += fileOrigin.nextToken();
			if(token < fileOrigin.countTokens()) {
				fileNameWithoutExtension += DOT;
			}
		}
		return fileNameWithoutExtension;
	}

	/**
	 * Esegue FileUtil.jarBlend e gestisce gli eventuali errori.
	 */
	/* Revisions:
	 * Fix nr   Date          Owner      Description
	 *  4418    10/10/2005    Ryo        Prima versione
	 *  5090    23/ 2/2006    Ryo        Cambiato NO_LAST_MODIFIED_SET da ERROR a WARNING
	 *  8892    19/ 3/2008    Ryo        Signature modificata
	 */
	//	private void primroseJarBlender(String[] files, String srcRoot, String dstRoot, boolean isTempDirToDelete) throws Exception	// Fix 8892 Ryo
	private boolean primroseJarBlender(String[] files, String srcRoot, String dstRoot, boolean isTempDirToDelete) throws Exception		// Fix 8892 Ryo
	{
		boolean righto = true;
		for(int file = 0; file < files.length; file++)
		{
			String nameFile = getFileNameWithoutExtension(files[file]);
			String srcFile = srcRoot + NAME_FIX_SERVER_DIFF + File.separatorChar + nameFile + ".zip";
			String dstFile = dstRoot + File.separatorChar + nameFile + ".jar";
			int result = FileUtil.jarBlend(srcFile, dstFile, srcRoot + NAME_FIX_SERVER_DIFF + File.separatorChar + "tempBlend", isTempDirToDelete);
			if(result != 0)
			{
				String errorHeader = "";	// Fix 5090 Ryo
				String errorFooter = "";
				switch (result)
				{
					case FileUtil.NO_DESTINATION_FILE:
					{
						errorHeader = ResourceLoader.getString(SETUP_RES, "ErrorString");	// Fix 5090 Ryo//Mod. 12771
						errorFooter = ResourceLoader.getString(SETUP_RES, "NoFileFoundErr");//Mod. 9634//"File non trovato";
						righto = false;	// Fix 8892 Ryo
						break;
					}
					case FileUtil.NO_TEMPORARY_DIRECTORY:
					{
						errorHeader = ResourceLoader.getString(SETUP_RES, "ErrorString");	// Fix 5090 Ryo//Mod. 12771
						errorFooter = ResourceLoader.getString(SETUP_RES, "DirCreationErr");//Mod. 9634//"Errore durante la creazione della directory temporanea per la decompressione del file";
						righto = false;	// Fix 8892 Ryo
						break;
					}
					case FileUtil.NO_LAST_MODIFIED_SET:
					{
						errorHeader = ResourceLoader.getString(SETUP_RES, "WarningString");	// Fix 5090 Ryo//Mod. 12771
						errorFooter = ResourceLoader.getString(SETUP_RES, "LastUpdDateErr");//Mod. 9634//"Errore durante l'impostazione della data di ultima modifica";
						break;	// Fix 8892 Ryo
					}
					//				Fix 8892 Ryo ->
					case FileUtil.FILE_IN_USE:
					{
						errorHeader = ResourceLoader.getString(SETUP_RES, "ErrorString");//Mod. 12771
						errorFooter = ResourceLoader.getString(SETUP_RES, "FileInUseErr");//Mod. 9634//"Errore durante l'aggiornamento: file in uso";
						righto = false;
					}
					//				Fix 8892 Ryo <-
				}
				//				String error = ResourceLoader.getString(SETUP_RES, "ErrorString") + "\n" + dstFile + ": " + errorFooter;	// Fix 5090 Ryo
				String error = errorHeader + "\n" + dstFile + ": " + errorFooter;	// Fix 5090 Ryo
				//				System.out.println(error);	// Fix 8892 Ryo
				output.println(error);
			}
		}
		return righto;
	}

	// Fix 4418 Ryo <-

	//Mod. 13978 inizio
	/**
	 * Metodo simile a primroseJarBlender a cui devo passare anche il nome della directory (senza path)
	 * contenente il file sorgente, da utilizzare per aggiornare il jar. Serve un nuovo metodo perché
	 * primroseJarBlender ha cablato come nome della directory: NAME_FIX_SERVER_DIFF
	 * *** Per una soluzione più elegante: nel vecchio metodo usare questo passandogli NAME_FIX_SERVER_DIFF
	 * Esegue FileUtil.jarBlend e gestisce gli eventuali errori.
	 */
	private boolean genericPrimroseJarBlender(String[] files, String srcRoot, String dstRoot, String dirName, boolean isTempDirToDelete) throws Exception		// Fix 8892 Ryo
	{
		boolean righto = true;
		for(int file = 0; file < files.length; file++)
		{
			String nameFile = getFileNameWithoutExtension(files[file]);
			String srcFile = srcRoot + dirName + File.separatorChar + nameFile + ".zip"; //Mod. 13978: parametro invece del nome cablato
			String dstFile = dstRoot + File.separatorChar + nameFile + ".jar";
			int result = FileUtil.jarBlend(srcFile, dstFile, srcRoot + dirName + File.separatorChar + "tempBlend", isTempDirToDelete); //Mod. 13978
			if(result != 0)
			{
				String errorHeader = "";	// Fix 5090 Ryo
				String errorFooter = "";
				switch (result)
				{
					case FileUtil.NO_DESTINATION_FILE:
					{
						errorHeader = ResourceLoader.getString(SETUP_RES, "ErrorString");	// Fix 5090 Ryo//Mod. 12771
						errorFooter = ResourceLoader.getString(SETUP_RES, "NoFileFoundErr");//Mod. 9634//"File non trovato";
						righto = false;	// Fix 8892 Ryo
						break;
					}
					case FileUtil.NO_TEMPORARY_DIRECTORY:
					{
						errorHeader = ResourceLoader.getString(SETUP_RES, "ErrorString");	// Fix 5090 Ryo//Mod. 12771
						errorFooter = ResourceLoader.getString(SETUP_RES, "DirCreationErr");//Mod. 9634//"Errore durante la creazione della directory temporanea per la decompressione del file";
						righto = false;	// Fix 8892 Ryo
						break;
					}
					case FileUtil.NO_LAST_MODIFIED_SET:
					{
						errorHeader = ResourceLoader.getString(SETUP_RES, "WarningString");	// Fix 5090 Ryo//Mod. 12771
						errorFooter = ResourceLoader.getString(SETUP_RES, "LastUpdDateErr");//Mod. 9634//"Errore durante l'impostazione della data di ultima modifica";
						break;	// Fix 8892 Ryo
					}
					//				Fix 8892 Ryo ->
					case FileUtil.FILE_IN_USE:
					{
						errorHeader = ResourceLoader.getString(SETUP_RES, "ErrorString");//Mod. 12771
						errorFooter = ResourceLoader.getString(SETUP_RES, "FileInUseErr");//Mod. 9634//"Errore durante l'aggiornamento: file in uso";
						righto = false;
					}
					//				Fix 8892 Ryo <-
				}
				//				String error = ResourceLoader.getString(SETUP_RES, "ErrorString") + "\n" + dstFile + ": " + errorFooter;	// Fix 5090 Ryo
				String error = errorHeader + "\n" + dstFile + ": " + errorFooter;	// Fix 5090 Ryo
				//				System.out.println(error);	// Fix 8892 Ryo
				output.println(error);
			}
		}
		return righto;
	}
	//fine mod. 13978

	/**
	 * Mod.2967
	 * CopyFixFile_WithFTP: copia i file contenuti nella fix.
	 * @return boolean
	 */
	/* Revisions:
	 * Fix nr   Date          Owner      Description
	 * 04418    10/10/2005    Ryo        Gestito l'upgrade dei jar di primrose
	 */
	public boolean copyFixFile_WithFTP(String nrFix, String pathFix) {
		FTPConnection ftp = null;
		try {
			//Controllo percorsi Client
			String fRoot = pathFix + File.separator;
			boolean lib_present = FileUtil.exists(fRoot + NAME_FIX_LIB);
			boolean web_present = FileUtil.exists(fRoot + NAME_FIX_WEB);
			boolean rpt_present = FileUtil.exists(fRoot + NAME_FIX_RPT);
			boolean rptCR2008_present = FileUtil.exists(fRoot + NAME_FIX_RPT); //Mod. 11234
			boolean server_present = FileUtil.exists(fRoot + NAME_FIX_SERVER);
			boolean client_present = FileUtil.exists(fRoot + NAME_FIX_CLIENT);
			boolean reports_present = FileUtil.exists(fRoot + NAME_FIX_REPORTS);
			boolean serverdiff_present = FileUtil.exists(fRoot + NAME_FIX_SERVER_DIFF); // Fix 4418 Ryo
			boolean usr_present = false;
			if(nameUserDir != null && !nameUserDir.equals(""))
				usr_present = FileUtil.exists(fRoot + nameUserDir);

			//Open FTP Connection
			ftp = openFTPConnection();

			if((copyLib && lib_present) || (copyWeb && web_present) ||
					(copyRpt && rpt_present) || (copyUser && usr_present) ||
					(copyServer && server_present) || (copyClient && client_present) ||
					(copyReports && reports_present)) {
				output.println("");
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFiles",
						new String[] {nrFix}));
				ftp.setBackupFiles(backupsFiles);
			}
			// UPLOAD directory lib - class/properties/img
			if(copyLib && lib_present) {
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
						new String[] {fRoot +
						NAME_FIX_LIB, getLibRoot()}));
				resetBackupDirFTP(ftp, getLibRoot(), 0);
				ftp.uploadTree(fRoot + NAME_FIX_LIB);
			}

			// UPLOAD directory web - tranne i file TFML
			if(copyWeb) {
				if(web_present) {
					output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
							new String[] {fRoot +
							NAME_FIX_WEB, getWebRoot()}));
					resetBackupDirFTP(ftp, getWebRoot(), 1);
					ftp.uploadTreeExcFilter(fRoot + NAME_FIX_WEB, "*.tfml");
				}
				// Copio le classi in web-inf/classes
				if(lib_present) {
					String webclassesroot = getWebRoot() + File.separator +
					NAME_APP_WEBINF + File.separator + NAME_APP_WEBCLASSES;
					output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
							new String[] {fRoot +
							NAME_FIX_LIB, webclassesroot}));
					resetBackupDirFTP(ftp, webclassesroot, 2);
					ftp.uploadTree(fRoot + NAME_FIX_LIB);
				}
				// Copio le librerie in web-inf/lib
				if(lib_present) {
					String weblibroot = getWebRoot() + File.separator + NAME_APP_WEBINF +
					File.separator + NAME_APP_WEBLIB;
					output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
							new String[] {fRoot +
							NAME_FIX_LIB, weblibroot}));
					String[] s = {
							"*.jar", "*.zip"};
					resetBackupDirFTP(ftp, weblibroot, 3);
					ftp.uploadFiles(fRoot + NAME_FIX_LIB, s);
				}
			}

			// UPLOAD dei report dalla directory opportuna in base alla versione di Crystal impostata
			if(copyRpt && (rpt_present || rptCR2008_present)) { //Mod. 11234
				if(this.crystalVer == ApplicationPreferences.CRYSTALR_VER_8_5) { //Mod. 11234
					output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
							new String[] {fRoot +
							NAME_FIX_RPT, getRptRoot()}));
					resetBackupDirFTP(ftp, getRptRoot(), 4);
					ftp.uploadTree(fRoot + NAME_FIX_RPT);
					//Mod. 11234 inizio
				}
				else if(crystalVer == ApplicationPreferences.CRYSTALR_VER_2008) {
					output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
							new String[] {fRoot +
							NAME_FIX_RPT_CR2008, getRptRoot()}));
					resetBackupDirFTP(ftp, getRptRoot(), 4);
					ftp.uploadTree(fRoot + NAME_FIX_RPT_CR2008);
				} // fine mod. 11234
			}

			// UPLOAD directory Server
			if(copyServer && server_present) {
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
						new String[] {fRoot +
						NAME_FIX_SERVER, getServerRoot()}));
				resetBackupDirFTP(ftp, getServerRoot(), 5);
				ftp.uploadTree(fRoot + NAME_FIX_SERVER);
			}
			// UPLOAD directory Client
			if(copyClient && client_present) {
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
						new String[] {fRoot +
						NAME_FIX_CLIENT, getClientRoot()}));
				resetBackupDirFTP(ftp, getClientRoot(), 6);
				ftp.uploadTree(fRoot + NAME_FIX_CLIENT);
			}

			// Fix 4418 Ryo ->

			// Upload directory serverdiff
			if(copyClient && serverdiff_present) {
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_SERVER_DIFF, getClientRoot()}));
				resetBackupDirFTP(ftp, getServerRoot(), 6);

				// creo la directory temporanea
				String tempDir = fRoot + NAME_FIX_SERVER_DIFF + File.separatorChar + "tempBlend2";
				FileUtil.makeDir(tempDir);

				// download dei jar da aggiornare
				String[] filesTemp = FileUtil.list(fRoot + NAME_FIX_SERVER_DIFF, "*.zip");

				int fileTemp = 0;
				boolean downloadRight = true;
				while (fileTemp < filesTemp.length && downloadRight) {
					String nameFile = getFileNameWithoutExtension(filesTemp[fileTemp]);
					String locPath = tempDir + File.separatorChar + nameFile + ".jar";
					ftp.changeDirectory(getClientRoot());
					downloadRight = ftp.downloadFile(nameFile + ".jar", locPath);
					fileTemp++;
				}

				// aggiornamento dei file scaricati nella directory temporanea
				String[] files = FileUtil.list(tempDir, "*.jar");
				primroseJarBlender(files, fRoot, tempDir, true);

				// upload della directory temporanea
				ftp.uploadTree(tempDir);

				// eliminazione della directory temporanea
				FileUtil.deleteTree(tempDir);
			}

			// Fix 4418 Ryo <-

			// UPLOAD directory Reports
			if(copyReports && reports_present) {
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
						new String[] {fRoot +
						NAME_FIX_REPORTS,
						getReportsRoot()}));
				resetBackupDirFTP(ftp, getReportsRoot(), 7);
				ftp.uploadTree(fRoot + NAME_FIX_REPORTS);
			}

			// UPLOAD file utente
			if(copyUser && usr_present) {
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo",
						new String[] {fRoot +
						nameUserDir, getUserRoot()}));
				resetBackupDirFTP(ftp, getUserRoot(), 8);
				ftp.uploadTree(fRoot + nameUserDir);
			}

		}
		catch (Exception e) {
			//    output.println("********** #ERRORE# **********");	// Fix 4418 Ryo
			output.println(ResourceLoader.getString(SETUP_RES, "ErrorString")); // Fix 4418 Ryo//Mod. 12771
			output.println(e.getMessage());
			return false;
		}
		finally {
			try {
				//Close FTP Connection
				closeFTPConnection(ftp);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		return true;
	}

	/**
	 * Mod.1475 - Non viene più utilizzato
	 * ControlCopyFixFile: controlla se la copia dei file è fattibile.
	 * @return boolean
	 */
	public boolean controlCopyFixFile(BufferedReader fixFile) {
		System.out.println(ResourceLoader.getString(SETUP_RES, "CheckCopyFilesTest")); //Mod. 13666//, new Object[] {newName})); //Fix 03679 //Mod. 12771
		output.println(ResourceLoader.getString(SETUP_RES, "FilesNoPermissions"));

		Vector fixCopyVector = new Vector();
		short version = 0;
		short release = 0;
		short modification = 0;
		int fixNr;

		//...booleano che indica se la fix è già stata applicata
		boolean fixIsAlreadyApplied = false;

		try {
			//...scorro il file con l'elenco delle fix
			String read = fixFile.readLine();
			while (read != null) {
				read = read.trim();

				//...non considero le righe vuote e quelle di commento
				if((!read.startsWith(COMMENT)) && (!read.equals(""))) {

					//...se la riga inizia con "Application" imposto i valori degli attributi
					if(read.startsWith(APPLICATION)) {
						fixNrStr = null;

						String[] values = extractApplicationValues(read);
						application = values[0];
						module = values[1];
						versionStr = values[2];
						releaseStr = values[3];
						modificationStr = values[4];
						//...leggo la riga successiva
						read = fixFile.readLine();
					}

					//...se la riga inizia con "Description" imposto i valori delle descrizioni
					else if(read.startsWith(DESCRIPTION)) {
						//...leggo la riga successiva
						read = fixFile.readLine();
					}

					//...se la riga  con "UpdateApplication" imposto i nuovi attributi
					else if(read.startsWith(UPDATE_APPL)) {
						//...leggo la riga successiva
						read = fixFile.readLine();
					}

					//...Se la riga non fa parte degli altri 3 casi allora è la riga di una fix
					else {

						//...Vettore che contiene i parametri
						Vector parVect = new Vector();

						// Mod. 905 - ini
						// Controllo se la fix da applicare o meno per l'applicazione corrente
						TagProcessor tagProcessor = new TagProcessor(null);
						String newLine = tagProcessor.replaceTags(read);
						if(!tagProcessor.isRunnable()) {
							read = fixFile.readLine();
							continue;
						}
						read = newLine.trim();
						// Mod. 905 - fin
						int pos = read.indexOf(SEPARATOR);
						fixNrStr = read.substring(0, pos).trim();

						//          ...Stringa con tutti i valori della fix che suddivido in singole
						//...stringhe che aggiungo al vettore parVect.
						String value = extractFixValues(read); //
						StringTokenizer valueTokenizer = new StringTokenizer(value, ",");
						int i = 0;
						while (valueTokenizer.hasMoreTokens()) {
							String resValue = valueTokenizer.nextToken();
							parVect.addElement(resValue);
							i++;
						}
						className = (String) parVect.elementAt(0);
						if(className.equalsIgnoreCase(APPLY_FIX_NAME))
							pathForCopy = getFixRoot() + File.separator +
							(String) parVect.elementAt(1);
						else
							pathForCopy = (String) parVect.elementAt(1);

						//Mod.2193 - ini
						String mask1 = "00000" + String.valueOf(fixNrStr);
						String nFix1 = mask1.substring(mask1.length() - 5, mask1.length());
						pathForCopy = pathForCopy.substring(0,
								pathForCopy.lastIndexOf(nFix1) +
								5);
						//Mod.2193 - fin

					}

					//...Inizio a controllare se la fix è già stata applicata
					if(fixNrStr != null && !fixNrStr.equals("")) {

						//...controllo che nessuno dei parametri sia nullo
						if(application == null || application.equals("") ||
								module == null || module.equals("") ||
								versionStr == null || versionStr.equals("") ||
								releaseStr == null || releaseStr.equals("") ||
								modificationStr == null || modificationStr.equals("")) {
							System.out.println(ResourceLoader.getString(SETUP_RES, //Fix 03679
							"NoCorrectParameters"));
							closeLogFile(); //Mod.1315 - chiudo file di Log
							return false;
						}

						//...Se tuti i parametri sono stati impostati correttamente proseguo
						else {

							//...cambio tutte le stringhe nei tipi giusti per gli attributi
							version = new Short(versionStr).shortValue();
							release = new Short(releaseStr).shortValue();
							modification = new Short(modificationStr).shortValue();
							fixNr = new Integer(fixNrStr).intValue();

							//...controllo se la fix è già stata applicata
							Vector swFix = this.fixIsApplied(application, module, version,
									release, modification, fixNr);

							//...se esiste già una fix con questa chiave ed è la prima volta
							//...che la trovo nel file,vuol dire che la fix è già stata applicata
							//if(swFix.size() == 0) {
							if(swFix.size() == 0 || onlyCopyFiles) { //Mod.2256 - IVAN
								if(fixCopyVector.indexOf(fixNrStr) == -1)

									//...se il numero di fix è nuovo (non è presente nel vettore) lo aggiungo
									fixCopyVector.add(fixNrStr);
							}
							read = fixFile.readLine();
						}

					} //...fine if(fixNrStr != null && !fixNrStr.equals(""))

				} //...fine if((!read.startsWith(COMMENT)) && (!read.equals("")))

				//...se le riga era un commento o era vuota, leggo la successiva
				else
					read = fixFile.readLine();
			}

			int fileNoPermissions = 0;
			String[] list = null;
			for (int i = 0; i < fixCopyVector.size(); i++) {
				fixNrStr = (String) fixCopyVector.elementAt(i);
				//String fRoot = getFixRoot()+File.separator+fixNrStr+File.separator;
				String fRoot = pathForCopy + File.separator; //Mod.2193
				boolean lib_present = FileUtil.exists(fRoot + NAME_FIX_LIB);
				boolean web_present = FileUtil.exists(fRoot + NAME_FIX_WEB);
				boolean rpt_present = FileUtil.exists(fRoot + NAME_FIX_RPT);
				boolean rptCR2008_present = FileUtil.exists(fRoot + NAME_FIX_RPT); //Mod. 11234
				//Mod.2258 - ini
				boolean server_present = FileUtil.exists(fRoot + NAME_FIX_SERVER);
				boolean client_present = FileUtil.exists(fRoot + NAME_FIX_CLIENT);
				boolean reports_present = FileUtil.exists(fRoot + NAME_FIX_REPORTS);
				//Mod.2258 - fin
				//Mod.2226 - ini
				boolean usr_present = false;
				if(nameUserDir != null && !nameUserDir.equals(""))
					usr_present = FileUtil.exists(fRoot + nameUserDir);
				//Mod.2226 - fin
				// Controllo la cartella lib - class/properties/img
				if(copyLib && lib_present) {
					list = FileUtil.listTree(fRoot + NAME_FIX_LIB, "*.*");
					fileNoPermissions = fileNoPermissions +
					controlPermissions(list, getLibRoot());
				}
				if(copyWeb && lib_present) {
					String webclassesroot = getWebRoot() + File.separator +
					NAME_APP_WEBINF + File.separator + NAME_APP_WEBCLASSES;
					fileNoPermissions = fileNoPermissions +
					controlPermissions(list, webclassesroot);
					String weblibroot = getWebRoot() + File.separator + NAME_APP_WEBINF +
					File.separator + NAME_APP_WEBLIB;
					String[] s = {
							"*.jar", "*.zip"};
					fileNoPermissions = fileNoPermissions +
					controlPermissions(list, weblibroot, s, false);
				}

				if(copyWeb && web_present) {
					list = FileUtil.listTree(fRoot + NAME_FIX_WEB, "*.*");
					String[] s = {
					".tfml"};
					fileNoPermissions = fileNoPermissions +
					controlPermissions(list, getWebRoot(), s, true);
				}

				// Controllo i report nella directory opportuna in funzione della versione di Crystal R.
				if(copyRpt && (rpt_present || rptCR2008_present)) { //Mod. 11234
					if(this.crystalVer == ApplicationPreferences.CRYSTALR_VER_8_5) { //Mod. 11234
						list = FileUtil.listTree(fRoot + NAME_FIX_RPT, "*.*");
						//Mod. 11234 inizio
					}
					else if(crystalVer == ApplicationPreferences.CRYSTALR_VER_2008) {
						list = FileUtil.listTree(fRoot + NAME_FIX_RPT_CR2008, "*.*");
					}
					// fine mod. 11234
					fileNoPermissions = fileNoPermissions +
					controlPermissions(list, getRptRoot());

				}

				//Mod.2226 - ini
				if(copyUser && usr_present) {
					list = FileUtil.listTree(fRoot + nameUserDir, "*.*");
					fileNoPermissions = fileNoPermissions +
					controlPermissions(list, getUserRoot());
				}
				//Mod.2226 - fin

				//Mod.2258 - ini
				// Controllo la cartella Server
				if(copyServer && server_present) {
					list = FileUtil.listTree(fRoot + NAME_FIX_SERVER, "*.*");
					fileNoPermissions = fileNoPermissions +
					controlPermissions(list, getServerRoot());
				}
				if(copyClient && client_present) {
					list = FileUtil.listTree(fRoot + NAME_FIX_CLIENT, "*.*");
					fileNoPermissions = fileNoPermissions +
					controlPermissions(list, getClientRoot());
				}
				if(copyReports && reports_present) {
					list = FileUtil.listTree(fRoot + NAME_FIX_REPORTS, "*.*");
					fileNoPermissions = fileNoPermissions +
					controlPermissions(list, getReportsRoot());
				}
				//Mod.2258 - fin
			}
			if(fileNoPermissions > 0) {
				System.out.println(ResourceLoader.getString(SETUP_RES, //Fix 03679
				"CheckCopyFilesError"));
				closeLogFile(); //Mod.1315 - chiudo file di Log
				return false;
			}
		}
		catch (Exception e) {
			e.printStackTrace(Trace.excStream);
			closeLogFile(); //Mod.1315 - chiudo file di Log
			return false;
		}
		pathForCopy = "";
		System.out.println(ResourceLoader.getString(SETUP_RES, "FileReportOkTitle")); //Fix 03679
		output.println(ResourceLoader.getString(SETUP_RES,
		"NoFilesWithoutPermissions"));
		return true;
	}

	//Mod. 1475 - Controlla i permessi di un file
	private int controlPermissions(String[] list, String dest) throws IOException {
		int cont = 0;
		for (int j = 0; j < list.length; j++) {
			File f = new File(dest + File.separator + list[j]);
			if(f.exists() && !f.canWrite()) {
				output.println(f.getAbsolutePath());
				cont++;
			}
		}
		return cont;
	}

	//Mod.1475
	private int controlPermissions(String[] list, String dest, String[] exts,
			boolean exclude) throws IOException {
		String[] cleanList = {};
		for (int i = 0; i < exts.length; i++) {
			if(exclude && i > 0)
				cleanList = cleanList(cleanList, cleanList, exts[i], exclude);
			else
				cleanList = cleanList(list, cleanList, exts[i], exclude);
		}
		return controlPermissions(cleanList, dest);
	}

	//Mod.1475
	private String[] cleanList(String[] iniList, String[] cleanList, String ext,
			boolean exclude) {
		Vector v = new Vector();
		if(!exclude) {
			for (int j = 0; j < cleanList.length; j++)
				v.addElement(cleanList[j]);
		}
		for (int i = 0; i < iniList.length; i++) {
			String s = iniList[i];
			if(exclude) {
				if(s.indexOf(ext) == -1)
					v.addElement(s);
			}
			else {
				if(s.indexOf(ext) != -1)
					v.addElement(s);
			}
		}
		String[] strs = new String[v.size()];
		for (int k = 0; k < v.size(); k++)
			strs[k] = (String) v.elementAt(k);
		return strs;
	}

	/**Mod.2662 reso public
	 * Metodo statico che descrive l'uso dei parametri per lanciare la classe.
	 */
	public static void printHelp() { //Fix 03679
		System.out.println("");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr01"));//Mod. 9634//"USO : java com.thera.thermfw.setup.FixFileProcessor <databaseName> <fixRoot> [options]");
		System.out.println("------------------------------------------------------");
		System.out.println("");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr02"));//Mod. 9634//"PARAMETRI OBBLIGATORI:");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr03"));//Mod. 9634//"  <String-databaseName> : Nome del database (presente in THERMFW.INI)");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr04"));//Mod. 9634//"  <String-fixRoot> : E' il percorso in cui si trovano le fix");
		System.out.println("");
		//Mod.905 - ini
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr05"));//Mod. 9634//"PARAMETRI FACOLTATIVI:");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr06"));//Mod. 9634//"  (NB: Se inserisco lo userName DEVO inserire anche la password)");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr07"));//Mod. 9634//"  -U  <String-userName>: Nome dell'utente di therm (se non lo inserisco e' ADMIN)");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr08"));//Mod. 9634//"  -P  <String-password>: Password dell'utente di therm (se non la inserisco e' ADMIN)");
		System.out.println("");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr09"));//Mod. 9634//"  -TP <String-platformType> : Tipo di piattaforma (se non lo inserisco e' NT)");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr10"));//Mod. 9634//"                    Inserire: NT per NT");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr11"));//Mod. 9634//"                              AS per AS-400");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr12"));//Mod. 9634//"                              LX per LINUX");
		System.out.println("");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr13"));//Mod. 9634//"  -A <applications>    : Elenco applicazioni a cui applicare il setup");
		System.out.println("");
		//Mod.1315
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr14"));//Mod. 9634//"  -CF <String-libRoot> <String-webRoot> <String-rptRoot> :");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr15"));//Mod. 9634//"      Copia dei file della fix. I seguenti parametri sono obbligatori");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr16"));//Mod. 9634//"      <String-libRoot>  : Path in cui copiare le classi (class)");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr17"));//Mod. 9634//"      <String-webRoot>  : Path in cui copiare i file web (jsp/html/js)");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr18"));//Mod. 9634//"      <String-rptRoot>  : Path in cui copiare i report");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr19"));//Mod. 9634//"      <String-userRoot> : Path in cui copiare i file utente"); //Mod.2226
		System.out.println("");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr20"));//Mod. 9634//"  -D  <String-nameUserDir> : Nome della directory con i file utente"); //Mod.2226
		//Mod.2258
		System.out.println("");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr21"));//Mod. 9634//"  -CF1 <String-serverRoot> <String-clientRoot> <String-reportsRoot> :");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr22"));//Mod. 9634//"       Copia dei file della fix per primrose. I seguenti parametri sono obbligatori");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr23"));//Mod. 9634//"      <String-serverRoot>  : Path in cui copiare la cartella server");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr24"));//Mod. 9634//"      <String-clientRoot>  : Path in cui copiare la cartella client");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr25"));//Mod. 9634//"      <String-reportsRoot> : Path in cui copiare la cartella reports");
		System.out.println(""); //Mod.2256
		//Mod. 13978 inizio
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr212"));//"  -CF2 <String-serverRoot> <String-clientRoot> <String-reportsRoot> :");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr222"));//"       Copia dei file della fix per primrose Plex ver.610. I seguenti parametri sono obbligatori");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr232"));//"      <String-serverRoot>  : Path in cui copiare la cartella server610");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr242"));//"      <String-clientRoot>  : Path in cui copiare la cartella client610");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr252"));//"      <String-reportsRoot> : Path in cui copiare la cartella reports");
		System.out.println("");
		//fine mod. 13978
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr26"));//Mod. 9634//"  -OCF : esegue solo la copia di tutti i file per tutte le fix del FixOrder.txt"); //Mod.2256
		//Mod.905 - fin
		//Mod.2967 - inizio
		System.out.println("");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr27"));//Mod. 9634//"  -FTP <String-host> <String-port> <String-user> <String-password>");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr28"));//Mod. 9634//"       Attiva la funzione di copia file tramite server FTP");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr29"));//Mod. 9634//"       I seguenti parametri sono obbligatori:");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr30"));//Mod. 9634//"      <String-host>     : l'indirizzo del server FTP;");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr31"));//Mod. 9634//"      <String-port>     : la porta del server FTP (per utilizzare quella di default passare -1);");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr32"));//Mod. 9634//"      <String-user>     : utente connessione al server FTP;");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr33"));//Mod. 9634//"      <String-password> : password utente server FTP;");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr34"));//Mod. 9634//"  -BKPS : esegue il backups dei file");
		//Mod.2967 - fine
		//Mod. 2295
		System.out.println("");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr35"));//Mod. 9634//"  -MI <Modalità insert> :  comportamento nel caso di riga duplicata dopo un INSERT sql");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr36"));//Mod. 9634//"       IO :(Insert Only) per non trasformare l'insert in update");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr37"));//Mod. 9634//"       IU :(Insert Update) per trasformare l'INSERT che da' Riga duplicata in un UPDATE");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr38"));//Mod. 9634//"       La mancanza del parametro corrisponde ad impostare il parametro ad Insert Update");
		//fine mod. 2295
		// fix 3168
		System.out.println("");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr39"));//Mod. 9634//"  -COMMIT : esegue commit ad ogni fix");
		// fine fix 3168
		// fix 3418
		System.out.println("");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr40"));//Mod. 9634//"  -UFN : considera univocità assoluta del numero di fix");
		// fine fix 3418
		//Fix 04576 MM
		System.out.println("");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr41"));//Mod. 9634//"  -H : Struttura gerarchica delle fix");
		//Fix 04576  MM
		System.out.println("------------------------------------------------------");
		System.out.println(ResourceLoader.getString(SETUP_RES, "PrnHelpStr42"));//Mod. 9634//"Eventuali errori verranno indirizzati nel file SQLFileReport.txt nella directory corrente.");
		System.out.println("");
		System.exit( -1);
		//    System.exit(0);
	}

	// Mod.1315 - 2149

	//Mod.2149 - INI - rigenerazione viste
	public void printInoperativeView(Vector v) {
		if(v != null && v.size() > 0) {
			System.out.println(""); //Fix 03679
			System.out.println(ResourceLoader.getString(SETUP_RES, "WarningView", //Fix 03679
					new
					String[] {String.valueOf(v.
							size())}));
			output.println("");
			output.println(ResourceLoader.getString(SETUP_RES, "WarningString"));//Mod. 9634//"********** #WARNING# **********");//Mod. 12771
			for (int i = 0; i < v.size(); i++)
				output.println(ResourceLoader.getString(SETUP_RES, "InoperativeView",
						new String[] {(String) v.
						elementAt(i)}));
			output.println("");
		}
	}

	//Mod.2149 - Stampa viste errate
	//07935 - DF - modificato
	public void printViewErrors(List views) {
		System.out.println(""); //Fix 03679
		System.out.println(ResourceLoader.getString(SETUP_RES, "ErrorsView", new String[] {String.valueOf(views.size()), FixRunner.FixFileReportName})); //Mod. 13666//newName}));
		output.println("");
		output.println(ResourceLoader.getString(SETUP_RES, "ErrorString"));//Mod. 12771
			for (int i = 0; i < views.size(); i++)
			{
				output.println(ResourceLoader.getString(SETUP_RES, "RegenerationView", new String[] {views.get(i).toString()}));
				System.out.println(ResourceLoader.getString(SETUP_RES, "RegenerationView", new String[] {views.get(i).toString()}));//Mod. 14238
				//Mod. 14328//((Exception)viewsExceptions.get(i)).printStackTrace(output);	// Fix 12319 Ryo
				//Mod. 14328 inizio: stampo l'eccezione con il rif. alla vista o, se non c'è un'eccez., do' messaggio generico
				DictView dw = (DictView)views.get(i); //i-ma vista
				String excKey = dw.getSchema()+"." + dw.getName();
				Exception excp = ((Exception)viewsExceptionsMap.get(excKey)); //relativa eccezione
				if (excp != null) {
					 excp.printStackTrace(output);
				}
				else
				{
					output.println(ResourceLoader.getString(SETUP_RES, "ErrNoExc", new String[] {excKey}));
					System.out.println(ResourceLoader.getString(SETUP_RES, "ErrNoExc", new String[] {excKey}));
				}
				//fine mod. 14328
				output.println("");
			}
		output.println("");
	}
	//07935 - Fine

	//Mod.1754 - Carica Fix Descriptor
	public void loadFixDescription() throws IOException {
		String readL = fixDescriptionFile.readLine();
		while (readL != null) {
			readL = readL.trim();
			if(readL.equals("")) {
				readL = fixDescriptionFile.readLine();
				continue;
			}
			if(readL.indexOf(PersistentObject.KEY_SEPARATOR) != -1) {
				Integer codFix = new Integer(readL.substring(0,
						readL.indexOf(PersistentObject.KEY_SEPARATOR)));
				String desFix = readL.substring(readL.indexOf(PersistentObject.
						KEY_SEPARATOR) + 1, readL.length());
				fixDescription.put(codFix, desFix);
			}
			readL = fixDescriptionFile.readLine();
		}
	}

	//Fix 03679 MM inizio
	/**
	 * @return Returns the startingTimestamp.
	 */
	public Timestamp getStartingTimestamp() {
		return startingTimestamp;
	}

	/**
	 * @param startingTimestamp The startingTimestamp to set.
	 */
	public void setStartingTimestamp(Timestamp startingTimestamp) {
		this.startingTimestamp = startingTimestamp;
	}

	//Fix 03679 MM fine

	//Mod.2226 - Ritorna il vettore delle fix per controllare se è già applicata
	public Vector fixIsApplied(String application, String module, short version, short release, short modification, int fixNr) throws Exception {
		//Fix 03679 MM inizio
		StringBuffer whereBuffer = new StringBuffer();
		whereBuffer.append(SoftwareFixTM.SOFTWARE_FIX).append("=").append(fixNr);
		if(!uniqueFixNumber) {
			whereBuffer.append(" AND ").append(SoftwareFixTM.SOFTWARE_ID).append("='").append(application).append("'")
			.append(" AND ").append(SoftwareFixTM.MODULE_ID).append("='").append(module).append("'")
			.append(" AND ").append(SoftwareFixTM.VERSION).append("=").append(version)
			.append(" AND ").append(SoftwareFixTM.RELEASE).append("=").append(release)
			.append(" AND ").append(SoftwareFixTM.MODIFICATION).append("=").append(modification);
		}
		if(getStartingTimestamp() != null) {
			String timestamp = Security.getDatabase().getLiteral(getStartingTimestamp());
			whereBuffer.append(" AND ").append(SoftwareFixTM.CREATION_TIMESTAMP).append("<").append(timestamp);
		}
		return SoftwareFix.retrieveList(whereBuffer.toString(), "", false);
		//  	Fix 03679 MM fine
	}

	//Mod.2256 - ini
	//Fix 03679 MM
	//  public boolean getFlagPresent(String flag, String[] params) {
	public static boolean getFlagPresent(String flag, String[] params) {
		for (int i = 2; i < params.length; i++) {
			if(params[i].equals(flag))
				return true;
		}
		return false;
	}

	//Mod.2967 - inizio
	/**
	 * OpenFTPConnection.
	 */
	public FTPConnection openFTPConnection() throws IOException {
		FTPConnection ftp = new FTPConnection();
		if(getFtpServerPort() != -1)
			ftp.connect(getFtpServerHost(), getFtpServerPort());
		else
			ftp.connect(getFtpServerHost());
		ftp.login(getFtpUserName(), getFtpUserPassword());
		return ftp;
	}

	/**
	 * CloseFTPConnection.
	 */
	public void closeFTPConnection(FTPConnection ftp) throws IOException {
		if(ftp != null) {
			ftp.logout();
			ftp.disconnect();
		}
	}

	/**
	 * Rimuove la directory di backup.
	 */
	/* Revisions:
	 * Fix nr   Date          Owner      Description
	 *  4418    10/10/2005    Ryo        La directory backups viene creata solo quando richiesto
	 */
	public void resetBackupDirFTP(FTPConnection ftp, String backup, int pos) throws
	IOException {
		ftp.changeDirectory(ftp.getRoot());
		ftp.changeDirectory(backup);
		ftp.setBackupPath(backup + File.separator + NAME_BACKUPS_DIR);
		ftp.setExcBackupPath(backup);
		if(genBackupDir[pos].equalsIgnoreCase("Y")) {
			ftp.removeTree(NAME_BACKUPS_DIR);
			if(backupsFiles) { // Fix 4418 Ryo
				ftp.makeDirectory(NAME_BACKUPS_DIR);
			}
			genBackupDir[pos] = "N";
		}
	}

	/**
	 * Rimuove la directory di backups.
	 */
	public void resetBackupDir(String backup, int pos) throws IOException {
		if(genBackupDir[pos].equalsIgnoreCase("Y")) {
			FileUtil.deleteTree(backup + File.separator + NAME_BACKUPS_DIR);
			genBackupDir[pos] = "N";
		}
		FileUtil.setBackupPath(backup + File.separator + NAME_BACKUPS_DIR);
		FileUtil.setExcBackupPath(backup);
	}

	//Mod.2967 - fine

	// Fix 3168
	/**
	 * Se non esistenti crea Applicazione, Modulo e Livello.
	 */
	protected SoftwareLevel createSwLevel() throws SQLException {
		Software sw = new Software();
		SoftwareModule swMod = new SoftwareModule();
		SoftwareLevel swLev = new SoftwareLevel();
		// Se l'applicazione non esiste ancora la salvo
		sw.setKey(application);
		sw.setVersionClass(CLASS_VERSION_NAME);
		sw.setDescription(applDescription);
		// Se la descrizione non è presente considero il nome dell'applicazione come descrizione
		if(applDescription == null || applDescription.equals(""))
			sw.setDescription(application);
		if(!sw.retrieve())
			sw.save();

		// Se il modulo non esiste ancora lo salvo
		swMod.setKey(application + KeyHelper.KEY_SEPARATOR + module);
		swMod.setDescription(modDescription);
		// Se la descrizione non è presente considero il nome del modulo come descrizione
		if(modDescription == null || modDescription.equals(""))
			sw.setDescription(module);
		if(!swMod.retrieve())
			swMod.save();

		// Se il livello non esiste ancora lo salvo
		swLev.setKey(application + KeyHelper.KEY_SEPARATOR + module + KeyHelper.KEY_SEPARATOR + versionStr + KeyHelper.KEY_SEPARATOR + releaseStr + KeyHelper.KEY_SEPARATOR + modificationStr);
		if(!swLev.retrieve())
			swLev.save();
		return swLev;
	}

	// Fine fix 3168

	//Fix 03679 MM inizio
	/**
	 * Metodo di ricerca dei files <code>relativeFileName</code> all'interno
	 * della directory <code>fixRoot</code>.
	 * La ricerca avviene secondo il seguente algoritmo:
	 * <ul>
	 * <li>ricerca del file <code>fixRoot</code>/std/&lt;db&gt;/&lt;platform&gt;/<code>relativeFileName</code>
	 * <li>se non trovato, ricerca del file <code>fixRoot</code>/std/&lt;db&gt;/<code>relativeFileName</code>
	 * <li>se non trovato, ricerca del file <code>fixRoot</code>/std/<code>relativeFileName</code>
	 * </ul>
	 * dove: <br>
	 * &lt;platform&gt; è la directory corrispondente alla piattaforma sulla quale si applicano le fix; <br>
	 * i valori possibili sono:
	 * <ul>
	 * <li>NT (server Windows)
	 * <li>AS (AS 400)
	 * <li>LX (Linux)
	 * </ul>
	 * &lt;db&gt; è la directory corrispondente al database sul quale si applicano le fix; <br>
	 * i valori possibili sono:
	 * <ul>
	 * <li>db2
	 * <li>sqlserver
	 * <li>oracle
	 * </ul>
	 */
	protected String findFixFile(String fixRoot, String relativeFileName) {
		String platformDir = (platformType == null) ? FixFileProcessor.NT : platformType;
		String completeFileName = relativeFileName;
		//controllo che esista il file fixRoot/std/<db>/<piattaforma>/relativeFileName
		StringBuffer dir = new StringBuffer(fixRoot).append(File.separator)
		.append(STD_DIR).append(File.separator)
		.append(getDBDir()).append(File.separator)
		.append(platformDir);
		if(existsDBPlatformDir(dir.toString())) {
			completeFileName = dir.append(File.separator).append(relativeFileName).toString();
			if(existsFile(completeFileName))
				return completeFileName;
		}
		//controllo che esista il file fixRoot/std/<db>/relativeFileName
		dir = new StringBuffer(fixRoot).append(File.separator)
		.append(STD_DIR).append(File.separator)
		.append(getDBDir());
		if(existsDBDir(dir.toString())) {
			completeFileName = dir.append(File.separator).append(relativeFileName).toString();
			if(existsFile(completeFileName))
				return completeFileName;
		}
		//restituisco il file fixRoot/std/relativeFileName
		completeFileName = new StringBuffer(fixRoot).append(File.separator)
		.append(STD_DIR).append(File.separator)
		.append(relativeFileName).toString();
		return completeFileName;
	}

	protected boolean existsFile(String fileName) {
		File file = new File(fileName);
		return file.exists() && !file.isDirectory();
	}

	protected boolean existsDBPlatformDir(String dirName) {
		if(existsDBPlatformDir == null)
			existsDBPlatformDir = new Boolean(existsDirectory(dirName));
		return existsDBPlatformDir.booleanValue();
	}

	protected boolean existsDBDir(String dirName) {
		if(existsDBDir == null)
			existsDBDir = new Boolean(existsDirectory(dirName));
		return existsDBDir.booleanValue();
	}

	protected boolean existsDirectory(String directoryName) {
		File file = new File(directoryName);
		return file.exists() && file.isDirectory();
	}

	/**
	 *
	 * @return
	 */
	protected String getDBDir() {
		if(dbDir == null)
			dbDir = Security.getDatabase().getDBManagerId();
		return dbDir;
	}

	// 3913 DM inizio
	protected void commit() throws SQLException {
		// 5629 DM fine
		if(OracleCommandHistory.isActive())
			OracleCommandHistory.clear();
		// 5629 DM fine
		ConnectionManager.commit();
		// 5629 DM inizio
		// if(ConnectionManager.getCurrentDatabase() instanceof SQLServerDatabase)
		if(CommandHistory.isActive())
			CommandHistory.clear();
	}

	protected void rollback() throws SQLException {
		ConnectionManager.rollback();
		// 5629 DM inizio
		// if(ConnectionManager.getCurrentDatabase() instanceof SQLServerDatabase)
		if(CommandHistory.isActive())
			// 5629 DM fine
			CommandHistory.clear();
	}

	// 3913 DM fine

	//Fix 04574 PM-MM Inizio
	//07935 - DF
	protected boolean makeViews() throws Exception {
		//Mod. 13667//if(onlyCopyFiles)
		if(onlyCopyFiles || !doneSthg) //Mod. 13667
			return true;

		BufferedReader viewSchemasFile = getViewSchemaFile();
		List views = loadViews(viewSchemasFile);
		if(viewSchemasFile != null)
			viewSchemasFile.close();

		if(views.size() > 0) {
			System.out.println("");
			System.out.println(ResourceLoader.getString(SETUP_RES, "DoRegenViews"));

			DDLExecutorForViews executor = new DDLExecutorForViews(new PrintWriter(new StringWriter()));
			//Cancellazione Viste
			dropViews(views,executor);
			//Creazione Viste
			regenerationViews(views,executor);
			if(views.size() > 0) {
				printViewErrors(views);
				return false;
			}
			else {
				System.out.println(ResourceLoader.getString(SETUP_RES,"RegenerationViewOK"));
				System.out.println("");
				output.println("");
				output.println(ResourceLoader.getString(SETUP_RES,"RegenerationViewOK"));
				output.println("");
			}
		}
		return true;
	}

	protected List loadViews(BufferedReader file) {
		try {
      		//Fix 19051 inizio
	      	return DictView.retrieveList("", DictViewTM.NAME, false);
      		/*
      		List schemas = loadViewSchemas(file);
	      	if(schemas.size() > 0) {
        		String whereCond = DictViewTM.SCHEMA_NAME + " IN (";
		        for (int i = 0; i < schemas.size(); i++) {
		          String schema = (String) schemas.get(i);
		          if(i > 0)
		            whereCond += ",";
		          whereCond += "'" + schema + "'";
		        }
		        whereCond += ")";
		        return DictView.retrieveList(whereCond, DictViewTM.NAME, false);
		    }
      		*/
		//Fix 19051 fine
		}
		catch (Exception e) {
			e.printStackTrace(Trace.excStream);
		}
		return new ArrayList();
	}

	protected List loadViewSchemas(BufferedReader file) {
		List res = new ArrayList();
		if(file == null)
			return res;
		try {
			String read = file.readLine();
			while (read != null) {
				read = read.trim();
				if(read.length() != 0)
					res.add(read);
				read = file.readLine();
			}
		}
		catch (IOException e) {
			e.printStackTrace(Trace.excStream);
		}
		return res;
	}

	/* Revisions:
	 * Fix #    Date          Owner      Description
	 * 12816     5/ 8/2010    Ryo        Tracce che consentono di monitorare le operazioni
	 */
	public void dropViews(List views,DDLExecutorForViews executor)
	{
		// Fix 12816 Ryo ->
		System.out.println("\nRimozione di " + views.size() + " viste");
		output.println("Rimozione di " + views.size() + " viste");//Fix 18069
		long start = System.currentTimeMillis();
		try
		{
			// Fix 12816 Ryo <-
			for(int i = 0;i < views.size();i++)
			{
				DictView elem = (DictView) views.get(i);
				try
				{
					System.out.print(".");	// Fix 12816 Ryo
					elem.dropView(executor);
				}
				catch(TDDMLException e)
				{
					System.out.println("\n" + ResourceLoader.getString(SETUP_RES, "NoDelView",  new String[] {elem.getSchema()+"."+elem.getName()}));//Mod. 9634//"Impossibile rimuovere la vista "+elem.getSchema()+"."+elem.getName()+". La vista non verrà rigenerata");	// Fix 12816 Ryo
					output.println(ResourceLoader.getString(SETUP_RES, "NoDelView",  new String[] {elem.getSchema()+"."+elem.getName()}));//Mod. 9634//"Impossibile rimuovere la vista "+elem.getSchema()+"."+elem.getName()+". La vista non verrà rigenerata");
					e.printStackTrace();
					views.remove(i);
					i--;
				}
			}
		}
		// Fix 12816 Ryo ->
		catch(Throwable t)
		{
			t.printStackTrace(Trace.excStream);
		}
		long end = System.currentTimeMillis();
		System.out.println("\nRimozione delle viste eseguita in " + (end-start) + " ms");
		// Fix 12816 Ryo <-
	}

	/* Revisions:
	 * Fix #    Date          Owner      Description
	 * 12319     3/ 3/2010    Ryo        Eccezione recuperata e inviata a Trace.excStream
	 * 12816     5/ 8/2010    Ryo        Tracce che consentono di monitorare le operazioni
	 */
	protected void regenerationViews(List views, DDLExecutorForViews executor)
	{
		int lastSize = 0;
		// Fix 12816 Ryo ->
		long start = System.currentTimeMillis();
		try
		{
			System.out.println("\nRigenerazione di " + views.size() + " viste");	// Fix 12816 Ryo
			// Fix 12816 Ryo <-
			while(views.size() > 0 && (views.size() != lastSize))
			{
				lastSize = views.size();
				for(int i = 0;i < views.size();i++)
				{
					DictView elem = (DictView) views.get(i);
					try
					{
						System.out.print(".");	// Fix 12816 Ryo
//						output.print(i + "\tstart: " + elem.getName());	// Fix 12816 Ryo Fix 18069 commented
						output.print(i + "\tstart: " + SystemParam.getSchema(elem.getSchema()) + elem.getName());	// Fix 18069 aggiunto il nome della schema
						elem.generateView(executor);
						//Mod.14328//output.println("end");	// Fix 12816 Ryo
						output.println(" :end");	//Mod.14328//

						if(DDLExecutorFileProcessor.viewExists(elem.getSchema(), elem.getName()))
						{
							views.remove(i);
							i--;
						}
						elem.buildGrantStmt(executor, elem.getSchema(), elem.getName()); //Mod. 14160
					}
					catch(TDDMLException e)
					{
						//Mod.14328//viewsExceptions.add(e);	// Fix 12319 Ryo
						viewsExceptionsMap.put(elem.getSchema()+"."+elem.getName(), e);//Mod.14328
						//e.printStackTrace();	// Fix 12319 Ryo
					}
				}
			}
		}
		// Fix 12816 Ryo ->
		catch(Throwable t)
		{
			t.printStackTrace(Trace.excStream);
		}
		long end = System.currentTimeMillis();
		System.out.println("\nRigenerazione delle viste completata in " + (end-start) + " ms\n");
		// Fix 12816 Ryo <-
	}

	//07935 - Fine

	//Fix 04576 PM-MM inizio
	protected String extractFixRoot(String[] args) {
		return args[1];
	}

	protected void endRun() {
		// 10936 DM inizio
		// 11592 DM inizio
		if(platformType.equals(AS))
			return;
		// 11592 DM fine
		String msg;
		try {
			int rc = ReorgRunStats.getReorgRunStats().updateDefaultGroup();
			commit();
			msg = rc + " " + ResourceLoader.getString(SETUP_RES, "AssignedTablesOK");
		}
		catch (Exception e) {
			msg = ResourceLoader.getString(SETUP_RES, "AssignedTablesError") + ":\n" + e;
			try {
				rollback();
			}
			catch (SQLException ignore) {
			}
		}
		System.out.println(msg);
		output.println(msg);
		// 10936 DM fine
	}

	protected boolean isOnlyCopyFilesFlagPresent(String[] args) {
		return getFlagPresent(ONLY_COPY_FLAG, args);
	}

	protected boolean isBackupFilesFlagPresent(String[] args) {
		return getFlagPresent(BACKUPS_FLAG, args);
	}

	protected boolean isCommitEachFixFlagPresent(String[] args) {
		return getFlagPresent(COMMIT_FLAG, args);
	}

	protected boolean isUniqueFixNumberFlagPresent(String[] args) {
		return getFlagPresent(UNIQUE_FIX_NUMBER, args);
	}

	protected boolean getHierarchicFlagPresent(String[] args) {
		return getFlagPresent(HIERARCHIC_FLAG, args);
	}
	//Fix 04576 PM-MM fine

	/* Revisions:
	 * Fix #    Date          Owner      Description
	 *  5116     2/ 3/2006    Ryo        Integrata la fix 4897 di Panthera (PM)
	 */
	protected boolean endApplyFix() throws Exception
	{
		return true;
	}

	//	Fix 5272 PM Inizio
	protected void resetAuthorizableFlag() throws Exception
	{
		System.out.println(ResourceLoader.getString(SETUP_RES, "StartRecalc"));//Mod. 9634//"=== Inizio ricalcolo del flag Authorizable delle entità ======");
		if(Entity.resetAuthorizableFlag(System.out) >= 0)
			System.out.println(ResourceLoader.getString(SETUP_RES, "RecalcSuccessful"));//Mod. 9634//"=== Ricalcolo del flag Authorizable delle entità completato con successo.");
		else
			System.out.println(ResourceLoader.getString(SETUP_RES, "RecalcFailed"));//Mod. 9634//"=== Ricalcolo del flag Authorizable delle entità fallito.");
	}
	//	Fix 5272 PM Fine
	//	6239 DM inizio
	protected void logAdvanceFixesTableNotFound() {
		output.println(ResourceLoader.getString(SETUP_RES, "WarningString"));//Mod. 9634//"#WARNING#");//Mod. 12771
		output.println(ResourceLoader.getString(SETUP_RES, "NoTableFoundErr",  new String[] {AdvanceFixesManager.TAB_NAME}));//Mod. 9634//"Non è stata trovata la tabella " + AdvanceFixesManager.TAB_NAME);
		output.flush();
	}

	protected void logAdvanceFixesError(Map unadjustedFixes) {
		output.println(ResourceLoader.getString(SETUP_RES, "ErrorString"));//Mod. 9634//"#ERRORE#");//Mod. 12771
		output.println(ResourceLoader.getString(SETUP_RES, "NoHealingFix"));//Mod. 9634//"Tra le fix che stanno per essere applicate non esistono le seguenti fix risanatrici:");
		output.println(ResourceLoader.getString(SETUP_RES, "HealingFix"));//Mod. 9634//"FIX RISANATRICE   FIX ANTICIPATE CHE LA RICHIEDONO");
		output.println("==================================================");
		Iterator i = unadjustedFixes.entrySet().iterator();
		while (i.hasNext())
		{
			Map.Entry e = (Map.Entry)i.next();
			String adj = Utils.format(e.getKey(), -18);
			String adv = e.getValue().toString();
			adv = adv.substring(1, adv.length() - 1);
			output.println(adj + adv);
			System.out.println("");//Mod. 14420
			System.out.println(adj + adv);//Mod. 14420
		}
		output.println(ResourceLoader.getString(SETUP_RES, "NoCont"));//Mod. 9634//"Impossibile continuare a meno che le fix anticipate riportate non vengano sospese.");
		output.flush();
		System.out.println(ResourceLoader.getString(SETUP_RES, "NoCont"));//Mod. 14420
		System.out.println("");//Mod. 14420
	}
	//	6239 DM fine
	//	6943 DM inizio
	protected static String getOracleDDLForView(String schema, String name) throws SQLException {
		if(cvGetOracleDDLForViewStmt == null)
			cvGetOracleDDLForViewStmt = new CachedStatement("select dbms_metadata.get_ddl('VIEW', ?, ?) from dual");
		PreparedStatement ps = cvGetOracleDDLForViewStmt.getStatement();
		ps.setString(1, name);
		ps.setString(2, schema);
		ResultSet rs = ps.executeQuery();
		String ddl = null;
		if(rs.next())
			ddl = rs.getString(1).trim();
		rs.close();
		return ddl;
	}
	//	6943 DM fine

	//07935 - DF (indicati da PM)
	protected BufferedReader getFixDescriptionListFile() {
		//28381 inizio
		//return getFile(FixDescriptionListFrame.FILE_NAME);
		return isStandalone() ? getFile(FixDescriptionListFrame.FILE_NAME) : getFixFile(FixDescriptionListFrame.FILE_NAME);
		//28381 fine		
	}

	protected BufferedReader getFixOrderFile() {
		//28381 inizio
		//return getFile(FILE_NAME);
		return isStandalone() ? getFile(FILE_NAME) : getFixFile(FILE_NAME);
		//28381 fine		
	}

	protected BufferedReader getViewSchemaFile() {
		//28381 inizio
		//return getFile(VIEWSCHEMA_FILE);
		return isStandalone() ? getFile(VIEWSCHEMA_FILE) : getFixFile(VIEWSCHEMA_FILE);
		//28381 fine
	}

	protected BufferedReader getApplicationFile() {
		//28381 inizio
		//return getFile(APPS_FILE);
		return isStandalone() ? getFile(APPS_FILE) : getFixFile(APPS_FILE);
		//28381 fine
	}

	//07935 - Fine

//Mod. 13978 inizio
	/**Mod.2258
	 * Metodo che esegue le copie di file richieste dal parametro -CF2.
	 * @param boolean righto precedente valore che indica la correttezza del processo
	 * @param String fRoot root path delle fix, quindi root dei file da copiare
	 * @param String pathFix root della fix che si sta esaminando
	 * @param String nFix numero della fix che si sta esaminando
	 * @return boolean true se è andato tutto bene o se mi mancavano parametri e quindi non ho fatto la copia
	 *                 false se avevo i parametri necessari, ho cercato di fare le copie, ma qualcosa non ha funzionato
	 */
	public boolean copy_CF2(boolean righto, String fRoot, String pathFix, String nFix) throws Exception {
		//Verifico se ho tutti i parametri che mi servono:
		// Se non ho il numero corretto di parametri segnalo ed esco senza copiare
		if (incompleteCF2Param) {
			System.out.println("");
			System.out.println(ResourceLoader.getString(SETUP_RES, "CF2incomplete", new String[] {nFix}));
			output.println(" ");
			output.println(ResourceLoader.getString(SETUP_RES, "WarningString"));
			output.println(ResourceLoader.getString(SETUP_RES, "CF2incomplete", new String[] {nFix}));
			return true;
		}
		// Se manca CF segnalo ed esco senza copiare
		if (CF2MissesCF) {
			System.out.println("");
			System.out.println(ResourceLoader.getString(SETUP_RES, "CF2missingCF", new String[] {nFix}));
			output.println(" ");
			output.println(ResourceLoader.getString(SETUP_RES, "WarningString"));
			output.println(ResourceLoader.getString(SETUP_RES, "CF2missingCF", new String[] {nFix}));
			return true;
		}

		boolean server2_present = FileUtil.exists(fRoot + NAME_FIX_SERVER_2);
		boolean client2_present = FileUtil.exists(fRoot + NAME_FIX_CLIENT_2);
		boolean reports2_present = FileUtil.exists(fRoot + NAME_FIX_REPORTS_2);
		boolean reportsCR20082_present = FileUtil.exists(fRoot + NAME_FIX_REPORTS_CR2008_2);
		boolean serverdiff2_present = FileUtil.exists(fRoot + NAME_FIX_SERVERDIFF_2);
		boolean websrc_present = FileUtil.exists(fRoot + NAME_FIX_WEBSRC_2);
		boolean clientweb_present = FileUtil.exists(fRoot + NAME_FIX_CLIENTWEB_2);
		boolean clientwebdiff_present = FileUtil.exists(fRoot + NAME_FIX_CLIENTWEBDIFF_2);
		boolean resources_present = FileUtil.exists(fRoot + NAME_FIX_RESOURCES_2);
		//Visto che non voglio bloccare le fix se la copia di -CF2 non funziona restituisco comunque true e segnalo
		//Se NON C'E' il parametro con la DESTINAZIONE, ma C'E' la DIR SORGENTE scrivo un messaggio sul log
		boolean ignoreCf2 = false;
		if (copyFiles2) {
			if (!copyClient2 && client2_present) {
				output.println(" ");
				output.println(ResourceLoader.getString(SETUP_RES, "WarningString"));
				output.println(ResourceLoader.getString(SETUP_RES, "ErrInCopyPrimroseFolders", new String[] {NAME_FIX_CLIENT_2, nFix}));
				System.out.println(ResourceLoader.getString(SETUP_RES, "ErrInCopyPrimroseFolders", new String[] {NAME_FIX_CLIENT_2, nFix}));
				ignoreCf2 = true;
			}
			if (!copyServer2 && server2_present) {
				output.println(" ");
				output.println(ResourceLoader.getString(SETUP_RES, "WarningString"));
				output.println(ResourceLoader.getString(SETUP_RES, "ErrInCopyPrimroseFolders", new String[] {NAME_FIX_SERVER_2, nFix}));
				System.out.println(ResourceLoader.getString(SETUP_RES, "ErrInCopyPrimroseFolders", new String[] {NAME_FIX_SERVER_2, nFix}));
				ignoreCf2 = true;
			}
			if (!copyServer2 && serverdiff2_present) {
				output.println(" ");
				output.println(ResourceLoader.getString(SETUP_RES, "WarningString"));
				output.println(ResourceLoader.getString(SETUP_RES, "ErrInCopyPrimroseFolders", new String[] {NAME_FIX_WEBSRC_2, nFix}));
				System.out.println(ResourceLoader.getString(SETUP_RES, "ErrInCopyPrimroseFolders", new String[] {NAME_FIX_WEBSRC_2, nFix}));
				ignoreCf2 = true;
			}
			if (!copyWeb && websrc_present) {
				output.println(" ");
				output.println(ResourceLoader.getString(SETUP_RES, "WarningString"));
				output.println(ResourceLoader.getString(SETUP_RES, "ErrInCopyPrimroseFolders", new String[] {NAME_FIX_WEBSRC_2, nFix}));
				System.out.println(ResourceLoader.getString(SETUP_RES, "ErrInCopyPrimroseFolders", new String[] {NAME_FIX_WEBSRC_2, nFix}));
				ignoreCf2 = true;
			}
			if (!copyReports2 && reports2_present) {
				output.println(" ");
				output.println(ResourceLoader.getString(SETUP_RES, "WarningString"));
				output.println(ResourceLoader.getString(SETUP_RES, "ErrInCopyPrimroseFolders", new String[] {this.NAME_FIX_REPORTS_2, nFix}));
				System.out.println(ResourceLoader.getString(SETUP_RES, "ErrInCopyPrimroseFolders", new String[] {this.NAME_FIX_REPORTS_2, nFix}));
				ignoreCf2 = true;
			}
		}

		//Se c'è il parametro che indica la destinazione ma è scorretto: segnalo ed ignoro -CF2
		if (server2_present && copyServer2 && !FileUtil.exists(getServerRoot2())) {
			System.out.println("");
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoServerRoot2", new String[] {nFix, getServerRoot2()}));
			output.println(" ");
			output.println(ResourceLoader.getString(SETUP_RES, "WarningString")); //Mod. 12771
			output.println(ResourceLoader.getString(SETUP_RES, "NoServerRoot2", new String[] {nFix, getServerRoot2()}));
					ignoreCf2 = true;
		}
		if (client2_present && copyClient2 && !FileUtil.exists(getClientRoot2())) {
			System.out.println("");
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoClientRoot2", new String[] {nFix, getClientRoot()}));
			output.println(" ");
			output.println(ResourceLoader.getString(SETUP_RES, "WarningString")); //Mod. 12771
			output.println(ResourceLoader.getString(SETUP_RES, "NoClientRoot2", new String[] {nFix, getClientRoot()}));
			ignoreCf2 = true;
		}
		if (reports2_present && copyReports2 && !FileUtil.exists(getReportsRoot2())) {
			System.out.println("");
			System.out.println(ResourceLoader.getString(SETUP_RES, "NoReportsRoot2", new String[] {nFix, getReportsRoot()}));
			output.println(" ");
			output.println(ResourceLoader.getString(SETUP_RES, "WarningString")); //Mod. 12771
			output.println(ResourceLoader.getString(SETUP_RES, "NoReportsRoot2", new String[] {nFix, getReportsRoot()}));
			ignoreCf2 = true;
		}

		//Se i parametri sono corretti: eseguo la copia
		//altrimenti: ignoro -CF2, lo segnalo e proseguo senza bloccare il processo
		if (!ignoreCf2) {
			//Se i parametri sono corretti eseguo le copie
			if ( (copyServer2 && server2_present || serverdiff2_present) ||
					(copyClient2 && client2_present) ||
					(copyReports2 && (reports2_present || reportsCR20082_present)) ||
					(copyWeb && websrc_present) ||
					(copyWeb && (resources_present || clientweb_present || clientwebdiff_present))) {
				output.println("");
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFiles", new String[] {nFix}));
			}

			//Copio la cartella fixRoot/.../server610  ==>  <server> = 1° PARAMETRO di -CF2
			//SE C'E' -CF2 + 1°param SERVER && C'E' LA DIR FIX/.../primrose/server610)
			if (copyServer2 && server2_present) {
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_SERVER_2, getServerRoot2()}));
				FileUtil.copyTree(fRoot + NAME_FIX_SERVER_2, getServerRoot2(), "*.*");
			}

			//Aggiorno usando la cartella fixRoot/.../serverdiff610  ==> aggiorno in <server> = 1° PARAMETRO di -CF2
			//SE C'E' -CF2 1°param SERVER && (C'E' LA DIR FIX/.../primrose/serverdiff610) ==> aggiorno con da FIX/.../primrose/serverdiff610/*.zip ==> 1° PARAMETRO=server
			 if (copyServer2 && serverdiff2_present) {
				 output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_SERVERDIFF_2, getServerRoot2()})); // Fix 5090 Ryo
				 String[] files = FileUtil.list(fRoot + NAME_FIX_SERVERDIFF_2, "*.zip");
				 righto = genericPrimroseJarBlender(files, fRoot, getServerRoot2(), NAME_FIX_SERVERDIFF_2, true);
			 }

			//Copio la cartella fixRoot/.../client610
			//SE C'E' -CF2 2°param CLIENT && (C'E' LA DIR FIX/.../primrose/client610) ==> copio da FIX/.../primrose/client610 ==> 2° PARAMETRO=client
			if (copyClient2 && client2_present) {
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_CLIENT_2, getClientRoot2()}));
				FileUtil.copyTree(fRoot + NAME_FIX_CLIENT_2, getClientRoot2(), "*.*");
			}

			 //Copio la cartella fixRoot/.../reports 1) nella directory passata come parametro report2 di -CF2
			 //                                      2) nella directory passata come parametro report di -CF * "FINANCE"
			 //                                      3) nella directory passata come parametro report di -CF * "cr2008/FINANCE"
			 //1) SE C'E' -CF2 3°param REPORTS && (C'E' LA DIR FIX/.../primrose/reports) ==> copio da FIX/.../primrose/reports/*.* ==> 3° PARAMETRO=reports
			 if (copyReports2 && reports2_present) {
				 output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_REPORTS_2, getReportsRoot2()}));
				 FileUtil.copyTree(fRoot + NAME_FIX_REPORTS_2, getReportsRoot2(), "*.*");
			 }
			 /* //Mod. 14621 inizio
			 //SE sotto la ...\reportsRoot2 c'è anche la dir /CR85: copio il contenuto dell'eventuale dir fix/.../reports anche in rptRoot/FINANCE/CR85
			//   e se c'è \reportsRoot2/CR82008 copio fix/.../reports2008 in in rptRoot/FINANCE/CR2008
			if (copyReports2 && (reports2_present || reportsCR20082_present)) {
				//(NON DISTINGUO TRA VER. 8.5 e 2008, quindi non implemento if else. In entrambi i casi come se fosse la 8.5
				String destDir1 = getRptRoot() + File.separator + NAME_APP_REPORTS + File.separator + PrintingToolInterface.CR85_PREFIX;
				if (FileUtil.exists(destDir1)) {
					output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_RPT, destDir1}));
					FileUtil.copyTree(fRoot + NAME_FIX_REPORTS_2, getReportsRoot2() + File.separator + PrintingToolInterface.CR85_PREFIX, "*.*");
					String destDir2 = getRptRoot() + File.separator + NAME_APP_REPORTS + File.separator + PrintingToolInterface.CR2008_PREFIX;
					if (FileUtil.exists(destDir2)) {
						output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_RPT_CR2008, destDir2}));
						FileUtil.copyTree(fRoot + NAME_FIX_RPT_CR2008, destDir2, "*.*");
					}
				}
			}
			*/
			//2) SE C'E' -CF 3°param REPORTS && (C'E' LA DIR FIX/.../primrose/reports) ==> copio da FIX/.../primrose/reports/*.* ==> 3° PARAMETRO(-CF)/FINANCE
			//3) SE C'E' -CF 3°param REPORTS && (C'E' LA DIR FIX/.../primrose/reports) ==> copio da FIX/.../primrose/reports/*.* ==> 3° PARAMETRO(-CF)/cr2008/FINANCE
			if (copyReports && reports2_present) {
				String reportsDestination = this.getRptRoot() + File.separator + NAME_APP_REPORTS;
					output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_REPORTS_2, reportsDestination}));
					FileUtil.copyTree(fRoot + NAME_FIX_REPORTS_2, reportsDestination, "*.*");

					reportsDestination = this.getRptRoot() + File.separator + NAME_APP_REPORTS_BIS + File.separator + NAME_APP_REPORTS;
					output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_REPORTS_2, reportsDestination}));
					FileUtil.copyTree(fRoot + NAME_FIX_REPORTS_2, reportsDestination, "*.*");
			}
			//fine mod. 14621

			//Copio la cartella fixRoot/.../websrc
			// Copio la cartella web - tranne i file TFML se ho la destinazione (presa da -CF)
			if (copyWeb) {
				if (websrc_present) {
					output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_WEBSRC_2, getWebRoot()}));
					FileUtil.copyTreeExcPattern(fRoot + NAME_FIX_WEBSRC_2, getWebRoot(), "*.tfml");
				}
				/* //Mod. 14621 inizio
				// Copio i .jar in web-inf/lib
				if (server2_present) {
					String weblibroot = getWebRoot() + File.separator + NAME_APP_WEBINF + File.separator + NAME_APP_WEBLIB;
					output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_SERVER_2, weblibroot}));
					String[] s = {"*.jar", "*.zip"};
					FileUtil.copyFiles(fRoot + NAME_FIX_SERVER_2, weblibroot, s);
				}
				//  Aggiorno i .jar in web-inf/lib
				if (serverdiff2_present) {
					String weblibroot = getWebRoot() + File.separator + NAME_APP_WEBINF + File.separator + NAME_APP_WEBLIB;
					output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_SERVER_2, weblibroot}));
					String[] files = FileUtil.list(fRoot + NAME_FIX_SERVERDIFF_2, "*.zip");
					righto = genericPrimroseJarBlender(files, fRoot, weblibroot, NAME_FIX_SERVERDIFF_2, true);
				}
				*/ //fine mod. 14621
			}

			//Copio la cartella fixRoot/.../resources
			if (copyWeb && resources_present) {
				String resourcesDestination = getWebRoot() + File.separator + NAME_APP_WEBINF + File.separator + NAME_APP_RESOURCES;
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_RESOURCES_2, resourcesDestination}));
				//22098 inizio
				deleteNlsFiles((fRoot + NAME_FIX_RESOURCES_2), resourcesDestination, "panelresource");
				deleteNlsFiles((fRoot + NAME_FIX_RESOURCES_2), resourcesDestination, "resource");
				//22098 fine
				FileUtil.copyTree(fRoot + NAME_FIX_RESOURCES_2, resourcesDestination, "*.*");
			}
			//Copio la cartella fixRoot/.../clientweb
			if (copyWeb && clientweb_present) {
				String clientwebDestination = getWebRoot() + File.separator + NAME_APP_WEBINF + File.separator + NAME_APP_WEBLIB;
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_CLIENTWEB_2, clientwebDestination}));
				FileUtil.copyTree(fRoot + NAME_FIX_CLIENTWEB_2, clientwebDestination, "*.*");
			}

			//Aggiorno usando la cartella fixRoot/.../clientwebdiff
			if (copyWeb && clientwebdiff_present) {
				String clientwebdiffDestination = getWebRoot() + File.separator + NAME_APP_WEBINF + File.separator + this.NAME_APP_WEBLIB;
				output.println(ResourceLoader.getString(SETUP_RES, "CopyFilesFromTo", new String[] {fRoot + NAME_FIX_CLIENTWEBDIFF_2, clientwebdiffDestination})); // Fix 5090 Ryo
				String[] files = FileUtil.list(fRoot + NAME_FIX_CLIENTWEBDIFF_2, "*.zip");
				righto = genericPrimroseJarBlender(files, fRoot, clientwebdiffDestination, NAME_FIX_CLIENTWEBDIFF_2, true);
			}
		}

		return righto;
	}
  //fine mod. 13978
	//22098 inizio
  protected void deleteNlsFiles(String resourcesSource, String resourcesDestination, String extension) {
    String destination = resourcesDestination;
    if (!destination.endsWith(File.separator)){
      destination += File.separator;
    }
    
    String[] sourceFiles = FileUtil.listTree(resourcesSource, ("*." + extension));
    for (int i = 0; i < sourceFiles.length; i++) {
      String sourceFile = sourceFiles[i];
      int underscoreIndex = sourceFile.length() - (extension.length() + 4); 
      if ((underscoreIndex <=  0) || (sourceFile.charAt(underscoreIndex) != '_')){
        String filename = sourceFile.substring(0, (sourceFile.length() - (extension.length() + 1)));
        
        String parentDir = filename.lastIndexOf(File.separatorChar) > 0 ? filename.substring(0, filename.lastIndexOf(File.separatorChar) + 1) : "";
        String name = filename.substring(parentDir.length());
        String destinationDir = destination + parentDir;
        
        String[] deletableFilenames = FileUtil.listTree(destinationDir, (name + "_*." + extension));
        for (int j = 0; j < deletableFilenames.length; j++) {
          FileUtil.deleteFile(destinationDir + deletableFilenames[j]);
        }
      }
    }
  }
  //22098 fine
  //28381 inizio
  public BufferedReader getFixFile(String filename) {
  	try {
			File file = new File(getFixRoot(), filename);
			if(file.exists()) {
				return new BufferedReader(new FileReader(file));
			}
		}
		catch (FileNotFoundException e) {
			e.printStackTrace(Trace.excStream);
		}
  	return null;
  }
  //28381 fine
  
  //33045 inizio
	protected BufferedReader getFullFixOrderFile() {
		return isStandalone() ? getFile(FixGetter.FULL_FIX_ORDER_FILE_NAME): getFixFile(FixGetter.FULL_FIX_ORDER_FILE_NAME);
	}
	
	protected Set<Integer> listFixes(BufferedReader br) throws Exception {
		 
		Set<Integer> listFixes = new HashSet<Integer>();
		if (br == null)
			return listFixes;
		String currLine = br.readLine();
		while (currLine != null) {
			currLine.trim();
			StringTokenizer st = new StringTokenizer(currLine, ":", false);
			if ((!currLine.startsWith(COMMENT)) && (!currLine.equals("")) && (!currLine.startsWith(APPLICATION))
					&& (!currLine.startsWith(DESCRIPTION)) && (!currLine.startsWith(UPDATE_APPL))) {
				String currFix = st.nextToken();
				listFixes.add(Integer.parseInt(currFix));
			}
			currLine = br.readLine();
		}
		br.close();
		return listFixes;
	}
	
	protected Set<Integer> listPrerequisites() throws Exception {
		Set<Integer> fixes = listFixes(getFixOrderFile());
		if (fixes.size() == 0) {
			System.out.println(ResourceLoader.getString(FixUnzipper.FIX_APPLYER_FRAME_RES, "NoFixFound"));
			return new HashSet<Integer>();
		}
		Set<Integer> prerequisites = listFixes(getFullFixOrderFile());
		prerequisites.removeAll(fixes);
		return prerequisites;
	}

	protected Set<Integer> listNotInstalledPrerequisites() throws Exception {
		Set<Integer> prerequisites = listPrerequisites();
		Set<Integer> notInstalledPrerequisites = new HashSet<Integer>(prerequisites);
		if (prerequisites.isEmpty())
			return notInstalledPrerequisites;
		StringBuffer where = new StringBuffer();
		boolean primo = true;
		for (Iterator<Integer> iterator = notInstalledPrerequisites.iterator(); iterator.hasNext();) {
			Integer fix = (Integer) iterator.next();
			if (primo)
			{
				where.append(fix);
				primo = false;
			}
			else 
				where.append(",").append(fix);
			
		}
		where.replace(0, 0, "SOFTWARE_FIX IN (")
			.append(")");
		Vector v = SoftwareFix.retrieveList(where.toString(), "", false);
		Iterator iter = v.iterator();
		while (iter.hasNext()) {
			Integer fix = ((SoftwareFix) (iter.next())).getSoftwareFix();
			notInstalledPrerequisites.remove(fix);
		}
		return notInstalledPrerequisites;
	}
	
	protected void logNotInstalledPrerequisites(Set<Integer> notInstalledPrerequisites) {
		String msg = ResourceLoader.getString(FixUnzipper.FIX_APPLYER_FRAME_RES, "PreviousFixRequired");
		System.out.println(msg);
		for (Iterator<Integer> iterator = notInstalledPrerequisites.iterator(); iterator.hasNext();) {
			Integer fix = (Integer) iterator.next();
			System.out.println(fix);
			
		}
	}
	  //33045 fine

	//Fix 33557 PM >
    public void setAggiornaTimestampFixCopyOnlyFile(boolean aggiornaTimestampFixCopyOnlyFile)
    {
    	iAggiornaTimestampFixCopyOnlyFile = aggiornaTimestampFixCopyOnlyFile;
    }
    
    public boolean isAggiornaTimestampFixCopyOnlyFile()
    {
    	return iAggiornaTimestampFixCopyOnlyFile;
    }
	//Fix 33557 PM <
    
}
