package de.zbmed.rosetta;

import java.net.URL;

import javax.xml.namespace.QName;

import com.exlibris.dps.IEWebServices_Service;
import com.exlibris.dps.sdk.pds.HeaderHandlerResolver;

import de.zbmed.utilities.Custom;
import de.zbmed.utilities.XML;

public class IEWS {
	public static XML getIE(String iePid, String rosettaInstance) throws Exception {
		final String rosettaURL = Custom.getRosettaURL(rosettaInstance);
		final String institution = Custom.getInstitution(rosettaInstance);
		final String userName = Custom.getUsername(rosettaInstance);
		final String password = Custom.getPassword(rosettaInstance);
		final String IE_WSDL_URL = Custom.getIE_WSDL_URL(rosettaURL);
		
		IEWebServices_Service ieWS = new IEWebServices_Service(new URL(IE_WSDL_URL),
				new QName("http://dps.exlibris.com/", "IEWebServices"));
		ieWS.setHandlerResolver(new HeaderHandlerResolver(userName, password, institution));

		int repeat = 10;
		String retString = null;
		while (true) {
			try {
				Thread.sleep(1000);
				retString = ieWS.getIEWebServicesPort().getIE(null, iePid, null);
				break;
			} catch (Exception e) {
				if (repeat>0) {
					System.err.println("getIE fehlgeschlagen");
					--repeat;
				} else {
					throw new Exception("getIE fehlgeschlagen");
				}
			}
		}
		if (retString == null) {
			throw new Exception("getIE ergab Nullantwort");
		}
		return new XML(retString);
	}
}
