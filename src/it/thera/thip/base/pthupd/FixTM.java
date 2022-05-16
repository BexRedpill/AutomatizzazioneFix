package it.thera.thip.base.pthupd;

import java.sql.*;

import com.thera.thermfw.base.*;
import com.thera.thermfw.persist.*;

import it.thera.thip.base.pthupd.FixTM;
import it.thera.thip.cs.DatiComuniEstesiTTM;

/*
 * @(#)FixTM.java
 */

/**
 * FixTM
 */
/*
 * Revisions:
 * Number     Date          Owner      Description
 * 16217      12/04/2012    TF         Prima versione
 * 17416      05/02/2013    ES         Modificato il nome della colonna: da LEVEL a FIX_LEVEL
 * 31497	  30/06/2020	FB		   Aggiunti nuovi attributi tra cui Dati comuni estesi
 * 31937	  29/09/2020	FB		   Nuovi attributi
 * 32117	  11/11/2020	FB		   Nuovi attributi
 * 32761	  18/01/2021	FB		   Nuovo attributo
 * 33217	  31/03/2021	FG		   Nuovi attributi
 * 33570      11/05/2021    FG		   Aggiunti nuovi attributi timestamp ultimo aggiornamento zip fix e reinstallazione fix
 * 33991	  30/06/2021    FG         Aggiunto attributo "NonPubblicabileMP" (Non pubblicabile per mancanza prerequisiti)
 */ 

public class FixTM extends TableManager {

	public static final String FIX = "FIX";
	public static final String MODULE = "MODULE";
	public static final String VERSION = "VERSION";
	public static final String RELEASE = "RELEASE";
	public static final String MODIFICATION = "MODIFICATION";
	public static final String DOMAIN = "DOMAIN";
	public static final String AREA = "AREA";
	public static final String DESCRIPTION = "DESCRIPTION";
	//Mod. 17416//public static final String LEVEL = "LEVEL";
	public static final String LEVEL = "FIX_LEVEL";//Mod. 17416//
	public static final String STATUS = "STATUS";
	public static final String RELEASE_DATE = "RELEASE_DATE";
	public static final String TS = "TS";
	
	//31497 ini
	public static final String PACCHETTO = "PACCHETTO";
	public static final String FIX_PACCHETTO = "FIX_PACCHETTO";
	public static final String FIX_IN_PACCHETTO = "FIX_IN_PACCHETTO";
	public static final String TIPO_IMPLEMENTO = "TIPO_IMPLEMENTO";
	public static final String PREREQ = "PREREQ";
	public static final String DATA_EMISSIONE = "DATA_EMISSIONE";
	public static final String PERCORSO_FUNZIONE= "PERCORSO_FUNZIONE";
	public static final String FUNZIONE = "FUNZIONE";
	public static final String SUBFUNZIONE = "SUBFUNZIONE";
	public static final String COD_RIF_HD = "COD_RIF_HD";
	public static final String COD_RIF_PTH = "COD_RIF_PTH";
	public static final String FIX_DESCRIPTION_HD = "FIX_DESCRIPTION_HD";
	public static final String UNID = "UNID";
	public static final String TIMESTAMP_ONLINE = "TIMESTAMP_ONLINE";
	public static final String VRM = "VRM";
	public static final String STATO_EMISSIONE = "STATO_EMISSIONE";
	public static final String STATO = "STATO";
	public static final String R_UTENTE_CRZ = "R_UTENTE_CRZ";
	public static final String R_UTENTE_AGG = "R_UTENTE_AGG";
	public static final String TIMESTAMP_CRZ = "TIMESTAMP_CRZ";
	public static final String TIMESTAMP_AGG = "TIMESTAMP_AGG";
	public static final String ALLEGATO_LX = "ALLEGATO_LX";
	public static final String ALLEGATO_P6 = "ALLEGATO_P6";
	public static final String ALLEGATO_WN = "ALLEGATO_WN";
	public static final String TS_CONNETTORE = "TS_CONNETTORE";
	//31497 fine

	//31937 ini
	public static final String VERIFICA_LAB = "VERIFICA_LAB";
	//31937 fine
	
	//32117 ini
	public static final String ALLEGATO_ESTERNO = "ALLEGATO_ESTERNO";
	public static final String PTH_UNID = "PTH_UNID";
	public static final String RISOLVE = "RISOLVE";
	//32117 fine
	
	public static final String DATA_DEFINITIVA = "DATA_DEFINITIVA";//32761
	
	//33217 ini
	public static final String INST_FIX = "INST_FIX"; 
	public static final String INST_FIX_SCHED = "INST_FIX_SCHED";
	public static final String INSTALLATA_HD = "INSTALLATA_HD";
	public static final String NON_PUBBLICABILE ="NON_PUBBLICABILE";
	//33217 fine
	
	//33570 ini
	public static final String TS_AGG_ZIP_FIX = "TS_AGG_ZIP_FIX";
	public static final String TS_REINSTALL_FIX = "TS_REINSTALL_FIX";
	//33570 fine
	
	public static final String NON_PUBBLICABILE_MP = "NON_PUBBLICABILE_MP";//33991
	
	//35629 ini
	public static final String TIPO_INSTALLAZIONE = "TIPO_INSTALLAZIONE";
	public static final String FORZA_FIX_ANTICIP = "FORZA_FIX_ANTICIP";
	//35629 fine
	
	public static final String TABLE_NAME = SystemParam.getSchema("THIP") + "FIXES";

	private static TableManager cInstance;

	private static final String CLASS_NAME = it.thera.thip.base.pthupd.Fix.class.getName();

	public synchronized static TableManager getInstance() throws SQLException {
		if (cInstance == null) {
			cInstance = (TableManager) Factory.createObject(FixTM.class);
		}
		return cInstance;
	}

	public FixTM() throws SQLException {
		super();
	}

	protected void initialize() throws SQLException {
		setTableName(TABLE_NAME);
		setObjClassName(CLASS_NAME);
		init();
	}

	protected void initializeRelation() throws SQLException {
		super.initializeRelation();
		addAttribute("Fix", FIX, "getIntegerObject");
		addAttribute("Module", MODULE);
		addAttribute("Version", VERSION);
		addAttribute("Release", RELEASE);
		addAttribute("Modification", MODIFICATION);
		addAttribute("Domain", DOMAIN);
		addAttribute("Area", AREA);
		addAttribute("Description", DESCRIPTION);
		addAttribute("Level", LEVEL);
		addAttribute("Status", STATUS);
		addAttribute("ReleaseDate", RELEASE_DATE);
		//addTimestampAttribute("Timestamp", TS);  //31497
		addAttribute("Timestamp",TS);
		//31497 ini
		//addAttribute("Timestamp",TS);
		addAttribute("Pacchetto",PACCHETTO);
		addAttribute("FixPacchetto",FIX_PACCHETTO);
		addAttribute("FixInPacchetto",FIX_IN_PACCHETTO);
		addAttribute("TipoImplemento", TIPO_IMPLEMENTO);
		addAttribute("Prereq", PREREQ);
		addAttribute("DataCreazione",DATA_EMISSIONE);
		addAttribute("PercorsoFunzione",PERCORSO_FUNZIONE);
		addAttribute("Funzione",FUNZIONE);
		addAttribute("SubFunzione",SUBFUNZIONE);
		addAttribute("FixDescriptionHD",FIX_DESCRIPTION_HD);
		addAttribute("UNID",UNID);
		addAttribute("TimestampOnline",TIMESTAMP_ONLINE);
		addAttribute("RifHD",COD_RIF_HD);
		addAttribute("RifPTH", COD_RIF_PTH);
		addAttribute("VRM",VRM);
		addAttribute("FixStatusEmissione",STATO_EMISSIONE);
		addAttribute("AllegatoLX",ALLEGATO_LX);
		addAttribute("AllegatoP6",ALLEGATO_P6);
		addAttribute("AllegatoWN",ALLEGATO_WN);
		addAttribute("TimestampConnettore",TS_CONNETTORE);
		addAttribute("TipoInstallazione", TIPO_INSTALLAZIONE); //35629
		addAttribute("ForzaFixAnticip", FORZA_FIX_ANTICIP); //35629
		//Dati comuni estesi
		addComponent("DatiComuniEstesi", DatiComuniEstesiTTM.class);
	    setTimestampColumn(TIMESTAMP_AGG);
		((DatiComuniEstesiTTM)getTransientTableManager("DatiComuniEstesi")).setExcludedColums();
		
		//31497 fine
		
		addAttribute("VerificaLaboratorio", VERIFICA_LAB); //31937
		
		addAttribute("AllegatoEsterno",ALLEGATO_ESTERNO); //32117
		addAttribute("UNIDPth",PTH_UNID); //32117
		addAttribute("Risolve",RISOLVE); //32117
		
		addAttribute("DataDefinitiva", DATA_DEFINITIVA); //32761
		
		addAttribute("InstallataHD", INSTALLATA_HD);//33217
		addAttribute("InstallazioneFix", INST_FIX);//33217
		addAttribute("InstallazioneFixSchedul", INST_FIX_SCHED);//33217
		addAttribute("FixNonPubblicabile", NON_PUBBLICABILE);//33217
		
		addAttribute("TimestampUltimoAggZipFix", TS_AGG_ZIP_FIX); //33570
		addAttribute("TimestampReinstallataHD", TS_REINSTALL_FIX); //33570
		
		addAttribute("NonPubblicabileMP", NON_PUBBLICABILE_MP); //33991
		
		setKeys(FIX);
	}

	private void init() throws SQLException {
		configure();
	}

}

