package it.thera.thip.base.pthupd.servlet;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.thera.thermfw.ad.ClassADCollection;
import com.thera.thermfw.web.ServletEnvironment;
import com.thera.thermfw.web.servlet.BaseServlet;

import it.thera.thip.base.pthupd.DominoCookieGetter;
/*
 * Revisions:
 * Number     Date          Owner      Description
 * 33209      03/11/2021    FG         Prima versione
 */

/*
 * Servlet che serve per fare il check del Login su Domino
 * Se il login non è ancora effettuato apre la pagina di LoginDomino.jsp
 * altrimenti, in base al parametro "actionType" svolge l'azione aprendo la pagina jsp relativa.
 */
public class FixDispatcherServlet extends HttpServlet{
	
	private static final String ACTION_DOWNLOAD_FIXES = "downloadFixes"; //Azione download fixes
	private static final String ACTION_DOWNLOAD_PREREQS = "downloadPrereqs"; //Azione download prerequisiti
	private static final String ACTION_VIEW_SCHEDA = "viewScheda"; //Azione apertura scheda fix
	private static final String ACTION_CALCOLA_PREREQS = "calcPrereqs"; //Azione calcola prerequisiti
	private static final String ACTION_SET_FIX_DA_INSTALLARE = "setFixDaInstallare"; //Azione installazione in automatico //35629
	
	//Fa il check del login su Domino
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		//Tipo di download che deve effettuare l'utente (download di "fixes" oppure di prerequisiti "prereqs")
		String action = req.getParameter("actionType");
		
		String user = (String) req.getSession().getAttribute("userDomino");
		String password = (String) req.getSession().getAttribute("pswDomino");

		if(user != null && password != null) {
			//Rilevo il cookie di autenticazione
			String cookie = DominoCookieGetter.getCookieFromLoginDomino(user, password);
			
			if(cookie != null) {
				
				//Metto in sessione il cookie di domino rilevato
				req.getSession().setAttribute("dominoCookie", cookie);
				
				if(action != null && action.equalsIgnoreCase(ACTION_DOWNLOAD_FIXES)) {
					//Rimando alla jsp di download delle fix
					req.getSession().getServletContext().getRequestDispatcher(("/it/thera/thip/base/pthupd/DownloadFix.jsp")).include(req, resp);
				}
				else if(action != null && action.equalsIgnoreCase(ACTION_DOWNLOAD_PREREQS)){
					//Rimando alla jsp di download dei prerequisiti
					//req.getSession().getServletContext().getRequestDispatcher("/servlet/it.thera.thip.base.pthupd.servlet.DownloadFixServlet?isDownloadPrereq=true").forward(req, resp);
					req.getSession().getServletContext().getRequestDispatcher(("/it/thera/thip/base/pthupd/DownloadFix.jsp?isDownloadPrereq=true")).include(req, resp);

				}else if(action != null && action.equalsIgnoreCase(ACTION_VIEW_SCHEDA)) {
					//Rimanda alla jsp di visualizzazione scheda Panthera
					req.getSession().getServletContext().getRequestDispatcher("/it/thera/thip/base/pthupd/openSchedaPanthera.jsp").forward(req, resp);
					
				}else if(action != null && action.equalsIgnoreCase(ACTION_CALCOLA_PREREQS)) {
					//Rimanda alla jsp di calcolo dei prerequisiti
					req.getSession().getServletContext().getRequestDispatcher("/it/thera/thip/base/pthupd/web/SpecificaDownloadFixPrereq.jsp").forward(req, resp);
				}else if(action != null && action.equalsIgnoreCase(ACTION_SET_FIX_DA_INSTALLARE)) {
					//Rimanda alla jsp di installazione in automatico delle fix selezionate
					req.getSession().getServletContext().getRequestDispatcher("/it/thera/thip/base/pthupd/SetFixDaInstallare.jsp").forward(req, resp); //35629
				}
				
			}else {
				//Se il cookie è null rimando alla jsp di login domino
				req.getSession().getServletContext().getRequestDispatcher(("/it/thera/thip/base/pthupd/LoginDomino.jsp?actionType=" + action)).forward(req, resp);
			}
		}else {
			//Se user e password di domino non sono in sessione rimando alla jsp di login domino
			req.getSession().getServletContext().getRequestDispatcher(("/it/thera/thip/base/pthupd/LoginDomino.jsp?actionType=" + action)).forward(req, resp);
		}
	}
	
	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException{
		doGet(req, resp);
	}
	
	
	
}
