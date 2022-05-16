package it.thera.thip.base.pthupd.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.collections4.map.HashedMap;

import com.thera.thermfw.ad.ClassADCollection;
import com.thera.thermfw.base.ResourceLoader;
import com.thera.thermfw.base.Trace;
import com.thera.thermfw.common.ErrorMessage;
import com.thera.thermfw.log.LogTask;
import com.thera.thermfw.persist.ConnectionManager;
import com.thera.thermfw.persist.ErrorCodes;
import com.thera.thermfw.persist.PersistentObject;
import com.thera.thermfw.rs.BaseResource;
import com.thera.thermfw.security.Security;
import com.thera.thermfw.web.*;
import com.thera.thermfw.web.servlet.*;

import it.thera.thip.base.generale.ParametroPsn;
import it.thera.thip.base.pthupd.DominoCookieGetter;
import it.thera.thip.base.pthupd.Fix;
import it.thera.thip.base.pthupd.FixUtils;
import it.thera.thip.base.pthupd.PsnCatalogoFix;
import it.thera.thip.base.pthupd.VerificaFixPubblicata;
import it.thera.thip.base.pthupd.api.CatalogoFixApiResource;

/*
 * @(#)FixGridActionAdapter.java
 */

/**
 * FixGridActionAdapter
 */
/*
 * Revisions:
 * Number     Date          Owner      Description
 * 16217      17/04/2012    TF         Prima versione
 * 31497	  30/06/2020	FB		   Riabilitate le azioni new e copy, forzo l'apertura in sola lettura per le fix membro di pacchetto
 * 31937	  28/09/2020	FB		   Modifica interazione doppio click. Aggiunta pulsanti per apertura scheda fix e download allegati scheda. Calcolo e download prerequisiti
 * 32117	  26/10/2020	FB		   Modificata sensibility a btn download prerequisiti. Migliorie
 * 33193	  31/03/2021	FG		   Aggiunto pulsante per mostrare la situazione fix 
 * 34388      05/10/2021    FG         Rimossi bottoni New, Open, Copy e Delete e redirect Edit a ShowObject. Aggiunta nuovo bottone downlaodFixSelectPlatform + check VRM > 4.7.0 DownloadPrerequisiti
 * 33209      03/11/2021    FG         Gestione Login su Domino
 * 35629							   Installazione automatica delle fix	
 */

public class FixGridActionAdapter extends GridActionAdapter {

	//35629 ini
	private static final String CONFIGURZIONE_CATALOGO_MANCANTE = "ConfigurzioneCatalogoMancante";
	private static final String ERRORE_BAS0000078 = "BAS0000078";
	private static final String NON_ESISTE = " non esiste";
	private static final String LA_FIX = "La fix ";
	public static final String PSN_CATALOGO_FIX_PROPERTIES = "it.thera.thip.base.pthupd.resources.PsnCatalogoFix";
	//35629 fine
	
	public static final String VISUALIZZA_SCHEDA = "VisualizzaScheda"; //31937
	public static final String DOWNLOAD_FIX = "DownloadFix"; //31937
	public static final String RES_FILE = "it.thera.thip.base.pthupd.web.resources.FixGridActionAdapter"; //31937
	public static final String DOWNLOAD_PREREQUISITI = "DownloadPrereq"; //31937
	private static final String mostraSchedaDoppioClick = ParametroPsn.getValoreParametroPsn("std.pthupd.fixes", "ShowSchedaOnDblClick");
	public static final String SITUAZIONE_FIX="SituazioneFix";//33193
	public static final String DOWNLOAD_FIX_SELECT_PLATFORM = "DownloadFixSelectPlatform"; //34388
	public static final String DOWNLOAD_FIX_SELECTED_PLTF = "DownloadFixSelectedPltf"; //34388
	public static final String DO_LOGIN_DOMINO = "DoLoginDomino"; //33209
	public static final String CALL_SERVLET_DOWNLOAD_FIX = "DownloadFixServlet";//33209
	public static final String SET_FIX_DA_INSTALLARE = "SetFixDaInstallare"; //35629
	public static final String ANNULLA_INSTALLAZIONE_AUTOMATICA = "AnnullaInstallazioneAutomatica"; //35629


	public void modifyMenuBar(WebMenuBar menuBar) {
		// menuBar.enableMenuItem("ListMenu.New", false); //31497
		menuBar.enableMenuItem("ListMenu.NewTemplate", false);
		menuBar.enableMenuItem("SelectedMenu.Copy", false);
	}

	public void modifyToolBar(WebToolBar toolBar) {
		// toolBar.enableButton("New", false); //31497
		//  toolBar.enableButton("Copy", false); //31497
		//31937 ini
		WebToolBarButton btnVisualizza = new WebToolBarButton("VisualizzaScheda", "action_submit", "new", "no", RES_FILE, "VisualizzaScheda", null, VISUALIZZA_SCHEDA, "single", false);
		//WebToolBarButton btnDownloadFix = new WebToolBarButton("DownloadFix", "action_submit", "infoArea", "no", RES_FILE, "DownloadFix", null, DOWNLOAD_FIX, "multiple", false);
		WebToolBarButton btnDownloadFix = new WebToolBarButton("DownloadFix", "action_submit", "new", "no", RES_FILE, "DownloadFix", null, DOWNLOAD_FIX, "multipleSelSingleWindow", false); //33209

		//WebToolBarButton btnDownloadPrereq = new WebToolBarButton("DownloadPrereq", "action_submit", "new", "no", RES_FILE, "DownloadPrereq", null, DOWNLOAD_PREREQUISITI, "no", false); //32117
		WebToolBarButton btnDownloadPrereq = new WebToolBarButton("DownloadPrereq", "action_submit", "new", "no", RES_FILE, "DownloadPrereq", null, DOWNLOAD_PREREQUISITI, "single", false); //32117

		toolBar.addButton("NavDocDgt", btnVisualizza);
		toolBar.addButton("VisualizzaScheda",btnDownloadFix);
		toolBar.addButton("DownloadFix", btnDownloadPrereq);
		//31937 fine

		WebToolBarButton btnSituazioneFix = new WebToolBarButton("SituazioneFix", "action_submit", "new", "no", RES_FILE, "SituazioneFix", null, SITUAZIONE_FIX, "single", false);//33193
		toolBar.addButton("DownloadPrereq", btnSituazioneFix);//33193
		
		//35629 ini
		try 
		{
			if (Security.validate("Fix", "INSTALLA_AUTOM"))
			{
				WebToolBarButton btnSetFixDaInstallare = new WebToolBarButton("SetFixDaInstallare", "action_submit", "new", "no", RES_FILE, "SetFixDaInstallare", null, SET_FIX_DA_INSTALLARE, "multipleSelSingleWindow", false);
				toolBar.addButton("DownloadPrereq", btnSetFixDaInstallare);

				WebToolBarButton btnAnnullaInstallazioneAutomatica = new WebToolBarButton("AnnullaInstallazioneAutomatica", "action_submit", "infoArea", "no", RES_FILE, "AnnullaInstallazioneAutomatica", null, ANNULLA_INSTALLAZIONE_AUTOMATICA, "multipleSelSingleWindow", false);
				toolBar.addButton("SetFixDaInstallare", btnAnnullaInstallazioneAutomatica);
			}
		} 
		catch (SQLException e) 
		{
			e.printStackTrace();
		}
		//35629 fine

		//34388 ini
		if(soloReadOnly()) {
			toolBar.removeButton("New");
			toolBar.removeButton("Open");
			toolBar.removeButton("Copy");
			toolBar.removeButton("Delete");
			toolBar.removeButton("SituazioneFix");
		}

		WebToolBarButton btnDownloadSelectPlatform = new WebToolBarButton("DownloadFixSelectPlatform", "action_submit", "new", "no", RES_FILE, "DownloadFixSelectPlatform", null, DOWNLOAD_FIX_SELECT_PLATFORM, "multipleSelSingleWindow", false);
		toolBar.addButton("DownloadFix", btnDownloadSelectPlatform);
		//34388 fine

	}

	//31497 ini
	protected void editObject(ClassADCollection cadc, ServletEnvironment se) throws ServletException, IOException{
		//34388 ini
		if(soloReadOnly()) {
			showObject(cadc, se);
		}else {
			//34388 fine
			String [] keys = se.getRequest().getParameterValues(OBJECT_KEY);
			for(int i=0; i<keys.length; i++) {
				String key = keys[i];
				try {
					Fix f = (Fix)Fix.elementWithKey(key, PersistentObject.NO_LOCK);
					if(f!=null) {
						if(f.getPacchetto()==Fix.FIX_MEMBRO)
							showObject(cadc, se);
						else
							super.editObject(cadc, se);
					}
				}catch(Throwable t) {
					t.printStackTrace(Trace.excStream);
				}
			}
		}//34388
	}
	//31497 fine

	//31937 ini

	public String getDoubleClickAction() {
		if(mostraSchedaDoppioClick!=null && mostraSchedaDoppioClick.equals("Y"))
			return VISUALIZZA_SCHEDA;
		else
			return super.getDoubleClickAction();
	}

	public void otherActions(ClassADCollection cadc, ServletEnvironment se) throws IOException, ServletException{
		String action = getStringParameter(se.getRequest(), ACTION);
		if(action.equalsIgnoreCase(VISUALIZZA_SCHEDA)) {
			String[] selected = se.getRequest().getParameterValues(OBJECT_KEY);
			if(selected!=null && selected.length>0) {
				String fixKey = selected[0];
				se.getSession().setAttribute("FixKey", fixKey);
				//se.sendRequest(getServletContext(), "it/thera/thip/base/pthupd/openSchedaPanthera.jsp", false);
				se.sendRequest(getServletContext(), "/servlet/it.thera.thip.base.pthupd.servlet.FixDispatcherServlet?actionType=viewScheda", false); //33209
			}
		}
		if(action.equalsIgnoreCase(DOWNLOAD_FIX)) {
			downloadFix(cadc, se);
		}
		if(action.equalsIgnoreCase(DOWNLOAD_PREREQUISITI)) {
			downloadPrerequisiti(cadc, se);
		}

		//33193
		if(action.equalsIgnoreCase(SITUAZIONE_FIX)) {
			situazioneFix(cadc, se);
		}

		//34388 ini
		if(action.equalsIgnoreCase(DOWNLOAD_FIX_SELECT_PLATFORM)) {
			//Apre pannellino di scelta della piattaforma
			selectPlatform(cadc, se);
		}

		if(action.equalsIgnoreCase(DOWNLOAD_FIX_SELECTED_PLTF)) {
			//scarica le fix con la piattaforma selezionata dal pannellino di scelta
			downloadFixSelectedPlatform(cadc, se);
		}
		//34388 fine

		//33209 ini
		if(action.equalsIgnoreCase(DO_LOGIN_DOMINO)){
			doLoginDomino(cadc, se);
		}

		if(action.equalsIgnoreCase(CALL_SERVLET_DOWNLOAD_FIX)) {
			callServletDownloadFix(cadc, se);
		}
		//33209 fine
		
		//35629 ini
		if(action.equalsIgnoreCase(SET_FIX_DA_INSTALLARE)) {
			try {
				callSetFixDaInstallare(cadc, se);
			} catch (ServletException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		if(action.equalsIgnoreCase(ANNULLA_INSTALLAZIONE_AUTOMATICA)) {
			try {
				callAnnullaInstallazioneAutomatica(cadc, se);
			} catch (ServletException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		//35629 fine

		else
			super.otherActions(cadc, se);
	}

	//35629
	private void callAnnullaInstallazioneAutomatica(ClassADCollection cadc, ServletEnvironment se) throws ServletException, IOException, SQLException {
		String[] keys = se.getRequest().getParameterValues(OBJECT_KEY);
		List<String> selectedKeys = new ArrayList<String>();
		for(int i=0; i < keys.length; i++)
			selectedKeys.add(keys[i]);
		
		for(String fixNum : selectedKeys){
			Fix fix = Fix.elementWithKey(fixNum, PersistentObject.OPTIMISTIC_LOCK);

			if(fix != null) {
				if(fix.getTipoInstallazione() == Fix.TI_INSTALLA_DA_REMOTO || fix.getTipoInstallazione() == Fix.TI_INSTALLA_IN_AUTOMATICO) {
					fix.setTipoInstallazione(Fix.TI_NON_INSTALLATA);
					fix.setForzaFixAnticip(false);
				}

				int rc = -1;
				try {
					rc = fix.save();
					if(rc < 0) {
						se.addErrorMessage(CatalogoFixApiResource.daRcAErrorMessage(rc, null));
					}
				}catch (SQLException e){
					e.printStackTrace();
					se.addErrorMessage(CatalogoFixApiResource.daRcAErrorMessage(rc, e));
				}
			}			
		}
		
		if(!se.isErrorListEmpity()) {
			ConnectionManager.rollback();
			//se.sendRequest(getServletContext(), "com/thera/thermfw/common/ErrorListHandler.jsp", false);
		}else {
			ConnectionManager.commit();
			String stringaFix = "";
			for(int i=0; i < keys.length; i++)
			{
				if (i != 0)
					stringaFix += ";";
				stringaFix += keys[i];
			}
			LogTask.startLogTaskForced("Fix", "ANNUL_INST_AUT", false, stringaFix);
			se.addErrorMessage(new ErrorMessage("BAS0000079"));
		}
		se.sendRequest(getServletContext(), "com/thera/thermfw/common/InfoAreaHandler.jsp", false);
	}

	//35629
	private void callSetFixDaInstallare(ClassADCollection cadc, ServletEnvironment se) throws ServletException, IOException, SQLException {
		PsnCatalogoFix catalogo = PsnCatalogoFix.elementWithKey("0", PersistentObject.OPTIMISTIC_LOCK);
		if(catalogo != null) {
			String[] keys = se.getRequest().getParameterValues(OBJECT_KEY);
			List selectedKeys = new ArrayList();
			for(int i=0; i<keys.length; i++)
				selectedKeys.add(keys[i]);

			boolean fixPubblicata = false;
			boolean errorFound = false;
			try {
				fixPubblicata = VerificaFixPubblicata.getInstance().areFixPubblicate(selectedKeys);
			}catch(Exception e) {
				errorFound = true;
				PrintWriter out = se.getResponse().getWriter();
				out.println("<script language='JavaScript1.2'>");
				out.println("  if (typeof parent.enableGridActions != 'undefined')");
				out.println("    parent.enableGridActions();");
				out.println("    parent.alert('Errore durante la chiamata al WS Panthera per la verifica di fix pubblicata\\n" + e.getMessage() + "');");
				out.println("    parent.window.close();"); 
				out.println("</script>");
			} 

			if(!fixPubblicata && !errorFound) {
				PrintWriter out = se.getResponse().getWriter();
				out.println("<script language='JavaScript1.2'>");
				out.println("  if (typeof parent.enableGridActions != 'undefined')");
				out.println("    parent.enableGridActions();");
				if(selectedKeys.size() > 1) {
					out.println("  parent.alert('" + ResourceLoader.getString(RES_FILE, "fixNonPubblicate")+  "')");
				}else {
					out.println(" parent.alert('" + ResourceLoader.getString(RES_FILE, "fixNonPubblicata") + "')");
				}
				out.println("    parent.window.close();"); 
				out.println("</script>");

			}else if(!errorFound) {  

				se.getSession().setAttribute("KeysToDownload", selectedKeys);

				se.sendRequest(getServletContext(), "/servlet/it.thera.thip.base.pthupd.servlet.FixDispatcherServlet?actionType=setFixDaInstallare", false);

			}
		}else {
			PrintWriter out = se.getResponse().getWriter();
			out.println("<script language='JavaScript1.2'>");
			out.println("  if (typeof parent.enableGridActions != 'undefined')");
			out.println("    parent.enableGridActions();");
			out.println("    parent.alert('" + ResourceLoader.getString(PSN_CATALOGO_FIX_PROPERTIES, CONFIGURZIONE_CATALOGO_MANCANTE) + "');");
			out.println("    parent.window.close();"); 
			out.println("</script>");
		}
	}

	private void downloadFix(ClassADCollection cadc, ServletEnvironment se) throws IOException, ServletException{
		String[] keys = se.getRequest().getParameterValues(OBJECT_KEY);
		List selectedKeys = new ArrayList();
		for(int i=0; i<keys.length; i++)
			selectedKeys.add(keys[i]);
		//34388 ini
		boolean fixPubblicata = false;
		boolean errorFound = false;
		try {
			fixPubblicata = VerificaFixPubblicata.getInstance().areFixPubblicate(selectedKeys);
		}catch(Exception e) {
			errorFound = true;
			PrintWriter out = se.getResponse().getWriter();
			out.println("<script language='JavaScript1.2'>");
			out.println("  if (typeof parent.enableGridActions != 'undefined')");
			out.println("    parent.enableGridActions();");
			out.println("    parent.alert('Errore durante la chiamata al WS Panthera per la verifica di fix pubblicata\\n" + e.getMessage() + "');");
			out.println("    parent.window.close();"); //33209
			out.println("</script>");
		} 

		if(!fixPubblicata && !errorFound) {
			PrintWriter out = se.getResponse().getWriter();
			out.println("<script language='JavaScript1.2'>");
			out.println("  if (typeof parent.enableGridActions != 'undefined')");
			out.println("    parent.enableGridActions();");
			if(selectedKeys.size() > 1) {
				out.println("  parent.alert('" + ResourceLoader.getString(RES_FILE, "fixNonPubblicate")+  "')");
			}else {
				out.println(" parent.alert('" + ResourceLoader.getString(RES_FILE, "fixNonPubblicata") + "')");
			}
			out.println("    parent.window.close();"); //33209
			out.println("</script>");

		}else if(!errorFound) {  
			//34388 fine
			se.getSession().setAttribute("KeysToDownload", selectedKeys);
			se.getSession().setAttribute("SO", FixUtils.getCurrentOperatingSystem());

			//33209 ini
			se.sendRequest(getServletContext(), "/servlet/it.thera.thip.base.pthupd.servlet.FixDispatcherServlet?actionType=downloadFixes", false);
			//33209 fine

		}//34388
	}


	private void downloadPrerequisiti(ClassADCollection cadc, ServletEnvironment se) throws IOException, ServletException{
		String key = se.getRequest().getParameter(OBJECT_KEY);
		//34388 ini
		Fix f = null;
		try {
			f = Fix.elementWithKey(key, PersistentObject.NO_LOCK);
		}catch(SQLException e) {
			e.printStackTrace();
		}
		//Se la fix appartiene ad un cumulativo inferiori alla 4.7.0 il calcolo e download dei prereq non � disponibile
		if(f != null && (f.getVersion() < 4 || (f.getVersion() == 4 && f.getRelease() < 7))) {
			PrintWriter out = se.getResponse().getWriter();
			out.println("<script language='JavaScript1.2'>");
			out.println("  if (typeof parent.enableGridActions != 'undefined')");
			out.println("    parent.enableGridActions();");
			out.println("    parent.alert('" + ResourceLoader.getString(RES_FILE, "calcoloPrereqNonDisponibile")+ "');");
			out.println("    parent.window.close();");
			out.println("</script>");
		}else {
			//34388 fine
			//se.getSession().setAttribute("FixNumber",key);
			se.getSession().setAttribute("numFixCalcPrereq", key); //33209
			se.sendRequest(getServletContext(), "it/thera/thip/base/pthupd/web/SpecificaDownloadFixPrereq.jsp", false);
		}//34388
	}

	//31937 fine

	//33193
	private void situazioneFix(ClassADCollection cadc, ServletEnvironment se) throws IOException, ServletException{
		String key = se.getRequest().getParameter(OBJECT_KEY);
		se.getRequest().setAttribute("NUMERO_FIX",key);
		se.sendRequest(getServletContext(), "it/thera/thip/base/pthupd/web/SituazioneFix.jsp", false);
	}

	//34388 ini

	private void selectPlatform(ClassADCollection cadc, ServletEnvironment se) throws IOException, ServletException{
		String[] keys = se.getRequest().getParameterValues(OBJECT_KEY);
		List selectedKeys = new ArrayList();
		for(int i=0; i<keys.length; i++)
			selectedKeys.add(keys[i]);

		//Verifico che tutte le fix selezionate siano pubblicat
		boolean fixPubblicate = false;
		try {
			fixPubblicate = VerificaFixPubblicata.getInstance().areFixPubblicate(selectedKeys);
		}catch(Exception e) {
			PrintWriter out = se.getResponse().getWriter();
			out.println("<script language='JavaScript1.2'>");
			out.println("  if (typeof parent.enableGridActions != 'undefined')");
			out.println("    parent.enableGridActions();");
			out.println("    parent.alert('Errore durante la chiamata al WS Panthera per la verifica di fix pubblicata\\n" + e.getMessage() + "');");
			out.println("    parent.window.close();");
			out.println("</script>");
		}
		if(!fixPubblicate) {
			PrintWriter out = se.getResponse().getWriter();
			out.println("<script language='JavaScript1.2'>");
			out.println("  if (typeof parent.enableGridActions != 'undefined')");
			out.println("    parent.enableGridActions();");
			if(selectedKeys.size() > 1) {
				out.println("  parent.alert('" + ResourceLoader.getString(RES_FILE, "fixNonPubblicate") + "')");
			}else {
				out.println(" parent.alert('" + ResourceLoader.getString(RES_FILE, "fixNonPubblicata") + "')");
			}			
			out.println("  parent.window.close()");
			out.println("</script>");
		}else {
			se.getSession().setAttribute("KeysToDownload", selectedKeys);
			//All'apertura del pannello di scelta piattaforma metto come selezionato di default il SO corrente
			se.getSession().setAttribute("SO", FixUtils.getCurrentOperatingSystem());
			se.sendRequest(getServletContext(), "it/thera/thip/base/pthupd/DownloadFixSelectPlatform.jsp", false);
		}

	}

	private void downloadFixSelectedPlatform(ClassADCollection cadc, ServletEnvironment se) throws IOException, ServletException{
		String selectedSO = (String) se.getRequest().getParameter("selSO");
		se.getSession().setAttribute("SO", selectedSO);
		//se.sendRequest(getServletContext(), "it/thera/thip/base/pthupd/DownloadFixDispatcher.jsp", false); <33209
		se.sendRequest(getServletContext(), "/servlet/it.thera.thip.base.pthupd.servlet.FixDispatcherServlet?actionType=downloadFixes", false); //33209

	} 

	public boolean soloReadOnly() {
		return true;
	}

	//34388 fine

	//33209 ini

	private void doLoginDomino(ClassADCollection cadc, ServletEnvironment se) throws ServletException, IOException {
		String user = se.getRequest().getParameter("username");
		String password = se.getRequest().getParameter("password");
		String actionType = se.getRequest().getParameter("actionType");

		//Prendo i parametri username e password necessari alla login su Domino
		//Se le credenziali esistono e sono corrette continua con con l'azione che ha richiesto la login
		//andando sulla servlet FixDispatcherServlet
		//Altrimenti apre la pagina LoginDomino.jsp indicando all'utente che le credenziali inserite sono errate

		if(user != null && password != null) {
			if(DominoCookieGetter.getCookieFromLoginDomino(user, password) != null) {
				se.getSession().setAttribute("userDomino", user);
				se.getSession().setAttribute("pswDomino", password);
				se.sendRequest(getServletContext(), "/servlet/it.thera.thip.base.pthupd.servlet.FixDispatcherServlet?actionType=" + actionType, false);
			}else {
				se.sendRequest(getServletContext(), "/it/thera/thip/base/pthupd/LoginDomino.jsp?errorCredentials=true&actionType=" + actionType, false);
			}
		}else {
			se.sendRequest(getServletContext(), "/it/thera/thip/base/pthupd/LoginDomino.jsp?errorCredentials=true&actionType=" + actionType, false);
		}
	}

	private void callServletDownloadFix(ClassADCollection cadc, ServletEnvironment se) throws ServletException, IOException {
		String isDownloadPrereqParam = se.getRequest().getParameter("isDownloadPrereq");
		boolean isDownloadPrereq = isDownloadPrereqParam != null && isDownloadPrereqParam.equalsIgnoreCase("true");
		String url = isDownloadPrereq ? "/servlet/it.thera.thip.base.pthupd.servlet.DownloadFixServlet?isDownloadPrereq=true" : 
			"/servlet/it.thera.thip.base.pthupd.servlet.DownloadFixServlet";
		se.sendRequest(getServletContext(), url, false);
	}

	//33209 fine


}
