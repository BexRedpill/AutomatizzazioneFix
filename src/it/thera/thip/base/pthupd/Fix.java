package it.thera.thip.base.pthupd;

import java.net.URLEncoder;
import java.sql.*;
import java.util.*;

import com.thera.thermfw.base.*;
import com.thera.thermfw.common.*;
import com.thera.thermfw.persist.*;
import com.thera.thermfw.security.Security;
/*
 * @(#)Fix.java
 */
import com.thera.thermfw.web.LicenceManager;

import it.thera.thip.base.pthupd.Fix;
import it.thera.thip.base.pthupd.FixChange;
import it.thera.thip.base.pthupd.FixDocumentation;
import it.thera.thip.base.pthupd.FixPO;
import it.thera.thip.base.pthupd.FixPrerequisito;
import it.thera.thip.base.pthupd.FixTM;
import it.thera.thip.crm.news.MkStoricoChiamata;
import it.thera.thip.crm.news.News;
import it.thera.thip.cs.ThipContextInfo;
import it.thera.thip.ws.WrapperJSON;

/**
 * Fix
 */
/*
 * Revisions: 
 * Number Date 		 Owner  Description 
 * 16217  12/04/2012 TF 	Prima versione
 * 31497  30/06/2020 FB	  	Aggiunti nuovi attributi
 * 31937  28/09/2020 FB	    Ricalcolo PercorsoFunzione alla save. Nuovi attributi
 * 32117  26/10/2020 FB		Ricalcolo M e VRM alla save
 * 32810  22/01/2021 FB		Chiamata a WS per salvataggio log download. Tipi allegato fix
 * 34271  14/09/2021 FB     Corretta rinumerazione VRM 
 */

public class Fix extends FixPO {

  // --Module
  public static final char PANTH_GESTIONALE = 'G';
  public static final char PANTH_AC = 'A';
  public static final char PANTH_PERS = 'P';

  // --Level
  public static final char ALTA = 'A';
  public static final char MEDIA = 'B';
  public static final char BASSA = 'C';

  // --Status
  public static final char TEST_RS = 'T';
  public static final char PRONTA_PER_COLLAUDO_FINALE = 'B';
  public static final char COLLAUDO_FINALE = 'C';
  public static final char PRONTA_PER_RILASCIO = 'Q';
  public static final char RILASCIO = 'R';
  public static final char TEST_DELIVERY = 'D';
  public static final char TEST_DELIVERY_RESPINTA = 'N';
  public static final char TEST_DELIVERY_OK = 'O';
  public static final char PUBBLICATA = 'P';
  public static final char RITIRATA = 'Z';

  
  //31497 ini 
  // --TipoImplemento
  public static final char FASTUPDATE= 'F';
  public static final char MIGLIORIA = 'M';
  public static final char NUOVO_IMPLEMENTO = 'I';
  public static final char FATT_ELETTRONICA = 'A';
  public static final char ALTRO = 'E';
  
  // --StatoEmissione
  public static final char BOZZA= '0';
  public static final char DEFINITIVA = '2';
  public static final char NON_PUBBLICABILE = '9';
  public static final char NON_DEFINITO = '-';
  
  // --Tipo pacchetto
  public static final char FIX_SINGOLA='N';
  public static final char FIX_INTESTATARIA = 'P';
  public static final char FIX_MEMBRO = 'M';
  //31497 fine
  
  //35629 ini
  //--Tipo installazione fix
  public static final char TI_NON_INSTALLATA = '1';
  public static final char TI_INSTALLA_DA_REMOTO = '2';
  public static final char TI_INSTALLA_IN_AUTOMATICO = '3';
  public static final char TI_INSTALLATA_DA_REMOTO = '4';
  public static final char TI_INSTALLATA_IN_AUTOMATICO = '5';
  public static final char TI_INSTALLATA = '6';
  //35629 fine
  
  //31937 ini
  public static final char DA_VERIFICARE = 'D';
  public static final char NON_VERIFICARE = 'N';
  public static final char VERIFICATA = 'V';
  //31937 fine
  
	public static final char NON_INSTALLATA = '-';
  
  //32810 ini
  public static final char ALL_INTERNO = 'I';
  public static final char ALL_ESTERNO = 'E';
  public static final char ALL_ONEDRIVE = 'F';
  //32810 fine
  
  public static CachedStatement cModuleStmt = new CachedStatement("SELECT " + FixTM.MODULE + " FROM " + FixTM.TABLE_NAME + " WHERE " + FixTM.FIX + " = ?");
 
  //31497 ini
  public Fix() {
	  setTipoImplemento(ALTRO);
  }
  //31497 fine
  
  //31937 ini
  public int save() throws SQLException{
	  if(getTipoImplemento()!=FASTUPDATE) {
		  StringBuilder sb = new StringBuilder();
		  if(getDomain()!=null && !getDomain().trim().equals("")) 
			  sb.append(getDomain());
		  if(getArea()!=null && !getArea().trim().equals(""))
			  sb.append(" / "+getArea());
		  if(getFunzione()!=null && !getFunzione().trim().equals(""))
			  sb.append(" / "+getFunzione());
		  if(getSubFunzione()!=null && !getSubFunzione().trim().equals(""))
			  sb.append(" / "+getSubFunzione());
		  setPercorsoFunzione(sb.toString());
	  }
	  //32117
	  if(getTipoImplemento()!=FASTUPDATE&&getFixStatusEmissione()==DEFINITIVA&&getModification()==0) {
		  String maxVRM = FixUtils.getMaxVRM();
		  if(maxVRM!=null && !maxVRM.trim().equals("")) {
			  String[] el = maxVRM.split("\\.");
			  short v = Short.parseShort(el[0]);
			  short r = Short.parseShort(el[1]);
			  short m = Short.parseShort(el[2]);
			  m = (short)(m+1);
			  setVersion(v);
			  setRelease(r);
			  setModification(m);
			  String mod = Short.toString(m);
			  //if(m<9)  //34271
			  if(m<=9) //34271
				  mod = "0"+mod;
			  setVRM(v+"."+r+"."+mod);
		  }
	  }
	  return super.save();
  }

  //31937 fine
  
  //32810 ini
  public String getUrlPerDownload(String so) {
	  String url = "";
	  if(getAllegatoEsterno()==ALL_ESTERNO)
		  url = "http://domino.thera.it/Allegati/";
	  else if(getAllegatoEsterno()==ALL_INTERNO)
		  url = "http://domino.thera.it/ptfweb/ptfpanthera.nsf/0/"+getUNID()+"/$FILE/";
	  
	  if(so.equalsIgnoreCase(FixUtils.SO_AS400))
		  url += getAllegatoP6();
	  else if(so.equalsIgnoreCase(FixUtils.SO_LINUX))
		  url += getAllegatoLX();
	  else if(so.equalsIgnoreCase(FixUtils.SO_WINDOWS))
		  url += getAllegatoWN();
	  return url;
  }
  //32810 fine
  
  public ErrorMessage checkDelete() {
    if (getStatus() == PUBBLICATA)
      return new ErrorMessage("THIP40T206");
    return null;
  }

  public List getChangesWithObject(String object) {
    List lstChangesWithObject = new ArrayList();
    if (object != null) {
      Iterator changeIter = getChange().iterator();
      while (changeIter.hasNext()) {
        FixChange fixChange = (FixChange) changeIter.next();
        if (fixChange.getChangeObj().equals(object)) {
          lstChangesWithObject.add(fixChange);
        }
      }
    }
    return lstChangesWithObject;
  }

  public List getDocsWithDocTitle(String docTitle) {
    List lstDocsWithDocTitle = new ArrayList();
    if (docTitle != null) {
      Iterator docsIter = getDocumentation().iterator();
      while (docsIter.hasNext()) {
        FixDocumentation fixDoc = (FixDocumentation) docsIter.next();
        if (fixDoc.getDocTitle().equals(docTitle)) {
          lstDocsWithDocTitle.add(fixDoc);
        }
      }
    }
    return lstDocsWithDocTitle;
  }

  public List getDocsWithDocPath(String docPath) {
    List lstDocsWithDocPath = new ArrayList();
    if (docPath != null) {
      Iterator docsIter = getDocumentation().iterator();
      while (docsIter.hasNext()) {
        FixDocumentation fixDoc = (FixDocumentation) docsIter.next();
        if (fixDoc.getDocPath().equals(docPath)) {
          lstDocsWithDocPath.add(fixDoc);
        }
      }
    }
    return lstDocsWithDocPath;
  }

  public static List getAvailableFixes(String module, int version, int release, int modification, boolean deliveryInfracom) {
    String where = "(" + getModuleCondition(module) + ") AND (" + getStatusCondition(deliveryInfracom) + ") AND (" + getVRMCondition(version, release, modification) + ")";
    try {
      return Fix.retrieveList(where, FixTM.FIX, false);
    }
    catch (Exception e) {
      e.printStackTrace(Trace.excStream);
    }
    return new ArrayList();
  }

  protected static String getVRMCondition(int version, int release, int modification) {
    int[] nextVRM = getNextVRM(version, release, modification);
    return FixTM.VERSION + " = " + nextVRM[0] + " AND " + FixTM.RELEASE + " = " + nextVRM[1] + " AND " + FixTM.MODIFICATION + " = " + nextVRM[2];
  }
  
  protected static int[] getNextVRM(int version, int release, int modification) {
    int[] nextVRM = new int[] {version, release, modification};
    String upperVersion = "(" + FixTM.VERSION + " > " + version + ")";
    String upperRelease = "((" + FixTM.VERSION + " = " + version + ") AND (" + FixTM.RELEASE + " > " + release + "))";
    String upperModification = "((" + FixTM.VERSION + " = " + version + ") AND (" + FixTM.RELEASE + " = " + release + ") AND (" + FixTM.MODIFICATION + " > " + modification + "))";
    String where = upperVersion + " OR " + upperRelease + " OR " + upperModification;
    String colmuns = FixTM.VERSION + ", " + FixTM.RELEASE + ", " + FixTM.MODIFICATION;
    String sql = "SELECT " + colmuns + " FROM " + FixTM.TABLE_NAME + " WHERE (" + where + ") ORDER BY " + colmuns;
    CachedStatement stmt = new CachedStatement(sql);
    try {
      ResultSet rs = stmt.executeQuery();
      if(rs.next()) {
        nextVRM[0] = rs.getInt(FixTM.VERSION);
        nextVRM[1] = rs.getInt(FixTM.RELEASE);
        nextVRM[2] = rs.getInt(FixTM.MODIFICATION);
      }
      rs.close();
      stmt.free();
    }
    catch (SQLException e) {
      e.printStackTrace(Trace.excStream);
    }
    return nextVRM;
  }  

  protected static String getStatusCondition(boolean deliveryInfracom) {
    if(deliveryInfracom) {
      return FixTM.STATUS + " IN ('" + RILASCIO + "', '" + TEST_DELIVERY + "', '" + TEST_DELIVERY_RESPINTA + "', '" + TEST_DELIVERY_OK + "', '" + PUBBLICATA + "', '" + RITIRATA + "')";
    }
    else {
      return FixTM.STATUS + " = '" + PUBBLICATA + "'";
    }
  }

  protected static String getModuleCondition(String module) {
    return FixTM.MODULE + " = '" + module + "'";
  }

  //31497 ini
  /**
   * Metodo di utilità che informa se un carattere rappresenta uno stato ammissibile per il campo FixStatusEmissione
   * @param c
   * @return
   */
  public static boolean isStatusEmissioneAdmitted(char c) {
	  return c==BOZZA||c==DEFINITIVA||c==NON_PUBBLICABILE||c==NON_DEFINITO;
  }
  //31497 fine
  public static String getModuleForFix(int fix) {
    try {
      PreparedStatement ps = cModuleStmt.getStatement();
      ps.setInt(1, fix);
      ResultSet rs = ps.executeQuery();
      if(rs.next()) {
        return rs.getString(FixTM.MODULE);
      }
    }
    catch (SQLException e) {
      e.printStackTrace(Trace.excStream);
    }
    return String.valueOf(PANTH_GESTIONALE);
  }

  public static void completeWithPrerequisites(List fixNumbers) {
    List prerequisitesList = new ArrayList();
    for (int i = 0; i < fixNumbers.size(); i++) {
      List fixPrerequisites = FixPrerequisito.getPrerequisites(((Integer)fixNumbers.get(i)).intValue());
      for (int j = 0; j < fixPrerequisites.size(); j++) {
        FixPrerequisito prerequisito = (FixPrerequisito)fixPrerequisites.get(j);
        Integer fixNumber = prerequisito.getFixPrerequisito().getFix();
        if(!(prerequisitesList.contains(fixNumber) || fixNumbers.contains(fixNumber))) {
          prerequisitesList.add(fixNumber);
        }
      }
    }
    if(!prerequisitesList.isEmpty()) {
      fixNumbers.addAll(prerequisitesList);
      completeWithPrerequisites(fixNumbers);
    }
  }
  
  //32810 ini
  public void notificaDownload() {
	  DownloadLoggerNotifier.getInstance().notificaDownloadFix(this);
  }
  
  //32810 fine
  
//public static int[] getInstalledFixNumbers(int version, int release, int modification) {
//String where = getVRMCondition(version, release, modification);
//try {
//  List installedFixes = Fix.retrieveList(where, FixTM.FIX, false);
//  if (!installedFixes.isEmpty()) {
//    int[] installedFixNumbers = new int[installedFixes.size()];
//    for (int i = 0; i < installedFixNumbers.length; i++) {
//      Fix fix = (Fix) installedFixes.get(i);
//      installedFixNumbers[i] = fix.getFix().intValue();
//    }
//    return installedFixNumbers;
//  }
//}
//catch (Exception e) {
//  e.printStackTrace(Trace.excStream);
//}
//return new int[0];
//}

//protected static String getVRMCondition(int version, int release, int modification) {
//String upperVersion = "(" + FixTM.VERSION + " > " + version + ")";
//String upperRelease = "((" + FixTM.VERSION + " = " + version + ") AND (" + FixTM.RELEASE + " > " + release + "))";
//String upperModification = "((" + FixTM.VERSION + " = " + version + ") AND (" + FixTM.RELEASE + " = " + release + ") AND (" + FixTM.MODIFICATION + " > " + modification + "))";
//return upperVersion + " OR " + upperRelease + " OR " + upperModification;
//}

  
//  public static void main(String[] args) {
//    try {
//      ConnectionManager.openMainConnection(new ConnectionDescriptor("PANTHSTD", "db2admin", "db2admin", new DB2NetDatabase("SERVER4", "50000")));
//      int[] nextVRM = getNextVRM(4, 0, 5);
//      System.out.println(nextVRM[0] +", " +  nextVRM[1] + ", " + nextVRM[2]);
//      ConnectionManager.closeMainConnection();
//    }
//    catch (SQLException e) {
//      e.printStackTrace();
//    }
//  }
}
