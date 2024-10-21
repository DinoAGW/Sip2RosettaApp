package de.zbmed.intern;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

import com.exlibris.core.sdk.formatting.DublinCore;
import com.exlibris.digitool.common.dnx.DnxDocument;
import com.exlibris.digitool.common.dnx.DnxDocumentHelper;
import com.exlibris.digitool.common.dnx.DnxDocumentHelper.AccessRightsPolicy;
import com.exlibris.digitool.common.dnx.DnxDocumentHelper.FileFixity;
import com.exlibris.dps.sdk.deposit.IEParser;

import de.zbmed.utilities.Utilities;
import de.zbmed.utilities.XML;
import gov.loc.mets.DivType;
import gov.loc.mets.FileType;
import gov.loc.mets.MetsType.FileSec.FileGrp;

public class FILE {
	private class Metadata {
		private String xPathKey;
		private String value;

		Metadata(String xPathKey, String value) {
			this.xPathKey = xPathKey;
			this.value = value;
		}
	}

	enum Datenquelle {
		Filesytem, Internet, Rosetta
	};

	private static final String fs = System.getProperty("file.separator");

	Stack<Metadata> metadata = new Stack<>();
	String fileOriginalName; // Dateiname in Rosetta
	String fileOriginalPath; // Pfad+Dateiname in Rosetta
	String pfadDerDatei; // Pfad+Dateiname der Quelldatei
	String md5sum = null;
	String label;
	String arPolicyId = "AR_EVERYONE";
	String arPolicyDescription = "Keine Beschränkung";

	private Datenquelle datenQuelle;
	private REP rep;
	private boolean moveMode = false;
	private String mimeType = null;
	private String fileTypeId;

	FILE(String dateipfad, String fileOriginalPath, REP rep) throws Exception {
		this.rep = rep;
		if (dateipfad.startsWith("file:///")) {
			this.datenQuelle = Datenquelle.Filesytem;
			this.pfadDerDatei = dateipfad.substring(8);
		} else if (dateipfad.startsWith("http://") || dateipfad.startsWith("https://")) {
			this.datenQuelle = Datenquelle.Internet;
			this.pfadDerDatei = dateipfad;
		} else if (dateipfad.startsWith("rosetta")) {
			this.datenQuelle = Datenquelle.Rosetta;
			this.pfadDerDatei = dateipfad;
			throw new Exception("Muss noch ausgearbeitet werden");
		} else {
			throw new Exception("Dateipfad muss mit einem gültigen Protokoll anfangen");
		}

		int lastSlash = dateipfad.lastIndexOf('/');
		String name = lastSlash != -1 ? dateipfad.substring(lastSlash + 1) : dateipfad;

		String fop;
		if ((fileOriginalPath == null) || (fileOriginalPath.length() == 0)) {
			fop = "";
			this.fileOriginalName = name;
		} else if (fileOriginalPath.charAt(fileOriginalPath.length() - 1) == '/') {
			fop = fileOriginalPath;
			this.fileOriginalName = name;
		} else if (fileOriginalPath.contains("/")) {
			fop = fileOriginalPath.substring(0, fileOriginalPath.lastIndexOf("/") + 1);
			this.fileOriginalName = fileOriginalPath.substring(fileOriginalPath.lastIndexOf("/") + 1);
		} else {
			fop = "";
			this.fileOriginalName = fileOriginalPath;
		}
		this.fileOriginalPath = fop.concat(this.fileOriginalName);
		// falls Modus aktiv noch this.rep.label.concat(fs) davor
		if (this.rep.sip.repFolderMode) {
			this.fileOriginalPath = this.rep.label.concat(fs).concat(this.fileOriginalPath);
		}
		this.label = this.fileOriginalName;
	}

	public void create() throws Exception {
		throw new Exception("Noch nicht implementiert");
		// erstellt eine Temporäre Datei auf der Festplatte,
		// welche eine Kopie der Urspungsdatei von wo auch immer ist
	}

	public FILE addMetadata(String xPathKey, String value) {
		this.metadata.add(new Metadata(xPathKey, value));
		return this;
	}

	public FILE setMimeType(String mimeType) {
		this.mimeType = mimeType;
		return this;
	}

	void placeFolderInsideStructMap(HashMap<String, DivType> divTypes) {
		int last = -1;
		int next;
		while ((next = this.fileOriginalPath.indexOf(fs, last + 1)) != -1) {// stimmt fs überhaupt immer?
			String nextString = this.fileOriginalPath.substring(0, next + 1);
			if (!divTypes.containsKey(nextString)) {
				String stelleString = this.fileOriginalPath.substring(0, last + 1);
				DivType div = divTypes.get(stelleString).addNewDiv();
				div.setLABEL(this.fileOriginalPath.substring(last + 1, next));
				divTypes.put(nextString, div);
			}
			last = next;
		}
	}

	void placeFileInsideStructMap(HashMap<String, DivType> divTypes) {
		int lastSlash = this.fileOriginalPath.lastIndexOf(fs);
		String folder = lastSlash != -1 ? this.fileOriginalPath.substring(0, lastSlash + 1) : "";
		DivType div = divTypes.get(folder).addNewDiv();
		div.setLABEL(this.label);
		div.setTYPE("FILE");
		div.addNewFptr().setFILEID(this.fileTypeId);
	}

	public FILE setARPolicy(String arPolicyId, String arPolicyDescription) {
		this.arPolicyId = arPolicyId;
		this.arPolicyDescription = arPolicyDescription;
		return this;
	}

	public FILE setLabel(String label) {
		this.label = label;
		return this;
	}

	public FILE setMd5sum(String md5sum) {
		this.md5sum = md5sum;
		return this;
	}

	public FILE setMoveMode(boolean moveStattCopy) {
		this.moveMode = moveStattCopy;
		return this;
	}

	void placeToTarget(String zielVerzeichnis) throws Exception {
		File file = new File(this.pfadDerDatei);
		if (!file.exists()) {
			System.err.println("Datei ist verschwunden von " + this.pfadDerDatei);
			throw new Exception();
		}

		String target = zielVerzeichnis.concat(this.fileOriginalPath);
		File targetfile = new File(target);
		if (targetfile.exists()) {
			System.err
					.println("Datei konnte nicht platziert werden, da " + this.fileOriginalPath + " bereits existiert");
			throw new Exception();
		}
		targetfile.mkdirs();
		try {
			if (moveMode) {
				Files.move(file.toPath(), Paths.get(target), StandardCopyOption.REPLACE_EXISTING);
			} else {
				Files.copy(file.toPath(), Paths.get(target), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Exception e) {
			System.err.println("Datei konnte nicht platziert werden: " + file.toPath() + " -> " + Paths.get(target));
			System.err.println("targetfile = " + targetfile);
			throw new Exception();
		}
	}

	void checkComplete() throws Exception {
		File file = new File(this.pfadDerDatei);
		if (!file.exists()) {
			throw new Exception("Datei ist verschwunden von " + this.pfadDerDatei);
		}
	}

	void saveAllToSip(FileGrp fGrp, IEParser ie) throws Exception {
		FileType fileType = ie.addNewFile(fGrp, this.mimeType, this.fileOriginalPath.replace('\\', '/'), this.label);
		this.fileTypeId = fileType.getID();

		if (!this.metadata.empty()) {
			// Füge Metadaten hinzu
			DublinCore dc = ie.getDublinCoreParser();
			for (Metadata md : metadata) {
				dc.addElement(md.xPathKey, md.value);
			}
			ie.setDublinCore(dc, this.fileTypeId);
		}

		DnxDocument fileDnx = ie.getFileDnx(this.fileTypeId);
		DnxDocumentHelper fileDnxHelper = new DnxDocumentHelper(fileDnx);
		AccessRightsPolicy ar = fileDnxHelper.new AccessRightsPolicy(this.arPolicyId, null, this.arPolicyDescription);
		fileDnxHelper.setAccessRightsPolicy(ar);
		ie.setFileDnx(fileDnxHelper.getDocument(), this.fileTypeId);
	}

	void checkMd5sums(IEParser ie) throws Exception {
		if (md5sum != null) {
			// add dnx - A new DNX is constructed and added on the file level
			DnxDocument dnx = ie.getFileDnx(this.fileTypeId);
			DnxDocumentHelper fileDocumentHelper = new DnxDocumentHelper(dnx);
			List<FileFixity> ffxtyList = fileDocumentHelper.getFileFixitys();
			for (FileFixity ffxty : ffxtyList) {
				if (ffxty.getFixityType().contentEquals("MD5")) {
					if (!ffxty.getFixityValue().contentEquals(this.md5sum)) {
						System.err.println("MD5-Summe stimmt nicht. Ist = '" + ffxty.getFixityValue() + "' Soll = '"
								+ this.md5sum + "'");
						throw new Exception();
					}
				}
			}
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
		StringBuilder line1 = new StringBuilder("File ");
		line1.append(this.fileOriginalName);
		line1.append(" = ");
		line1.append(quote(this.fileOriginalPath));
		System.out.println(line1);
		if (!this.metadata.empty()) {
			StringBuilder mds = new StringBuilder("Metadata:");
			for (Metadata md : this.metadata) {
				mds.append("\n");
				mds.append(quote(md.xPathKey));
				mds.append(" = ");
				mds.append(quote(md.value));
			}
			System.out.println(mds);
		}
		System.out.println("Quelle = ".concat(this.pfadDerDatei));
		System.out.println("Label = ".concat(this.label));
		System.out.println("MD5 = ".concat(quote(this.md5sum)));
		StringBuilder arp = new StringBuilder("AccessRights Policy = ");
		arp.append(this.arPolicyId);
		arp.append(" (");
		arp.append(this.arPolicyDescription);
		arp.append(")");
		System.out.println(arp);
	}

	private FILE loadFromMetsSip(File metsFile, String id) throws Exception {
		XML metsXml = new XML(metsFile);
		XML.NODE metsNode = metsXml.getNode().getChildren().filter("nameEquals", "mets:mets").get(0);

		XML.NODE fileAmd = metsNode.getChildren().filter("attrEquals", "mets:amdSec", "ID", id + "-amd").get(0);
		XML.NODE arPolicyRecord = fileAmd.getChildren().filter("nameEquals", "mets:rightsMD").get(0).getChildren()
				.filter("nameEquals", "mets:mdWrap").get(0).getChildren().filter("nameEquals", "mets:xmlData").get(0)
				.getChildren().filter("nameEquals", "dnx").get(0).getChildren()
				.filter("attrEquals", "section", "id", "accessRightsPolicy").get(0).getChildren()
				.filter("nameEquals", "record").get(0);
		String arPolicyId = arPolicyRecord.getChildren().filter("attrEquals", "key", "id", "policyId").get(0)
				.getTextContent();
		String arPolicyDescription = arPolicyRecord.getChildren().filter("attrEquals", "key", "id", "policyDescription")
				.get(0).getTextContent();
		this.setARPolicy(arPolicyId, arPolicyDescription);
		XML.NODE techMdDnx = fileAmd.getChildren().filter("nameEquals", "mets:techMD").get(0).getChildren()
				.filter("nameEquals", "mets:mdWrap").get(0).getChildren().filter("nameEquals", "mets:xmlData").get(0)
				.getChildren().filter("nameEquals", "dnx").get(0);
		String label = techMdDnx.getChildren().filter("attrEquals", "section", "id", "generalFileCharacteristics")
				.get(0).getChildren().filter("nameEquals", "record").get(0).getChildren()
				.filter("attrEquals", "key", "id", "label").get(0).getTextContent();
		this.setLabel(label);
		XML.NODE fileFixityRecord = techMdDnx.getChildren().filter("attrEquals", "section", "id", "fileFixity").get(0)
				.getChildren().filter("nameEquals", "record").get(0);
		if (!fileFixityRecord.getChildren().filter("attrEquals", "key", "id", "fixityType").get(0).getTextContent()
				.contentEquals("MD5")) {
			throw new Exception(
					"An dieser Stelle wird eigentlich nur MD5 erwartet, auch wenn andere fixityTypes auch machbar wären");
		}
		String fixityValue = fileFixityRecord.getChildren().filter("attrEquals", "key", "id", "fixityValue").get(0)
				.getTextContent();
		this.setMd5sum(fixityValue);
		XML.NODELIST fileDmds = metsNode.getChildren().filter("attrEquals", "mets:dmdSec", "ID", id + "-dmd");
		if (fileDmds.size() != 0) {
			XML.NODELIST ieMetadata = fileDmds.get(0).getChildren().filter("nameEquals", "mets:mdWrap").get(0)
					.getChildren().filter("nameEquals", "mets:xmlData").get(0).getChildren()
					.filter("nameEquals", "dc:record").get(0).getChildren();
			for (XML.NODE md : ieMetadata) {
				this.addMetadata(md.getXPathKey(), md.getTextContent());
			}
		}
		return this;
	}

	public FILE loadFromSip(File ingestFile, String id) throws Exception {
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
}
