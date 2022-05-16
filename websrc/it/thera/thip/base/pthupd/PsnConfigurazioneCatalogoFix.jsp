<!DOCTYPE html>
<html>

<head>
<%@ page import="it.thera.thip.base.pthupd.PsnCatalogoFix" %>
<%@ page import="com.thera.thermfw.base.ResourceLoader" %>
<title><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "Pagina")%></title>
<%String URL = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();%>
<base href="<%=URL%>/">
<script src="it/thera/thip/base/pthupd/lib/jquery.min.js"></script>
<link href="thermweb/css/therm.css" rel="stylesheet" type="text/css" />
<link href="thermweb/css/thermHF.css" rel="STYLESHEET" type="text/css" >
<link href="it/thera/thip/cs/form.css" rel="STYLESHEET" type="text/css" >
<link rel="stylesheet" href="it/thera/thip/base/pthupd/style/PsnCatalogoFixStyle.css" >
<script> window.resizeTo(600, 350); </script>
</head>

<body>
	<form id="form">
		<table>
			<tr>
				<td><p class="titolo"><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "Titolo")%></p></td>
			</tr>
			<tr>
				<td colspan=2><hr></td>
			</tr>
			<tr>
				<td><label for="RepositoryFix"><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "Repository")%></label></td>
				<td><input class="obbligatorio" type="text" id="RepositoryFix" name="RepositoryFix" required></td>
			</tr>
			<tr>
				<td><label for="DirectoryBlat"><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "Blat")%></label></td>
				<td><input type="text" id="DirectoryBlat" name="DirectoryBlat"></td>
			</tr>
			<tr>
				<td><label for="Email"><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "Email")%></label></td>
				<td><textarea id="Email" rows="5" cols="30"></textarea></td>
			</tr>
			<tr>
				<td><label for="Utente"><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "Utente")%></label></td>
				<td><input type="text" id="Utente" name="Utente"></td>
			</tr>
			<tr>
				<td><label for="Password"><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "Pwd")%></label></td>
				<td><input type="password" id="Password" name="Password"></td>
			</tr>
		</table>
		<button id="OkBtn" type='button' onclick='postConfigurazione()'><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "Ok")%></button>
		<button type='button' onclick='window.close()'><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "Annulla")%></button>
	</form>
</body>

<script type="text/javascript">
	var id;
	var getResult = "200";
	var token = localStorage.getItem("pth-jwt");
	
	if(token){
	$.ajax({
				'url' : '<%=URL%>/' + 'api/catalogo-fix/psn',
				'method' : 'GET',
				'headers' : {'Authorization' : 'Bearer ' + token},
				'contentType' : "application/json",
				'success' : function(data) {
					document.getElementById("RepositoryFix").value = data.RepositoryFix;
					document.getElementById("DirectoryBlat").value = data.DirectoryBlat;
					document.getElementById("Email").value = data.MailTo;
					document.getElementById("Utente").value = data.Utente;
					document.getElementById("Password").value = data.Password;
					id = data.Id;
				},
				'error' : function(err) {
					let errorText = '';
					if(err.status != 404){
						for (let i = 0; i < err.responseJSON.errors.length; i++) {
							errorText += err.responseJSON.errors[i].text + '\n';
						}
						alert('<%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "Error")%>' + err.status + '\n' + errorText);
					}
					
					if (err.status == 401) {
						window.close();
					}
					if(err.status == 404){
						getResult = err.status;
					}
				}
			});
	}else{
		alert('<%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "NonAutorizzato")%>');
		parent.window.close();
	}
	
	function postConfigurazione() {
		document.getElementById("OkBtn").disabled = true;
		
		var rep = document.getElementById("RepositoryFix").value;
		var blat = document.getElementById("DirectoryBlat").value;
		var mail = document.getElementById("Email").value;
		var utente = document.getElementById("Utente").value;
		var password = document.getElementById("Password").value;
		var informazioniConfigurazione = {
			"RepositoryFix" : rep,
			"DirectoryBlat" : blat,
			"Utente" : utente,
			"Id" : id,
			"MailTo" : mail,
			"Password" : password
		};
		
		let metodo;
		if(getResult == 404){
			metodo = 'POST';
		}else{
			metodo = 'PUT';
		}
		
		if (checkCampiRegex(informazioniConfigurazione)) {
			$.ajax({
				'url' : '<%=URL%>/' + 'api/catalogo-fix/psn',
				'method' : metodo,
				'dataType' : 'text',
				'headers' : {'Authorization' : 'Bearer ' + token},
				'contentType' : "application/json",
				'data' : JSON.stringify(informazioniConfigurazione),
				'success' : function(data) {
					location.reload(true);
				},
				'error' : function(err) {
					let errorText = '';
					let response = JSON.parse(err.responseText)
					
					for (let i = 0; i < response.errors.length; i++) {
					errorText += response.errors[i].text + '\n';
						}
					
					alert('<%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "Error")%>' + ' ' + err.status + ' : ' + errorText);
					document.getElementById("OkBtn").disabled = false;
				}
			});
		}else{
			document.getElementById("OkBtn").disabled = false;
		}
	}
	
	function checkCampiRegex(informazioniConfigurazione) {
		if (!informazioniConfigurazione.RepositoryFix) {
			alert("<%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "RepositoryObbligatoria")%>");
			return false;
		}
		
		if (informazioniConfigurazione.MailTo) {
			let mail = "([a-zA-Z0-9._%-]+@[a-zA-Z0-9.-]+\.{1}[a-zA-Z]{2,10})";
			if(!informazioniConfigurazione.MailTo.match("^" + mail + "((;" + mail + ")?)*(;)?$")){
				alert('<%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "MailNonValida")%>');
				return false;
			}
			
			if (!informazioniConfigurazione.DirectoryBlat) {
				alert("<%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "BlatRichiesta")%>");
				return false;
			}
		}
		return true;
	}
	
</script>
</html>