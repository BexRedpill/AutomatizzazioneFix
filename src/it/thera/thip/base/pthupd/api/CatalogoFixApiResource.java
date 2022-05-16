package it.thera.thip.base.pthupd.api;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.collections4.map.HashedMap;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.json.JSONArray;
import org.json.JSONObject;

import com.thera.thermfw.base.ResourceLoader;
import com.thera.thermfw.base.SystemParam;
import com.thera.thermfw.common.ErrorMessage;
import com.thera.thermfw.log.LogTask;
import com.thera.thermfw.persist.CachedStatement;
import com.thera.thermfw.persist.ConnectionManager;
import com.thera.thermfw.persist.ErrorCodes;
import com.thera.thermfw.persist.Factory;
import com.thera.thermfw.persist.PersistentObject;
import com.thera.thermfw.rs.BaseResource;

import it.thera.thip.base.pthupd.Fix;
import it.thera.thip.base.pthupd.PsnCatalogoFix;

@Path("/catalogo-fix")
@Produces("application/json")
@Consumes("application/json")
public class CatalogoFixApiResource extends BaseResource{

	private static final String ID_CATALOGO_0 = "0";
	private static final String BAS0000045 = "BAS0000045";
	private static final String CONFIGURZIONE_CATALOGO_MANCANTE = "ConfigurzioneCatalogoMancante";
	private static final String SEPARATORE_PUNTO_E_VIRGOLA = ";";
	private static final String NON_ESISTE = " non esiste";
	private static final String LA_FIX = "La fix ";
	private static final String LA_STRINGA_CON_LE_FIX_NON_È_CORRETTA = "La stringa con le fix non è corretta";
	private static final String PWD_DOMINO = "PwdDomino";
	private static final String USER_DOMINO = "UserDomino";
	private static final String FIX_RIALLINEAMENTO_PERSONALIZZAZIONI = "FixRiallineamentoPersonalizzazioni";
	private static final String FORZA_FIX_ANTICIPATE = "ForzaFixAnticip";
	private static final String PASSWORD = "Password";
	private static final String UTENTE = "Utente";
	private static final String MAIL_REGEX = "([a-zA-Z0-9._%-]+@[a-zA-Z0-9.-]+\\.{1}[a-zA-Z]{2,10})";
	private static final String LISTA_FIX = "(([0-9]){5})";
	private static final String BAS0000078 = "BAS0000078";
	private static final String DIRECTORY_BLAT = "DirectoryBlat";
	private static final String STRINGA_VUOTA = "";
	private static final String MAIL_TO = "MailTo";
	private static final String REPOSITORY_FIX = "RepositoryFix";
	private static final String REPOSITORY_OBBLIGATORIA = "RepositoryObbligatoria";
	private static final String ERRORE_CREDENZIALI = "ErroreCredenziali";
	private static final String MAIL_NON_VALIDA = "MailNonValida";
	private static final String BLAT_RICHIESTA = "BlatRichiesta";
	public static final String PROPERTIES = "it.thera.thip.base.pthupd.resources.PsnCatalogoFix";
	private static final String FIX_ANTICIPATE = "FixAnticipate";
	private static final String NUMERO_FIX_ANTICIPATE = "NumeroFixAnticipate";
	public static final String TAB_NAME = SystemParam.getFrameworkSchema() + "ADVANCE_FIXES";
	public static final String SELECT_SQL = "SELECT ADVANCE_FIX_NUMBER, CLOSING_FIX_NUMBER FROM " + TAB_NAME + " WHERE STATUS = 'V'";
	private static final String FIX_SELEZIONATE = "FixSelezionate";

	protected static CachedStatement selectFixAnticipate = new CachedStatement(SELECT_SQL);

	@GET @Path("/psn")
	public Response getPersCatalogoFix() throws SQLException {
		PsnCatalogoFix catalogo = PsnCatalogoFix.elementWithKey(ID_CATALOGO_0, PersistentObject.OPTIMISTIC_LOCK);

		if(catalogo != null) {
			JSONObject json = new JSONObject();
			json.put(MAIL_TO, catalogo.getMailTo());
			json.put(REPOSITORY_FIX, catalogo.getRepositoryFix());
			json.put(DIRECTORY_BLAT, catalogo.getDirectoryBlat());
			json.put(UTENTE, catalogo.getUtente());
			json.put(PASSWORD, catalogo.getPassword());
			json.put("Id", catalogo.getId());
			return buildResponse(Status.OK, json);
		}else {
			sendError(Status.NOT_FOUND, new ErrorMessage(BAS0000045));
			return buildResponse(Status.NOT_FOUND);
		}
	}

	@GET @Path("/fix-anticipate")
	public Response getFixAnticipate() throws SQLException {

		List<String> listFixAnticipate = new ArrayList<String>();
		JSONArray fixAnticipate = new JSONArray(listFixAnticipate);

		ResultSet setFixAnticipate = selectFixAnticipate.executeQuery();
		while(setFixAnticipate.next()) {
			listFixAnticipate.add(setFixAnticipate.getString(1));
		}
		setFixAnticipate.close();

		for(String fixAnticipata: listFixAnticipate) {
			fixAnticipate.put(fixAnticipata);
		}

		JSONObject json = new JSONObject();
		json.put(FIX_ANTICIPATE, fixAnticipate);
		json.put(NUMERO_FIX_ANTICIPATE, listFixAnticipate.size());

		return buildResponse(Status.OK, json);
	}
	
	@PUT @Path("/installa-in-automatico")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response updateTipoInstallazioneInAutomatico(
			@FormDataParam(FIX_SELEZIONATE) String stringaFix,
			@FormDataParam(FORZA_FIX_ANTICIPATE) boolean forzaFixAnticip, 
			@FormDataParam(FIX_RIALLINEAMENTO_PERSONALIZZAZIONI) InputStream fileInputStream,
			@FormDataParam(FIX_RIALLINEAMENTO_PERSONALIZZAZIONI) FormDataContentDisposition fileDetail,
			@FormDataParam(USER_DOMINO) String user,
			@FormDataParam(PWD_DOMINO) String password) throws SQLException {

		List<Integer> result = new ArrayList<Integer>();
		Map<String, ErrorMessage> erroriFix = new HashedMap<String, ErrorMessage>();

		if(checkListaFix(stringaFix)) {
			String[] fixSelezionate = stringaFix.split(SEPARATORE_PUNTO_E_VIRGOLA);
			
			for(String fixNum: fixSelezionate) {
				Fix fix = Fix.elementWithKey(fixNum, PersistentObject.OPTIMISTIC_LOCK);
				if(fix != null) {
					
					if(fix.getTipoInstallazione() == Fix.TI_NON_INSTALLATA) {
						fix.setTipoInstallazione(Fix.TI_INSTALLA_IN_AUTOMATICO);
						fix.setForzaFixAnticip(forzaFixAnticip);
						
						int rc = -1;
						try {
							rc = fix.save();
							result.add(rc);
							erroriFix.put(fixNum, daRcAErrorMessage(rc, null));
						}catch (SQLException e){
							e.printStackTrace();
							erroriFix.put(fixNum, daRcAErrorMessage(rc, e));
						}
					}
				}else {
					sendError(Status.CONFLICT, BAS0000078, LA_FIX + fixNum + NON_ESISTE);
				}
			}
		}else {
			sendError(Status.CONFLICT, BAS0000078, LA_STRINGA_CON_LE_FIX_NON_È_CORRETTA);
		}
		
		List<ErrorMessage> errors = new ArrayList<ErrorMessage>();
		for(ErrorMessage errori: erroriFix.values()) {
			if(errori != null) {
				errors.add(errori);
			}
		}
		
		for(Integer resultrc : result) {
			if(resultrc < 0){
				ConnectionManager.rollback();
				sendErrors(Status.CONFLICT, errors);
			}
		}
		
		if(errors.isEmpty()) {
			PsnCatalogoFix catalogo = PsnCatalogoFix.elementWithKey(ID_CATALOGO_0, PersistentObject.OPTIMISTIC_LOCK);
			if(catalogo != null) {
				String rep = catalogo.getRepositoryFix();
				
				if(fileInputStream != null && !fileDetail.getFileName().equals("")) {
					try {
						OutputStream out = new FileOutputStream(new File(new File(rep), fileDetail.getFileName()));
						int read = 0;
						byte[] bytes = new byte[1024];

						while ((read = fileInputStream.read(bytes)) != -1) {
							out.write(bytes, 0, read);
						}
						out.flush();
						out.close();
						
					} catch (IOException e) {
						e.printStackTrace();
						sendError(Status.CONFLICT, createMessageForIOException(e, fileDetail.getFileName()));
					}
				}
				LogTask.startLogTaskForced("Fix", "INSTALLA_AUTOM", true, stringaFix);
				ConnectionManager.commit();
				
				return buildResponse(Status.OK, "");
			}else {
				sendError(Status.BAD_REQUEST, ResourceLoader.getString(PROPERTIES, CONFIGURZIONE_CATALOGO_MANCANTE));
			}
			
		}
		return null;
	}

	@PUT @Path("/psn")
	public Response updatePsnCatalogoFix(String entity) throws SQLException {
		System.out.println(entity);
		PsnCatalogoFix catalogo = PsnCatalogoFix.elementWithKey(ID_CATALOGO_0, PersistentObject.OPTIMISTIC_LOCK);
		if (catalogo == null)
		{
			sendError(Status.NOT_FOUND, new ErrorMessage(BAS0000045));
			return buildResponse(Status.NOT_FOUND);
		}
		
		JSONObject json = new JSONObject(entity);

		checkCampi(json);

		catalogo.setMailTo(json.getString(MAIL_TO));
		catalogo.setRepositoryFix(json.getString(REPOSITORY_FIX));
		catalogo.setDirectoryBlat(json.getString(DIRECTORY_BLAT));
		catalogo.setUtente(json.getString(UTENTE));
		catalogo.setPassword(json.getString(PASSWORD));

		int rc = -1;
		ErrorMessage err = null;
		try {
			rc = catalogo.save();
			err = daRcAErrorMessage(rc, null);
		}catch (SQLException e){
			e.printStackTrace();
			err = daRcAErrorMessage(rc, e);
			throw e;
		}

		if(rc >= 0) {
			ConnectionManager.commit();
			return buildResponse(Status.OK);
		}else {
			sendError(Status.CONFLICT, err);
		}
		return null;
	}

	@POST @Path("/psn")
	public Response newPsnCatalogoFix(String entity) throws SQLException {
		System.out.println(entity);
		PsnCatalogoFix catalogo = (PsnCatalogoFix)Factory.createObject(PsnCatalogoFix.class);

		JSONObject json = new JSONObject(entity);

		checkCampi(json);

		catalogo.setMailTo(json.getString(MAIL_TO));
		catalogo.setRepositoryFix(json.getString(REPOSITORY_FIX));
		catalogo.setDirectoryBlat(json.getString(DIRECTORY_BLAT));
		catalogo.setUtente(json.getString(UTENTE));
		catalogo.setPassword(json.getString(PASSWORD));

		int rc = -1;
		ErrorMessage err = null;
		try {
			rc = catalogo.save();
			err = daRcAErrorMessage(rc, null);
		}catch (SQLException e){
			e.printStackTrace();
			err = daRcAErrorMessage(rc, e);
			throw e;
		}

		if(rc >= 0) {
			ConnectionManager.commit();
			return buildResponse(Status.OK);
		}else {
			sendError(Status.CONFLICT, err);
		}
		return null;
	}

	private void checkCampi(JSONObject json) {
		if(json.isNull(REPOSITORY_FIX)) {
			sendError(Status.CONFLICT, BAS0000078, ResourceLoader.getString(PROPERTIES, REPOSITORY_OBBLIGATORIA));
		}else {
			if(json.getString(REPOSITORY_FIX).equals(STRINGA_VUOTA)) {
				sendError(Status.CONFLICT, BAS0000078, ResourceLoader.getString(PROPERTIES, REPOSITORY_OBBLIGATORIA));
			}
		}

		if(!json.isNull(MAIL_TO)) {
			if(!json.getString(MAIL_TO).equals(STRINGA_VUOTA)) {
				if(!checkMail(json.getString(MAIL_TO))) {
					sendError(Status.CONFLICT, BAS0000078, ResourceLoader.getString(PROPERTIES, MAIL_NON_VALIDA));
				}else {
					if(json.isNull(DIRECTORY_BLAT)) {
						sendError(Status.CONFLICT, BAS0000078, ResourceLoader.getString(PROPERTIES, BLAT_RICHIESTA));
					}else {
						if(json.getString(DIRECTORY_BLAT).equals(STRINGA_VUOTA)) {
							sendError(Status.CONFLICT, BAS0000078, ResourceLoader.getString(PROPERTIES, BLAT_RICHIESTA));
						}
					}
				}
			}	
		}else {
			sendError(Status.CONFLICT, BAS0000078, ResourceLoader.getString(PROPERTIES, MAIL_NON_VALIDA));
		}

		if(json.isNull(UTENTE) || json.isNull(PASSWORD)) {
			sendError(Status.CONFLICT, BAS0000078, ResourceLoader.getString(PROPERTIES, ERRORE_CREDENZIALI));
		}
	}

	private boolean checkMail(String mail) {
		String mailValida = MAIL_REGEX;
		if(mail.matches("^" + mailValida + "((;" + mailValida + ")?)*(;)?$")) {
			return true;
		}
		return false;
	}

	private boolean checkListaFix(String listaFix) {
		String fix = LISTA_FIX;
		if(listaFix.matches("^" + fix + "((;" + fix + ")?)*(;)?$")) {
			return true;
		}
		return false;
	}

	public static ErrorMessage daRcAErrorMessage(int rc, SQLException sqlException){

		if (rc == ErrorCodes.NO_ROWS_UPDATED && sqlException == null) {
			return new ErrorMessage("BAS0000032");
		}
		else if (rc < ErrorCodes.OK){
			ErrorMessage message = null;
			if (rc == ErrorCodes.OPTIMISTIC_LOCK_FAILED) {
				return new ErrorMessage("BAS0000035");
			}
			else if (rc == ErrorCodes.DUPLICATED_ROW)
				return new ErrorMessage("BAS0000034");
			else if (rc == ErrorCodes.CONSTRAINT_VIOLATION)
				return new ErrorMessage("BAS0000033");
			else if (rc == ErrorCodes.LOCKED_ROW || rc == ErrorCodes.OBJ_TIMEOUT)
				return new ErrorMessage("BAS0000019");
			else if (rc == ErrorCodes.NO_ROWS_FOUND)
				return new ErrorMessage(BAS0000045);
			else {
				if (sqlException != null)
					return createMessageForSQLException(sqlException);
				else
					return new ErrorMessage("BAS0000036", String.valueOf(rc));

			}
		}
		return null;
	}

	protected static ErrorMessage createMessageForSQLException(SQLException sqlException)
	{
		return new ErrorMessage("BAS0000036", String.valueOf(sqlException.getErrorCode())+": " + sqlException.getLocalizedMessage());
	}
	
	protected ErrorMessage createMessageForIOException(IOException ioException, String nomeFile)
	{
		return new ErrorMessage(BAS0000078, "Caricamento file " + nomeFile + " non riuscito: " + String.valueOf(ioException.getLocalizedMessage()));
	}
}