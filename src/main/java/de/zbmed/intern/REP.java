package de.zbmed.intern;

import java.io.File;
import java.util.HashMap;
import java.util.Stack;

import com.exlibris.core.sdk.consts.Enum;
import com.exlibris.digitool.common.dnx.DnxDocument;
import com.exlibris.digitool.common.dnx.DnxDocumentHelper;
import com.exlibris.digitool.common.dnx.DnxDocumentHelper.AccessRightsPolicy;
import com.exlibris.dps.sdk.deposit.IEParser;

import de.zbmed.utilities.Utilities;
import de.zbmed.utilities.XML;
import gov.loc.mets.DivType;
import gov.loc.mets.StructMapType;
import gov.loc.mets.MetsDocument.Mets;
import gov.loc.mets.MetsType.FileSec.FileGrp;

public class REP {
	private static final String fs = System.getProperty("file.separator");

	String preservationType;
	Stack<FILE> files = new Stack<>();
	String label;
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
		XML.NODE metsNode = metsXml.getNode().getChildren().filter("nameEquals", "mets:mets").get(0);

		XML.NODE repAmd = metsNode.getChildren().filter("attrEquals", "mets:amdSec", "ID", id + "-amd").get(0);
		XML.NODE arPolicyRecord = repAmd.getChildren().filter("nameEquals", "mets:rightsMD").get(0).getChildren()
				.filter("nameEquals", "mets:mdWrap").get(0).getChildren().filter("nameEquals", "mets:xmlData").get(0)
				.getChildren().filter("nameEquals", "dnx").get(0).getChildren()
				.filter("attrEquals", "section", "id", "accessRightsPolicy").get(0).getChildren()
				.filter("nameEquals", "record").get(0);
		String arPolicyId = arPolicyRecord.getChildren().filter("attrEquals", "key", "id", "policyId").get(0)
				.getTextContent();
		String arPolicyDescription = arPolicyRecord.getChildren().filter("attrEquals", "key", "id", "policyDescription")
				.get(0).getTextContent();
		this.setARPolicy(arPolicyId, arPolicyDescription);
		String label = repAmd.getChildren().filter("nameEquals", "mets:techMD").get(0).getChildren()
				.filter("nameEquals", "mets:mdWrap").get(0).getChildren().filter("nameEquals", "mets:xmlData").get(0)
				.getChildren().filter("nameEquals", "dnx").get(0).getChildren()
				.filter("attrEquals", "section", "id", "generalRepCharacteristics").get(0).getChildren()
				.filter("nameEquals", "record").get(0).getChildren().filter("attrEquals", "key", "id", "label").get(0)
				.getTextContent();
		this.setLabel(label);
		XML.NODELIST files = metsNode.getChildren().filter("nameEquals", "mets:fileSec").get(0).getChildren()
				.filter("attrEquals", "mets:fileGrp", "ID", id).get(0).getChildren();
		for (XML.NODE file : files) {
			String fileId = file.getAttributes().get("ID");
			String fileOriginalPath = file.getChildren().get(0).getAttributes().get("xlin:href");
			String dateipfad = "file:///".concat(metsFile.getParent()).concat(fs).concat("streams").concat(fs)
					.concat(fileOriginalPath);
			this.newFile(dateipfad, fileOriginalPath).loadFromSip(metsFile, fileId);
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

	public void printoutDiff(SIP otherSip) {
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
}
