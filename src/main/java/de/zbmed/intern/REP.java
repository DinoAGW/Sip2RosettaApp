package de.zbmed.intern;

import java.io.File;
import java.util.HashMap;
import java.util.Objects;
import java.util.Stack;

import com.exlibris.core.sdk.consts.Enum;
import com.exlibris.digitool.common.dnx.DnxDocument;
import com.exlibris.digitool.common.dnx.DnxDocumentHelper;
import com.exlibris.digitool.common.dnx.DnxDocumentHelper.AccessRightsPolicy;
import com.exlibris.dps.sdk.deposit.IEParser;

import de.zbmed.rosetta.IEWS;
import de.zbmed.utilities.Utilities;
import de.zbmed.utilities.XML;
import de.zbmed.utilities.XML.NODE;
import de.zbmed.utilities.XML.NODELIST;
import gov.loc.mets.DivType;
import gov.loc.mets.MetsDocument.Mets;
import gov.loc.mets.MetsType.FileSec.FileGrp;
import gov.loc.mets.StructMapType;

public class REP {
	private static final String fs = System.getProperty("file.separator");

	String preservationType;
	Stack<FILE> files = new Stack<>();
	String label = null;
	String arPolicyId = "AR_EVERYONE";
	String arPolicyDescription = "Keine Beschränkung";

	SIP sip;
	private FileGrp fGrp;

	/**
	 * input: Preservation Type preservationType For example PRESERVATION_MASTER
	 * PRE_INGEST_MODIFIED_MASTER MODIFIED_MASTER selfmade preservationtypes
	 */
	REP(String preservationType, SIP sip) {
		if (preservationType == null) {
			this.preservationType = "PRESERVATION_MASTER";
		} else {
			this.preservationType = preservationType;
		}
		this.sip = sip;
		this.label = "Rep".concat(Integer.toString(sip.reps.size() + 1)).concat(" (").concat(this.preservationType)
				.concat(")");
	}

	void addStructMap(Mets mets) throws Exception {
		StructMapType sm = mets.addNewStructMap();
		sm.setID(this.fGrp.getID() + "-1"); // je nachdem welche ID schon vergeben ist
		sm.setTYPE("LOGICAL");
		HashMap<String, DivType> divTypes = new HashMap<String, DivType>();
		DivType div1 = sm.addNewDiv();
		div1.setLABEL(this.label);
		divTypes.put("", div1);
		for (FILE file : files) {
			file.placeFolderInsideStructMap(divTypes);
		}
		for (FILE file : files) {
			file.placeFileInsideStructMap(divTypes);
		}
	}

	public REP setLabel(String label) {
		this.label = label;
		return this;
	}

	public FILE newFile(String dateipfad, String fileOriginalPath) throws Exception {
		FILE file = new FILE(dateipfad, fileOriginalPath, this);
		files.push(file);
		return file;
	}

	public REP setARPolicy(String arPolicyId, String arPolicyDescription) {
		this.arPolicyId = arPolicyId;
		this.arPolicyDescription = arPolicyDescription;
		return this;
	}

	void placeToTarget(String zielVerzeichnis) throws Exception {
		for (FILE file : files) {
			try {
				file.placeToTarget(zielVerzeichnis.concat("content").concat(fs).concat("streams").concat(fs));
			} catch (Exception e) {
				System.err.println("Fehler beim platzieren der Datei");
				throw e;
			}
		}
	}

	void checkComplete() throws Exception {
		if (this.files.empty()) {
			throw new Exception("Repräsentation hat keine Dateien: " + this.preservationType);
		}
		for (FILE file : files) {
			file.checkComplete();
		}
	}

	void saveAllToSip(IEParser ie) throws Exception {
		this.fGrp = ie.addNewFileGrp(Enum.UsageType.VIEW, preservationType);
		DnxDocument dnxDocument = ie.getFileGrpDnx(this.fGrp.getID());
		DnxDocumentHelper documentHelper = new DnxDocumentHelper(dnxDocument);
		documentHelper.getGeneralRepCharacteristics().setRevisionNumber("1");
		documentHelper.getGeneralRepCharacteristics().setLabel(this.label);
		AccessRightsPolicy ar = documentHelper.new AccessRightsPolicy(this.arPolicyId, null, this.arPolicyDescription);
		documentHelper.setAccessRightsPolicy(ar);
		ie.setFileGrpDnx(documentHelper.getDocument(), this.fGrp.getID());

		for (FILE file : this.files) {
			file.saveAllToSip(this.fGrp, ie);
		}
	}

	void checkMd5sums(IEParser ie) throws Exception {
		for (FILE file : this.files) {
			file.checkMd5sums(ie);
		}
	}

	private String quote(String str) {
		if (str == null) {
			return "null";
		} else {
			return "'".concat(str).concat("'");
		}
	}

	public void printout() {
		StringBuilder line1 = new StringBuilder("Repräsentation ");
		line1.append(this.preservationType);
		line1.append(" = ");
		line1.append(quote(this.label));
		System.out.println(line1);
		StringBuilder arp = new StringBuilder("AccessRights Policy = ");
		arp.append(this.arPolicyId);
		arp.append(" (");
		arp.append(this.arPolicyDescription);
		arp.append(")");
		System.out.println(arp);
		for (FILE file : this.files) {
			file.printout();
		}
	}

	private REP loadFromMetsSip(File metsFile, String id) throws Exception {
		XML metsXml = new XML(metsFile);
		NODE metsNode = metsXml.getNode().getChildren().filter("nameEquals", "mets:mets").get(0);

		NODE repAmd = metsNode.getChildren().filter("attrEquals", "mets:amdSec", "ID", id + "-amd").get(0);
		NODELIST accessRightsPolicy = repAmd.getChildren().filter("nameEquals", "mets:rightsMD").get(0).getChildren()
				.filter("nameEquals", "mets:mdWrap").get(0).getChildren().filter("nameEquals", "mets:xmlData").get(0)
				.getChildren().filter("nameEquals", "dnx").get(0).getChildren()
				.filter("attrEquals", "section", "id", "accessRightsPolicy");
		if (accessRightsPolicy.size() > 0) {
			NODE arPolicyRecord = accessRightsPolicy.get(0).getChildren().filter("nameEquals", "record").get(0);
			String arPolicyId = arPolicyRecord.getChildren().filter("attrEquals", "key", "id", "policyId").get(0)
					.getTextContent();
			String arPolicyDescription = arPolicyRecord.getChildren()
					.filter("attrEquals", "key", "id", "policyDescription").get(0).getTextContent();
			this.setARPolicy(arPolicyId, arPolicyDescription);
		}
		String label = repAmd.getChildren().filter("nameEquals", "mets:techMD").get(0).getChildren()
				.filter("nameEquals", "mets:mdWrap").get(0).getChildren().filter("nameEquals", "mets:xmlData").get(0)
				.getChildren().filter("nameEquals", "dnx").get(0).getChildren()
				.filter("attrEquals", "section", "id", "generalRepCharacteristics").get(0).getChildren()
				.filter("nameEquals", "record").get(0).getChildren().filter("attrEquals", "key", "id", "label").get(0)
				.getTextContent();
		this.setLabel(label);
		NODELIST files = metsNode.getChildren().filter("nameEquals", "mets:fileSec").get(0).getChildren()
				.filter("attrEquals", "mets:fileGrp", "ID", id).get(0).getChildren();
		for (NODE file : files) {
			String fileId = file.getAttributes().get("ID");
			String dateipfad = "file:///".concat(metsFile.getParent()).concat(fs).concat("streams").concat(fs)
					.concat(file.getChildren().get(0).getAttributes().get("xlin:href"));
			String fileOriginalPath = null;
			NODELIST fileOriginalPaths = metsNode.getChildren()
					.filter("attrEquals", "mets:amdSec", "ID", fileId.concat("-amd")).get(0).getChildren()
					.filter("nameEquals", "mets:techMD").get(0).getChildren().filter("nameEquals", "mets:mdWrap").get(0)
					.getChildren().filter("nameEquals", "mets:xmlData").get(0).getChildren().filter("nameEquals", "dnx")
					.get(0).getChildren().filter("attrEquals", "section", "id", "generalFileCharacteristics").get(0)
					.getChildren().filter("nameEquals", "record").get(0).getChildren()
					.filter("attrEquals", "key", "id", "fileOriginalPath");
			if (fileOriginalPaths.size() == 1) {
				fileOriginalPath = fileOriginalPaths.get(0).getTextContent();
			}
			this.newFile(dateipfad, fileOriginalPath).loadFromSip(metsFile, fileId);
		}
		return this;
	}

	public REP loadFromRosetta(String rosettaInstance, String iePid, XML metsXml, String id) throws Exception {
		if (metsXml == null) {
			metsXml = IEWS.getIE(iePid, rosettaInstance);
		}
		NODE metsNode = metsXml.getNode().getChildren().filter("nameEquals", "mets:mets").get(0);

		NODE repAmd = metsNode.getChildren().filter("attrEquals", "mets:amdSec", "ID", id + "-amd").get(0);
		NODELIST accessRightsPolicies = repAmd.getChildren().filter("nameEquals", "mets:rightsMD").get(0).getChildren()
				.filter("nameEquals", "mets:mdWrap").get(0).getChildren().filter("nameEquals", "mets:xmlData").get(0)
				.getChildren().filter("nameEquals", "dnx").get(0).getChildren()
				.filter("attrEquals", "section", "id", "accessRightsPolicy");
		if (accessRightsPolicies.size() != 0) {
			NODE arPolicyRecord = accessRightsPolicies.get(0).getChildren().filter("nameEquals", "record").get(0);
			String arPolicyId = arPolicyRecord.getChildren().filter("attrEquals", "key", "id", "policyId").get(0)
					.getTextContent();
			String arPolicyDescription = arPolicyRecord.getChildren()
					.filter("attrEquals", "key", "id", "policyDescription").get(0).getTextContent();
			this.setARPolicy(arPolicyId, arPolicyDescription);
		}
		NODELIST labels = repAmd.getChildren().filter("nameEquals", "mets:techMD").get(0).getChildren()
				.filter("nameEquals", "mets:mdWrap").get(0).getChildren().filter("nameEquals", "mets:xmlData").get(0)
				.getChildren().filter("nameEquals", "dnx").get(0).getChildren()
				.filter("attrEquals", "section", "id", "generalRepCharacteristics").get(0).getChildren()
				.filter("nameEquals", "record").get(0).getChildren().filter("attrEquals", "key", "id", "label");
		if (labels.size() == 1) {
			String label = labels.get(0).getTextContent();
			this.setLabel(label);
		}
		NODELIST files = metsNode.getChildren().filter("nameEquals", "mets:fileSec").get(0).getChildren()
				.filter("attrEquals", "mets:fileGrp", "ID", id).get(0).getChildren();
		for (NODE file : files) {
			String fileId = file.getAttributes().get("ID");
			String dateipfad = "rosetta://".concat(file.getChildren().get(0).getAttributes().get("xlin:href"));
			String fileOriginalPath = metsNode.getChildren()
					.filter("attrEquals", "mets:amdSec", "ID", fileId.concat("-amd")).get(0).getChildren()
					.filter("nameEquals", "mets:techMD").get(0).getChildren().filter("nameEquals", "mets:mdWrap").get(0)
					.getChildren().filter("nameEquals", "mets:xmlData").get(0).getChildren().filter("nameEquals", "dnx")
					.get(0).getChildren().filter("attrEquals", "section", "id", "generalFileCharacteristics").get(0)
					.getChildren().filter("nameEquals", "record").get(0).getChildren()
					.filter("attrEquals", "key", "id", "fileOriginalPath").get(0).getTextContent();
			this.newFile(dateipfad, fileOriginalPath).loadFromRosetta(rosettaInstance, iePid, metsXml, fileId);
		}
		return this;
	}

	public REP loadFromSip(File ingestFile, String id) throws Exception {
		Utilities.checkFile(ingestFile);
		String ingestFilePath = ingestFile.toString();
		String contentPath = ingestFilePath.substring(0, ingestFilePath.lastIndexOf(fs) + 1);
		Utilities.checkFolder(new File(contentPath + fs + "streams"));
		if (ingestFilePath.endsWith(".xml")) {
			return loadFromMetsSip(ingestFile, id);
		}
		throw new Exception(
				"IngestDatei Dateiendung nicht implementiert: " + ingestFilePath.substring(contentPath.length()));
	}

	public void printoutDiff(SIP otherSip) throws Exception {
		int otherRepIndex = -1;
		for (int index = 0; index < otherSip.reps.size(); ++index) {
			if (Objects.equals(this.preservationType, otherSip.reps.get(index).preservationType)) {
				if (otherRepIndex == -1) {
					otherRepIndex = index;
				} else {
					throw new Exception(
							"Andere SIP hat mehrere Repräsentationen, welche der hiesigen Repräsentation entsprechen könnten.\nDies ist zurzeit nicht erlaubt.");
				}
			}
		}
		if (otherRepIndex == -1) {
			throw new Exception(
					"Andere SIP hat keine Repräsentation, welche der hiesigen Repräsentation entsprechen könnte.\nDies ist zurzeit nicht erlaubt.");
		}
		REP otherRep = otherSip.reps.get(otherRepIndex);
		System.out.println("RepIndex: " + otherRepIndex);

		if (!Objects.equals(this.label, otherRep.label)) {
			StringBuilder line1 = new StringBuilder("Hier: Repräsentation ");
			line1.append(this.preservationType);
			line1.append(" = ");
			line1.append(quote(this.label));
			line1.append("\n");
			line1.append("Dort: Repräsentation ");
			line1.append(otherRep.preservationType);
			line1.append(" = ");
			line1.append(quote(otherRep.label));
			System.out.println(line1);
		}
		if (!Objects.equals(this.arPolicyId, otherRep.arPolicyId)
				|| !Objects.equals(this.arPolicyDescription, otherRep.arPolicyDescription)) {
			StringBuilder arp = new StringBuilder("Hier: AccessRights Policy = ");
			arp.append(this.arPolicyId);
			arp.append(" (");
			arp.append(this.arPolicyDescription);
			arp.append(")");
			arp.append("\n");
			arp.append("Dort: AccessRights Policy = ");
			arp.append(otherRep.arPolicyId);
			arp.append(" (");
			arp.append(otherRep.arPolicyDescription);
			arp.append(")");
			System.out.println(arp);
		}
		for (FILE file : this.files) {
			file.printoutDiff(otherRep);
		}
		// es fehlen noch die zu löschenden Dateien zu ermitteln
	}
}
