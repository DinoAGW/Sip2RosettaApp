package de.zbmed.rosetta;

import java.nio.charset.StandardCharsets;

import de.zbmed.utilities.Custom;
import de.zbmed.utilities.XML;
import de.zbmed.utilities.XML.NODELIST;
//import de.zbmed.utilities.WebServices;

public class SRU {

	public static String searchIE(String rosettaInstance, String query) throws Exception {
		String[] command = new String[4];
		command[0] = "curl";
		command[1] = "-H";
		command[2] = "Authorization: ".concat(Authentification.getAuthToken(rosettaInstance));
		command[3] = Custom.getSRU_URL(rosettaInstance)
				.concat("?version=1.2&auth=local&operation=searchRetrieve&query=").concat(query)
				.concat("&maximumRecords=10000&recordSchema=dc");
		ProcessBuilder pb = new ProcessBuilder(command);
		Process p = pb.start();
		String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);// InputStream.readAllbytes
																								// benötigt Java
																								// mindestens 9
		p.waitFor();
		int exitCode;
		if ((exitCode = p.exitValue()) != 0) {
			throw new Exception("Curl endete mit exitCode = " + exitCode);
		}
		return output;
	}

	public static String searchIEByUserDefinedA(String rosettaInstance, String userDefinedA) throws Exception {
		return searchIE(rosettaInstance, "IE.generalIECharacteristics.UserDefinedA=".concat(userDefinedA));
	}

	public static String getIePidToUserDefinedA(String rosettaInstance, String userDefinedA) throws Exception {
		String sruAntwortString = searchIEByUserDefinedA(rosettaInstance, userDefinedA);
		XML sruAntwortXml = new XML(sruAntwortString);
		NODELIST nl = sruAntwortXml.getNode().getChildren().get(0).getChildren();
		if (!"1".equals(nl.filter("nameEquals", "numberOfRecords").get(0).getTextContent())) {
			throw new Exception("Genau eine Antwort der SRU Schnittstelle erwartet");
		}
		return nl.filter("nameEquals", "records").get(0).getChildren().filter("nameEquals", "record").get(0)
				.getChildren().filter("nameEquals", "recordData").get(0).getChildren().filter("nameEquals", "dc:record")
				.get(0).getChildren().filter("attrEquals", "dc:identifier", "xsi:type", "PID").get(0).getTextContent();
	}

	public static void main(String[] args) throws Exception {
		String iePid = getIePidToUserDefinedA("prod", "17bbag20");
		System.out.println(iePid);
//		System.out.println(WebServices.getIE("prod", iePid));
	}

}
