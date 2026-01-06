package de.zbmed.rosetta;


import java.util.Base64;

import de.zbmed.utilities.*;

public class Authentification {

	public static String getAuthToken(String rosettaInstance) throws Exception {
		StringBuilder sb = new StringBuilder();
		sb.append(Customs.getUsername(rosettaInstance));
		sb.append("-institutionCode-");
		sb.append(Customs.getInstitution(rosettaInstance));
		sb.append(":");
		sb.append(Customs.getPassword(rosettaInstance));
		return "Basic ".concat(Base64.getEncoder().encodeToString(sb.toString().getBytes()));
	}
}
