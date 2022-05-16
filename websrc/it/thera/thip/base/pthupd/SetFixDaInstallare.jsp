<!DOCTYPE html>
<html>

<head>
<%@ page import="it.thera.thip.base.pthupd.PsnCatalogoFix"%>
<%@ page import="com.thera.thermfw.base.ResourceLoader"%>
<%@ page import="java.util.List"%>
<% List<String> fixSelezionate = (List)session.getAttribute("KeysToDownload"); %>
<% String user = (String)session.getAttribute("userDomino"); %>
<% String pwd = (String)session.getAttribute("pswdDomino"); %>
<title><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "PaginaSospendiFix")%></title>
<%
String URL = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
%>
<base href="<%=URL%>/">
<script src="it/thera/thip/base/pthupd/lib/jquery.min.js"></script>
<link href="thermweb/css/therm.css" rel="stylesheet" type="text/css" />
<link href="thermweb/css/thermHF.css" rel="STYLESHEET" type="text/css">
<link href="it/thera/thip/cs/form.css" rel="STYLESHEET" type="text/css">
<link rel="stylesheet" href="it/thera/thip/base/pthupd/style/PsnCatalogoFixStyle.css">
<script> window.resizeTo(700, 250); </script>
</head>


<body bottommargin="0" leftmargin="0" rightmargin="0" topmargin="0" style="height: 100vh;">
	<form id="Attendere" name="Attendere" style="height: 100%">
		<table>
	  		<tr>
				<td><p class="titolo"><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "OperazioneInCorso")%></p></td>
			</tr>
		</table>
	</form>


	<form id="NoFixAnticip" name="NoFixAnticip" style = 'display: none; height: 100%'>
		<table style="height: auto;">
	  		<tr>
				<td><p class="titolo"><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "InstallazioneProssimoRiavvio")%></p></td>
			</tr>
			
			<tr>
				<td><button onclick = 'window.close()'><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "Ok")%></button></td>
			</tr>	
		</table>
	</form>


	<form id="FixAnticipate" name="FixAnticipate" style = 'display: none; height: 100%'>
		<table>
			<tr>
				<td><p class="titolo"><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "SospendiFix")%></p></td>
			</tr>

			<tr>
				<td colspan=2><hr></td>
			</tr>

			<tr>
				<td><input type="checkbox"
					id="CheckForzaSospensioneFixAnticipate"
					name="CheckForzaSospensioneFixAnticipate"> <label
					for="CheckForzaSospensioneFixAnticipate"><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "SospendiFix")%></label></td>
			</tr>

			<tr>
				<td><br></br> <label for="FixRiallineamentoPersonalizzazioni"><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "ZipRiallineamentoPers")%></label></td>
				<td><br></br> <input type="file"
					name="FixRiallineamentoPersonalizzazioni" id="FixRiallineamentoPersonalizzazioni"
					accept=".zip" /></td>
			</tr>
			
			<tr>
				<td><button id="OkBtn" type='button' onclick="setFixInstallaAutomatico('FixAnticipate')"><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "Ok")%></button>
				<button type='button' onclick='window.close()'><%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "Annulla")%></button></td>
			</tr>
		</table>
	</form>
</body>

<script type="text/javascript">
	var token = localStorage.getItem("pth-jwt");
	var fixAnticip = false;


	if(token){
	$.ajax({
			'url' : '<%=URL%>/' + 'api/catalogo-fix/fix-anticipate',
			'method' : 'GET',
			'headers' : {'Authorization' : 'Bearer ' + token},
			'contentType' : "application/json",
			'success' : function(data) {
				var numerofix = data.NumeroFixAnticipate;
				
				if( numerofix == 0){
					window.resizeTo(500, 250);
					setFixInstallaAutomatico('NoFixAnticip');	
				}else{
					fixAnticip = true;
					document.getElementById("Attendere").style.display = 'none';
					document.getElementById("FixAnticipate").style.display = 'block';
				}
			},
			'error' : function(err) {
				let errorText = '';
					for (let i = 0; i < err.responseJSON.errors.length; i++) {
						errorText += err.responseJSON.errors[i].text + '\n';
					}
					alert('<%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "Error")%>' + ' '+ err.status + ' ' + err.responseJSON.status + '\n' + errorText);
				if (err.status == 401) {
					window.close();
				}
			}
		});
	}else{
		alert('<%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "NonAutorizzato")%>');
		parent.window.close();
	}
	
	function setFixInstallaAutomatico(id){
		let myForm = document.getElementById(id);
		let formData = new FormData(myForm);
		
		formData.append("UserDomino", '<%=user%>');
		formData.append("PwdDomino", '<%=pwd%>');
		
		let stringaFix = '';
		<%
		for(int i = 0; i < fixSelezionate.size(); i++){
			%>
			 stringaFix += <%=fixSelezionate.get(i)%> + ";"; 
		<%	
		}
		%>
		
		let anticip = false;
		
		if(fixAnticip){
			anticip = document.getElementById("CheckForzaSospensioneFixAnticipate").checked;
		}
		
		formData.append("FixSelezionate", stringaFix);
		formData.append("ForzaFixAnticip", anticip);
		
		$.ajax({
			'url' : '<%=URL%>/' + 'api/catalogo-fix/installa-in-automatico',
			'method' : 'PUT',
			'dataType' : 'text',
			'headers' : {'Authorization' : 'Bearer ' + token},
			'contentType' : false,
			'processData' : false,
			'data' : formData,
			'success' : function(data) {
				if(id == 'FixAnticipate'){
					alert('<%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "ModificheCorrette")%>');
					parent.window.close();
				}
				if(id == 'NoFixAnticip'){
					document.getElementById("Attendere").style.display = 'none';
					document.getElementById("NoFixAnticip").style.display = 'block';
				}
			},
			'error' : function(err) {
				let errorText = '';
				let response = JSON.parse(err.responseText)
				
				for (let i = 0; i < response.errors.length; i++) {
				errorText += response.errors[i].text + '\n';
					}
				
				alert('<%=ResourceLoader.getString(PsnCatalogoFix.PROPERTIES, "Error")%>' + ' ' + err.status + ' : ' + errorText);
				parent.window.close();
			}
		});
	}
	
</script>
</html>