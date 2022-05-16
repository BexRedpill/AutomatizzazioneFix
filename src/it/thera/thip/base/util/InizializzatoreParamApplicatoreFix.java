package it.thera.thip.base.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.thera.thermfw.base.SystemParam;
import com.thera.thermfw.persist.CachedStatement;
import com.thera.thermfw.persist.ConnectionManager;
import com.thera.thermfw.security.Security;
import com.thera.thermfw.web.LicenceManager;

public class InizializzatoreParamApplicatoreFix {

	private String ivMailTo = "set MAIL_TO=";
	private String ivRepositoryFix = "set REPOSITORY_FIX=";
	private String ivDirectoryBlat = "set DIRECTORY_BLAT=";
	private String ivDiscoPth = "set DISCO_PTH=";
	private String ivIstanza = "set ISTANZA=";
	private String ivIdClientePth = "set ID_CLIENTE_PTH=";
	private String ivIdRagSocClientePth = "set RAGIONE_SOC_CLIENTE_PTH=";
	private String ivUtente = "set UTENTE_DOMINO=";
	private String ivPassword = "set PWD_UTENTE_DOMINO=";
	private ArrayList <String> ivRigheFile = new ArrayList <String> ();
	private String ivThipRoot;

	private static File fileWithDirectoryAssurance(String directory, String filename) {
		File dir = new File(directory);
		if (!dir.exists()) dir.mkdirs();
		return new File(directory + "/" + filename);
	}

	protected void writeFile(File f, Collection lines){
		try {
			PrintWriter output = new PrintWriter(new FileWriter(f));
			Iterator i = lines.iterator();
			while(i.hasNext())
				output.println(i.next());
			output.close();
		}catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public String getCodiceClientePTH() {
		String ret = new String();

		List l = LicenceManager.getAltreInfornmazioniLicenza();
		if (l.size() > 2)
			ret = (String)LicenceManager.getAltreInfornmazioniLicenza().get(1);
		return ret;
	}

	public String getRagioneSocialeCliente() {
		String ret = new String();

		List l = LicenceManager.getAltreInfornmazioniLicenza();
		if (l.size() > 2)
			ret = (String)LicenceManager.getAltreInfornmazioniLicenza().get(0);
		return ret;
	}

	public boolean run(String[] args){
		ivThipRoot = args[1];
		ivDiscoPth += ivThipRoot.trim().substring(0, 2);
		ivRigheFile.add(ivDiscoPth);
		LicenceManager.initLicence("THIP");

		String[] words = ivThipRoot.split("\\\\");
		ivIstanza += words[2];
		ivRigheFile.add(ivIstanza);

		try {
			String qry = "SELECT * FROM " + SystemParam.getSchema("THIP") + "PSN_CATALOGO_FIX";
			CachedStatement cmd = new CachedStatement(qry); 
			ResultSet result = cmd.executeQuery();

			if(result.next()) {

				ivMailTo += result.getString("MAIL_TO");
				ivRepositoryFix += result.getString("REPOSITORY_FIX");
				ivDirectoryBlat += result.getString("DIR_BLAT");
				ivUtente += result.getString("UTENTE_DOMINO");
				ivPassword += result.getString("PWD_UTENTE_DOMINO");

			}
			result.close();
			cmd.free();
		}catch(SQLException e) {
			e.printStackTrace();
		}
		ivRigheFile.add(ivDirectoryBlat);
		ivRigheFile.add(ivRepositoryFix);
		ivRigheFile.add(ivMailTo);

		ivIdClientePth += getCodiceClientePTH();
		ivRigheFile.add(ivIdClientePth);

		ivIdRagSocClientePth += getRagioneSocialeCliente();
		ivRigheFile.add(ivIdRagSocClientePth);
		
		ivRigheFile.add(ivUtente);
		ivRigheFile.add(ivPassword);

		File file = fileWithDirectoryAssurance(ivThipRoot + "\\bin", "SetVarApplicatoreFix.bat");
		writeFile(file, ivRigheFile);

		return true;
	}

	public static void main(String[] args) throws Exception{
		if(args != null && args.length == 2) {
			Security.setCurrentDatabase(args[0], null); 
			Security.openDefaultConnection(); 
			InizializzatoreParamApplicatoreFix paramFix = new InizializzatoreParamApplicatoreFix();
			paramFix.run(args);
			Security.closeDefaultConnection(); 
		}else {
			System.out.println("Errore!");
		}
	}
}