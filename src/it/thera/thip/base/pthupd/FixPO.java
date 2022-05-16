package it.thera.thip.base.pthupd;

import java.sql.*;
import java.sql.Date;
import java.util.*;

import com.thera.thermfw.base.TimeUtils;
import com.thera.thermfw.base.Trace;
import com.thera.thermfw.common.*;
import com.thera.thermfw.persist.*;
import com.thera.thermfw.security.*;
import com.thera.thermfw.setup.SoftwareFixTM;

import it.thera.thip.base.pthupd.Fix;
import it.thera.thip.base.pthupd.FixPO;
import it.thera.thip.base.pthupd.FixTM;
import it.thera.thip.cs.DatiComuniEstesi;
import it.thera.thip.cs.PersistentObjectDCE;

/*
 * @(#)FixPO.java
 */

/**
 * Fix
 */
/*
 * Revisions:
 * Number     Date          Owner      Description
 * 16217      12/04/2012    TF         Prima versione
 * 31497	  30/06/2020	FB		   Aggiunti alcuni attributi
 * 31937	  29/09/2020	FB		   Nuovi attributi. Modificata OneToMany verso FixTicket/FixDocumentation per recepire le modifiche fatte su quella classe
 * 32117	  11/11/2020	FB		   Nuovi attributi per gestione allegato esterno.
 * 32761	  18/01/2021	FB		   Aggiunto attributo Data Definitiva
 * 32810	  09/04/2021	FB		   Cambio tipo all'attributo Allegato Esterno
 * 33217      31/03/2021    FG		   Nuovi attributi
 * 33570	  11/05/2021	FG		   Nuovi attributi
 * 33991      30/06/2021    FG         Nuovo attributo "NonPubblicabileMP" (Non pubblicabile per mancanza prerequisiti)
 */

public abstract class FixPO extends PersistentObjectDCE implements BusinessObject, Authorizable, Deletable, ConflictableWithKey {

	private static Fix cInstance;

	//--Attributi
	protected Integer iFix;
	protected char iModule = Fix.PANTH_GESTIONALE;
	protected short iVersion;
	protected short iRelease;
	protected short iModification;
	protected String iDomain;
	protected String iArea;
	protected String iDescription;
	protected char iLevel = Fix.ALTA;
	protected char iStatus = Fix.TEST_RS;
	protected java.sql.Date iReleaseDate;
	protected java.sql.Timestamp iTimestamp = TimeUtils.getCurrentTimestamp();  
	//31497 ini
	protected char iPacchetto=Fix.FIX_SINGOLA;
	protected Integer iFixPacchetto;
	protected String iFixInPacchetto;
	protected char iTipoImplemento;
	protected String iPrereq;
	protected Date iDataCreazione;
	protected String iPercorsoFunzione;
	protected String iFunzione;
	protected String iSubFunzione;
	protected String iRifHD;
	protected String iRifPTH;
	protected String iFixDescriptionHD;
	protected String iUNID;
	protected Timestamp iTimestampOnline;
	protected String iVRM;
	protected char iFixStatusEmissione=Fix.NON_DEFINITO;
	protected String iAllegatoLX;
	protected String iAllegatoP6;
	protected String iAllegatoWN;
	protected Timestamp iTimestampConnettore;
	//31497 fine

	//31937 ini
	protected char iVerificaLaboratorio;
	//31937 fine

	//32117 ini
	protected String iUNIDPth;
	//protected boolean iAllegatoEsterno=false; //32810
	protected String iRisolve;
	//32117 fine

	protected Date iDataDefinitiva; //32761

	protected char iAllegatoEsterno = 'I'; //32810

	protected char iInstallazioneFix = Fix.NON_INSTALLATA; //33217
	protected char iInstallazioneFixSchedul = Fix.NON_INSTALLATA; //33217
	protected boolean iInstallataHD = false; //33217
	protected boolean iFixNonPubblicabile = false; //33217

	//33570 ini
	protected Timestamp iTimestampReinstallataHD;
	protected Timestamp iTimestampUltimoAggZipFix;
	//33570 fine

	//33991 ini
	protected boolean iNonPubblicabileMP;
	//33991 fine

	//--OneToMany
	//31937
	// protected OneToMany iTicket = new OneToMany(it.thera.thip.base.pthupd.FixTicket.class, this, 1, false);
	protected OneToMany iTicket = new OneToMany(it.thera.thip.base.pthupd.FixTicketWrapper.class, this, 1, false); //31937
	protected OneToMany iChange = new OneToMany(it.thera.thip.base.pthupd.FixChange.class, this, 1, false);
	//31937
	//protected OneToMany iDocumentation = new OneToMany(it.thera.thip.base.pthupd.FixDocumentation.class, this, 1, false);
	protected OneToMany iDocumentation = new OneToMany(it.thera.thip.base.pthupd.FixDocumentationWrapper.class, this, 1, false);
	protected OneToMany iPrerequisiti = new OneToMany(it.thera.thip.base.pthupd.FixPrerequisito.class, this, 1, false);

	//31497
	protected OneToMany iContenutoPacchetto = new OneToMany(it.thera.thip.base.pthupd.FixesPacks.class, this, 1, false);

	protected char iTipoInstallazione = Fix.TI_NON_INSTALLATA; //35629
	protected boolean iForzaFixAnticip; //35629

	public static Vector retrieveList(String where, String orderBy, boolean optimistic) throws SQLException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		if (cInstance == null)
			cInstance = (Fix) Factory.createObject(Fix.class);
		return PersistentObject.retrieveList(cInstance, where, orderBy, optimistic);
	}

	public static Fix elementWithKey(String key, int lockType) throws SQLException {
		return (Fix) PersistentObject.elementWithKey(Fix.class, key, lockType);
	}

	public void setFix(Integer fix) {
		this.iFix = fix;
		setDirty();
		setOnDB(false);
		iTicket.setFatherKeyChanged();
		iChange.setFatherKeyChanged();
		iDocumentation.setFatherKeyChanged();
	}

	public char getTipoInstallazione() {
		return iTipoInstallazione;
	}

	public void setTipoInstallazione(char iTipoInstallazione) {
		this.iTipoInstallazione = iTipoInstallazione;
		setDirty();
	}

	public boolean getForzaFixAnticip() {
		return iForzaFixAnticip;
	}

	public void setForzaFixAnticip(boolean iForzaFixAnticipate) {
		this.iForzaFixAnticip = iForzaFixAnticipate;
		setDirty();
	}

	public Integer getFix() {
		return iFix;
	}

	public void setModule(char module) {
		this.iModule = module;
		setDirty();
	}

	public char getModule() {
		return iModule;
	}

	public void setVersion(short version) {
		this.iVersion = version;
		setDirty();
	}

	public short getVersion() {
		return iVersion;
	}

	public void setRelease(short release) {
		this.iRelease = release;
		setDirty();
	}

	public short getRelease() {
		return iRelease;
	}

	public void setModification(short modification) {
		this.iModification = modification;
		setDirty();
	}

	public short getModification() {
		return iModification;
	}

	public void setDomain(String domain) {
		this.iDomain = domain;
		setDirty();
	}

	public String getDomain() {
		return iDomain;
	}

	public void setArea(String area) {
		this.iArea = area;
		setDirty();
	}

	public String getArea() {
		return iArea;
	}

	public void setDescription(String description) {
		this.iDescription = description;
		setDirty();
	}

	public String getDescription() {
		return iDescription;
	}

	public void setLevel(char level) {
		this.iLevel = level;
		setDirty();
	}

	public char getLevel() {
		return iLevel;
	}

	public void setStatus(char status) {
		this.iStatus = status;
		setDirty();
	}

	public char getStatus() {
		return iStatus;
	}

	public void setReleaseDate(java.sql.Date releaseDate) {
		this.iReleaseDate = releaseDate;
		setDirty();
	}

	public java.sql.Date getReleaseDate() {
		return iReleaseDate;
	}

	//fix 31497
	/*
  public void setTimestamp(java.sql.Timestamp timestamp) {
    this.iTimestamp = timestamp;

  }

  public java.sql.Timestamp getTimestamp() {
    return iTimestamp;
  }
	 */

	public void setTs(Timestamp t) {
		this.iTimestamp=t;
		setDirty();
	}

	public Timestamp getTs() {
		return iTimestamp;
	}

	public List getTicket() {
		return getTicketInternal();
	}

	public List getChange() {
		return getChangeInternal();
	}

	public List getPrerequisiti() {
		return getPrerequisitiInternal();
	}

	public List getDocumentation() {
		return getDocumentationInternal();
	}

	public void setEqual(Copyable obj) throws CopyException {
		super.setEqual(obj);
		FixPO fixPO = (FixPO) obj;
		if (fixPO.iReleaseDate != null)
			iReleaseDate = (java.sql.Date) fixPO.iReleaseDate.clone();
		//31497 ini
		/*
    if (fixPO.iTimestamp != null)
      iTimestamp = (java.sql.Timestamp) fixPO.iTimestamp.clone();
		 */
		if(fixPO.getTimestamp()!=null)
			setTimestamp(fixPO.getTimestamp());
		//31497 fine
		iTicket.setEqual(fixPO.iTicket);
		iChange.setEqual(fixPO.iChange);
		iPrerequisiti.setEqual(fixPO.iPrerequisiti);
		iDocumentation.setEqual(fixPO.iDocumentation);
		iContenutoPacchetto.setEqual(fixPO.iContenutoPacchetto); //31497
	}

	public Vector checkAll(BaseComponentsCollection components) {
		Vector errors = new Vector();
		components.runAllChecks(errors);
		return errors;
	}

	public void setKey(String key) {
		setFix(KeyHelper.stringToIntegerObj(key));
	}

	public String getKey() {
		return KeyHelper.objectToString(getFix());
	}

	public boolean isDeletable() {
		return checkDelete() == null;
	}

	public int saveOwnedObjects(int rc) throws SQLException {
		rc = iTicket.save(rc);
		rc = iChange.save(rc);
		rc = iPrerequisiti.save(rc);
		rc = iDocumentation.save(rc);
		rc = iContenutoPacchetto.save(rc);
		return rc;
	}

	public int deleteOwnedObjects() throws SQLException {
		int rcTicket = getTicketInternal().delete();
		if (rcTicket < ErrorCodes.NO_ROWS_UPDATED)
			return rcTicket;
		int rcChange = getChangeInternal().delete();
		if (rcChange < ErrorCodes.NO_ROWS_UPDATED)
			return rcChange;
		int rcPrerequisiti = getPrerequisitiInternal().delete();
		if (rcPrerequisiti < ErrorCodes.NO_ROWS_UPDATED)
			return rcPrerequisiti;
		int rcDocumentation = getDocumentationInternal().delete();
		if (rcDocumentation < ErrorCodes.NO_ROWS_UPDATED)
			return rcDocumentation;
		return rcTicket + rcChange + rcPrerequisiti + rcDocumentation;
	}

	public boolean initializeOwnedObjects(boolean result) {
		result = iTicket.initialize(result);
		result = iChange.initialize(result);
		result = iPrerequisiti.initialize(result);
		result = iDocumentation.initialize(result);
		result = iContenutoPacchetto.initialize(result); //31497
		return result;
	}

	public String toString() {
		return getClass().getName() + " [" + KeyHelper.formatKeyString(getKey()) + "]";
	}

	protected TableManager getTableManager() throws SQLException {
		return FixTM.getInstance();
	}

	protected OneToMany getTicketInternal() {
		if (iTicket.isNew())
			iTicket.retrieve();
		return iTicket;
	}

	protected OneToMany getChangeInternal() {
		if (iChange.isNew())
			iChange.retrieve();
		return iChange;
	}

	protected OneToMany getPrerequisitiInternal() {
		if (iPrerequisiti.isNew())
			iPrerequisiti.retrieve();
		return iPrerequisiti;
	}

	protected OneToMany getDocumentationInternal() {
		if (iDocumentation.isNew())
			iDocumentation.retrieve();
		return iDocumentation;
	}

	//31497 ini

	protected OneToMany getContenutoPacchettoInternal() {
		if(iContenutoPacchetto.isNew())
			iContenutoPacchetto.retrieve();
		return iContenutoPacchetto;
	}

	public List getContenutoPacchetto() {
		return getContenutoPacchettoInternal();
	}

	//31497 ini getter/setter

	public char getPacchetto() {
		return iPacchetto;
	}

	public void setPacchetto(char b) {
		this.iPacchetto=b;
		setDirty();
	}

	public Integer getFixPacchetto() {
		return iFixPacchetto;
	}

	public void setFixPacchetto(Integer iCodicePacchetto) {
		this.iFixPacchetto = iCodicePacchetto;
		setDirty();
	}

	public String getFixInPacchetto() {
		return iFixInPacchetto;
	}

	public void setFixInPacchetto(String iFixContenutePacchetto) {
		this.iFixInPacchetto = iFixContenutePacchetto;
		setDirty();
	}

	public char getTipoImplemento() {
		return iTipoImplemento;
	}

	public void setTipoImplemento(char iTipoImplemento) {
		this.iTipoImplemento = iTipoImplemento;
		setDirty();
	}

	public String getPrereq() {
		return iPrereq;
	}

	public void setPrereq(String iPrereq) {
		this.iPrereq = iPrereq;
		setDirty();
	}

	public Date getDataCreazione() {
		return iDataCreazione;
	}

	public void setDataCreazione(Date iDataCreazione) {
		this.iDataCreazione = iDataCreazione;
		setDirty();
	}

	public String getPercorsoFunzione() {
		return iPercorsoFunzione;
	}

	public void setPercorsoFunzione(String iPercorsoFunzione) {
		this.iPercorsoFunzione = iPercorsoFunzione;
		setDirty();
	}

	public String getFunzione() {
		return iFunzione;
	}

	public void setFunzione(String iFunzione) {
		this.iFunzione = iFunzione;
		setDirty();
	}

	public String getSubFunzione() {
		return iSubFunzione;
	}

	public void setSubFunzione(String iSubFunzione) {
		this.iSubFunzione = iSubFunzione;
		setDirty();
	}

	public String getRifHD() {
		return iRifHD;
	}

	public void setRifHD(String iRifHD) {
		this.iRifHD = iRifHD;
		setDirty();
	}

	public String getRifPTH() {
		return iRifPTH;
	}

	public void setRifPTH(String iRifPTH) {
		this.iRifPTH = iRifPTH;
		setDirty();
	}

	public String getFixDescriptionHD() {
		return iFixDescriptionHD;

	}

	public void setFixDescriptionHD(String iFixDescriptionHD) {
		this.iFixDescriptionHD = iFixDescriptionHD;
		setDirty();
	}

	public String getUNID() {
		return iUNID;
	}

	public void setUNID(String iUNID) {
		this.iUNID = iUNID;
		setDirty();
	}

	public Timestamp getTimestampOnline() {
		return iTimestampOnline;
	}

	public void setTimestampOnline(Timestamp iTimestampOnline) {
		this.iTimestampOnline = iTimestampOnline;
		setDirty();
	}

	public String getVRM() {
		return iVRM;
	}

	public void setVRM(String vrm) {
		this.iVRM=vrm;
		setDirty();
	}

	public char getFixStatusEmissione() {
		return this.iFixStatusEmissione;
	}

	public void setFixStatusEmissione(char a) {
		this.iFixStatusEmissione=a;
		setDirty();
	}


	public Date getDataInstallazione() {
		try {
			String sql = "SELECT "+SoftwareFixTM.CREATION_TIMESTAMP +
					" FROM "+SoftwareFixTM.TABLE_NAME+" WHERE "+SoftwareFixTM.SOFTWARE_FIX+"="+getFix().intValue();
			CachedStatement stmt = new CachedStatement(sql);
			ResultSet rs = stmt.executeQuery();
			if(rs.next()) {
				Timestamp tsInstallazione = rs.getTimestamp(SoftwareFixTM.CREATION_TIMESTAMP);
				return TimeUtils.getDate(tsInstallazione);
			}
			return null;
		}catch(SQLException ex) {
			ex.printStackTrace(Trace.excStream);
			return null;
		}
	}

	public String getAllegatoLX() {
		return this.iAllegatoLX;
	}

	public void setAllegatoLX(String a) {
		this.iAllegatoLX=a;
		setDirty();
	}

	public String getAllegatoP6() {
		return this.iAllegatoP6;
	}

	public void setAllegatoP6(String a) {
		this.iAllegatoP6=a;
		setDirty();
	}

	public String getAllegatoWN() {
		return this.iAllegatoWN;
	}

	public void setAllegatoWN(String a) {
		this.iAllegatoWN=a;
		setDirty();
	}

	public Timestamp getTimestampConnettore() {
		return iTimestampConnettore;
	}

	public void setTimestampConnettore(Timestamp ts) {
		iTimestampConnettore=ts;
		setDirty();
	}
	//31497 fine getter/setter

	//31937 ini
	public char getVerificaLaboratorio() {
		return iVerificaLaboratorio;
	}

	public void setVerificaLaboratorio(char v) {
		this.iVerificaLaboratorio=v;
		setDirty();
	}
	//31937 fine

	//32117 ini
	public String getUNIDPth() {
		return this.iUNIDPth;
	}
	public void setUNIDPth(String s) {
		this.iUNIDPth=s;
		setDirty();
	}

	//public boolean getAllegatoEsterno() { //32810
	public char getAllegatoEsterno() {//32810
		return this.iAllegatoEsterno;
	}
	// public void setAllegatoEsterno(boolean b) {//32810
	public void setAllegatoEsterno(char b) {
		this.iAllegatoEsterno=b;
		setDirty();
	}

	public String getRisolve() {
		return this.iRisolve;
	}
	public void setRisolve(String r) {
		this.iRisolve=r;
		setDirty();
	}
	//32117 fine

	//32761 ini

	public Date getDataDefinitiva() {
		return this.iDataDefinitiva;
	}
	public void setDataDefinitiva(Date d) {
		this.iDataDefinitiva=d;
		setDirty();
	}

	//32761 fine

	//33217 ini
	public char getInstallazioneFix() {
		return iInstallazioneFix;
	}

	public void setInstallazioneFix(char iInstallazioneFix) {
		this.iInstallazioneFix = iInstallazioneFix;
	}

	public char getInstallazioneFixSchedul() {
		return iInstallazioneFixSchedul;
	}

	public void setInstallazioneFixSchedul(char iInstallazioneFixSchedul) {
		this.iInstallazioneFixSchedul = iInstallazioneFixSchedul;
	}

	public boolean isInstallataHD() {
		return iInstallataHD;
	}

	public void setInstallataHD(boolean iInstallataHD) {
		this.iInstallataHD = iInstallataHD;
	}

	public void setFixNonPubblicabile(boolean iFixNonPubblicabile) {
		this.iFixNonPubblicabile = iFixNonPubblicabile;
	}

	public boolean getFixNonPubblicabile() {
		return iFixNonPubblicabile;
	}

	//33217 fine

	//33570 ini

	public Timestamp getTimestampReinstallataHD() {
		return iTimestampReinstallataHD;
	}

	public void setTimestampReinstallataHD(Timestamp iTimestampReinstallataHD) {
		this.iTimestampReinstallataHD = iTimestampReinstallataHD;
	}

	public Timestamp getTimestampUltimoAggZipFix() {
		return iTimestampUltimoAggZipFix;
	}

	public void setTimestampUltimoAggZipFix(Timestamp iTimestampUltimoAggZipFix) {
		this.iTimestampUltimoAggZipFix = iTimestampUltimoAggZipFix;
	}
	//33570 fine

	//33991 ini
	public boolean getNonPubblicabileMP() {
		return this.iNonPubblicabileMP;
	}

	public void setNonPubblicabileMP(boolean nonPubblicabileMP) {
		this.iNonPubblicabileMP = nonPubblicabileMP;
	}
	//33991 fine

}

