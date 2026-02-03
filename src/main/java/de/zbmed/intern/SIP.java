package de.zbmed.intern;

import java.io.File;
import java.util.Objects;
import java.util.Stack;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;

import com.exlibris.core.sdk.consts.Enum;
import com.exlibris.core.sdk.formatting.DublinCore;
import com.exlibris.core.sdk.utils.FileUtil;
import com.exlibris.digitool.common.dnx.DnxDocument;
import com.exlibris.digitool.common.dnx.DnxDocumentHelper;
import com.exlibris.digitool.common.dnx.DnxDocumentHelper.AccessRightsPolicy;
import com.exlibris.digitool.common.dnx.DnxDocumentHelper.CMS;
import com.exlibris.digitool.common.dnx.DnxDocumentHelper.GeneralIECharacteristics;
import com.exlibris.dps.sdk.deposit.IEParser;
import com.exlibris.dps.sdk.deposit.IEParserFactory;

import de.zbmed.rosetta.IEWS;
import de.zbmed.utilities.Utilities;
import de.zbmed.utilities.XML;
import de.zbmed.utilities.XML.NODE;
import de.zbmed.utilities.XML.NODELIST;
import gov.loc.mets.MdSecType.MdWrap.MDTYPE;
import gov.loc.mets.MetsDocument;
import gov.loc.mets.MetsDocument.Mets;

public class SIP {
	private class Metadata {
		private String xPathKey;
		private String value;

		Metadata(String xPathKey, String value) {
			this.xPathKey = xPathKey;
			this.value = value;
		}
	}

	private static final String fs = System.getProperty("file.separator");

	Stack<REP> reps = new Stack<>();
	Stack<Metadata> metadata = new Stack<>();
	String userDefinedA = null;
	String userDefinedB = null;
	String userDefinedC = null;
	String cmsSystem;
	String cmsRecordId;
	MDTYPE.Enum sourceMdType = null;
	String otherSourceMdType = null;
	XmlObject sourceMd = null;
	String arPolicyId = "AR_EVERYONE";
	String arPolicyDescription = "Keine Beschränkung";

	boolean repFolderMode = false;

	private static final String ROSETTA_METS_SCHEMA = "http://www.exlibrisgroup.com/xsd/dps/rosettaMets";
	private static final String METS_SCHEMA = "http://www.loc.gov/METS/";
	private static final String XML_SCHEMA = "http://www.w3.org/2001/XMLSchema-instance";
	private static final String XML_SCHEMA_REPLACEMENT = "http://www.exlibrisgroup.com/XMLSchema-instance";

	public REP newREP(String preservationType) {
		REP rep = new REP(preservationType, this);
		reps.push(rep);
		return rep;
	}

	public SIP addMetadata(String xPathKey, String value) {
		this.metadata.add(new Metadata(xPathKey, value));
		return this;
	}

	public SIP setUserDefined(String ABorC, String value) throws Exception {
		switch (ABorC) {
		case "A":
			this.userDefinedA = value;
			break;
		case "B":
			this.userDefinedB = value;
			break;
		case "C":
			this.userDefinedC = value;
			break;
		default:
			throw new Exception("ABorC sollte A, B oder C sein, ist aber = '" + ABorC + "'");
		}
		return this;
	}

	public SIP setCms(String system, String recordId) throws Exception {
		if (system == null)
			throw new Exception("System darf nicht null sein");
		if (recordId == null)
			throw new Exception("RecordId darf nicht null sein");
		this.cmsSystem = system;
		this.cmsRecordId = recordId;
		return this;
	}

	public SIP setSourceMD(MDTYPE.Enum mdType, XmlObject sourceMd, String otherMdType) {
		this.sourceMdType = mdType;
		this.sourceMd = sourceMd;
		this.otherSourceMdType = otherMdType;
		return this;
	}

	public SIP setARPolicy(String arPolicyId, String arPolicyDescription) {
		this.arPolicyId = arPolicyId;
		this.arPolicyDescription = arPolicyDescription;
		return this;
	}

	private void checkComplete() throws Exception {
		if (reps.empty()) {
			throw new Exception("SIP hat keine Repräsentation");
		}

		boolean hasPreservationMaster = false;
		for (REP rep : reps) {
			if (rep.preservationType.contentEquals("PRESERVATION_MASTER")) {
				hasPreservationMaster = true;
			}
			rep.checkComplete();
		}
		if (!hasPreservationMaster) {
			throw new Exception("Es gibt keinen 'PRESERVATION_MASTER'");
		}

		if (this.metadata.empty()) {
			throw new Exception("Es gibt keine Metadaten");
		}
	}

	public void setRepFolderMode(boolean value) {
		this.repFolderMode = value;
	}

	public void saveAllToSip(String ziel) throws Exception {
		File zielFile = new File(ziel);
		if (zielFile.exists()) {
			System.err.println("Ziel schon belegt: " + zielFile.getAbsolutePath());
			throw new Exception();
		}
		if (ziel.charAt(ziel.length() - 1) != fs.charAt(0)) {
			ziel = ziel.concat(fs);
		}

		checkComplete();

		// Dateien an Zielort verschieben
		for (REP rep : reps) {
			try {
				rep.placeToTarget(ziel);
			} catch (Exception e) {
				System.err.println("SIP-Auslieferung nicht erfolgreich: Es konnte eine Datei nicht platziert werden.");
				System.err.println("SIP hat damit einen inkonsistenten Zustand und ist nun unbrauchbar.");
				System.err.println("Ich weiß nicht wie das passieren konnte.");
				System.err.println("Es tut mir Leid =(");
				throw e;
			}
		}

		// Erstelle mets
		IEParser ie = IEParserFactory.create();
		DublinCore dc = ie.getDublinCoreParser();

		// Füge Metadaten hinzu
		for (Metadata md : metadata) {
			dc.addElement(md.xPathKey, md.value);
		}
		ie.setIEDublinCore(dc);

		// Füge ggf SourceMD hinzu
		if (this.sourceMd != null) {
			if (this.otherSourceMdType == null) {
				ie.setIeSourceMd(this.sourceMdType, this.sourceMd);
			} else {
				ie.setIeSourceMd(this.sourceMdType, this.sourceMd, this.otherSourceMdType);
			}
		}

		// Füge Repräsentationen hinzu
		for (REP rep : reps) {
			rep.saveAllToSip(ie);
		}

		String filesRootFolder = ziel.concat("content").concat(fs).concat("streams").concat(fs);
		ie.generateChecksum(filesRootFolder, Enum.FixityType.MD5.toString());
		for (REP rep : reps) {
			rep.checkMd5sums(ie);
		}

		ie.updateSize(filesRootFolder);

		// create IE DNX Section
		DnxDocument ieDnx = ie.getDnxParser();
		DnxDocumentHelper ieDnxHelper = new DnxDocumentHelper(ieDnx);

		// add userDefinedFields if set
		if (this.userDefinedA != null || this.userDefinedB != null || this.userDefinedC != null) {
			GeneralIECharacteristics generalIeCharacteristics = ieDnxHelper.new GeneralIECharacteristics(null, null,
					null, null, null, this.userDefinedA, this.userDefinedB, this.userDefinedC);
			ieDnxHelper.setGeneralIECharacteristics(generalIeCharacteristics);
		}

		// add cmsSystem if set
		if (this.cmsSystem != null) {
			CMS cms = ieDnxHelper.getCMS();
			if (cms == null) {
				cms = ieDnxHelper.new CMS();
			} else {
				// ist dies hier überhaupt jemals möglich?
				System.err.println("Dies wurde ausgeführt");
			}
			cms.setSystem(this.cmsSystem);
			cms.setRecordId(this.cmsRecordId);
			ieDnxHelper.setCMS(cms);
		}

		// add AR Policy on IE-level
		AccessRightsPolicy ar = ieDnxHelper.new AccessRightsPolicy(this.arPolicyId, null, this.arPolicyDescription);
		ieDnxHelper.setAccessRightsPolicy(ar);

		// set IE DNX Section
		ie.setIeDnx(ieDnxHelper.getDocument());

		// example for adding a logical Struct Map.
//		ie.generateStructMap(null, null, "Table of Contents");
		MetsDocument metsDoc = MetsDocument.Factory.parse(ie.toXML());
		Mets mets = metsDoc.getMets();
		for (REP rep : reps) {
			rep.addStructMap(mets);
		}

		// insert IE created in content directory
		String metsFileName = ziel.concat("content").concat(fs).concat("mets.xml");

		File ieXML = new File(metsFileName);
		XmlOptions opt = new XmlOptions();
		opt.setSavePrettyPrint();
		String xmlMetsContent = metsDoc.xmlText(opt);

		// Need to replace manually the namespace with Rosetta Mets schema in order to
		// pass validation against mets_rosetta.xsd
		String xmlRosettaMetsContent = xmlMetsContent.replaceAll(XML_SCHEMA, XML_SCHEMA_REPLACEMENT);
		xmlRosettaMetsContent = xmlMetsContent.replaceAll(METS_SCHEMA, ROSETTA_METS_SCHEMA);
		FileUtil.writeFile(ieXML, xmlRosettaMetsContent);
	}

	private String quote(String str) {
		if (str == null) {
			return "null";
		} else {
			return "'".concat(str).concat("'");
		}
	}

	public SIP printout() {
		System.out.println("SIP:");
		StringBuilder ud = new StringBuilder("UserDefined = [");
		ud.append(quote(this.userDefinedA));
		ud.append(", ");
		ud.append(quote(this.userDefinedB));
		ud.append(", ");
		ud.append(quote(this.userDefinedC));
		ud.append("]");
		System.out.println(ud);
		StringBuilder cms = new StringBuilder("CMS (");
		cms.append(quote(this.cmsSystem));
		cms.append(") = ");
		cms.append(quote(this.cmsRecordId));
		System.out.println(cms);
		StringBuilder mds = new StringBuilder("Metadata:");
		for (Metadata md : metadata) {
			mds.append("\n");
			mds.append(quote(md.xPathKey));
			mds.append(" = ");
			mds.append(quote(md.value));
		}
		System.out.println(mds);
		if (this.sourceMd != null) {
			StringBuilder smd = new StringBuilder("SourceMD (");
			smd.append(this.sourceMdType);
			if (this.otherSourceMdType != null) {
				smd.append(" -> ");
				smd.append(this.otherSourceMdType);
			}
			smd.append(")");
			smd.append("\n-----------------------------------\n");
			smd.append(this.sourceMd);
			smd.append("\n-----------------------------------");
			System.out.println(smd);
		}
		StringBuilder arp = new StringBuilder("AccessRights Policy = ");
		arp.append(this.arPolicyId);
		arp.append(" (");
		arp.append(this.arPolicyDescription);
		arp.append(")");
		System.out.println(arp);
		for (REP rep : reps) {
			rep.printout();
		}
		System.out.println("SIP Ende");
		return this;
	}

	private SIP loadFromMetsSip(File metsFile) throws Exception {
		XML metsXml = new XML(metsFile);
		NODE metsNode = metsXml.getNode().getChildren().filter("nameEquals", "mets:mets").get(0);
		NODELIST ieMetadata = metsNode.getChildren().filter("attrEquals", "mets:dmdSec", "ID", "ie-dmd").get(0)
				.getChildren().filter("nameEquals", "mets:mdWrap").get(0).getChildren()
				.filter("nameEquals", "mets:xmlData").get(0).getChildren().filter("nameEquals", "dc:record").get(0)
				.getChildren();
		for (NODE md : ieMetadata) {
			this.addMetadata(md.getXPathKey(), md.getTextContent());
		}
		NODE ieAmd = metsNode.getChildren().filter("attrEquals", "mets:amdSec", "ID", "ie-amd").get(0);
		NODELIST ieAmdTech = ieAmd.getChildren().filter("nameEquals", "mets:techMD").get(0).getChildren()
				.filter("nameEquals", "mets:mdWrap").get(0).getChildren().filter("nameEquals", "mets:xmlData").get(0)
				.getChildren().filter("nameEquals", "dnx").get(0).getChildren();
		NODELIST generalIECharacteristics = ieAmdTech.filter("attrEquals", "section", "id", "generalIECharacteristics");
		if (!generalIECharacteristics.empty()) {
			NODELIST generalIECharacteristicsKeys = generalIECharacteristics.get(0).getChildren()
					.filter("nameEquals", "record").get(0).getChildren();
			NODELIST uda = generalIECharacteristicsKeys.filter("attrEquals", "key", "id", "UserDefinedA");
			if (!uda.empty()) {
				this.setUserDefined("A", uda.get(0).getTextContent());
			}
			NODELIST udb = generalIECharacteristicsKeys.filter("attrEquals", "key", "id", "UserDefinedB");
			if (!udb.empty()) {
				this.setUserDefined("B", udb.get(0).getTextContent());
			}
			NODELIST udc = generalIECharacteristicsKeys.filter("attrEquals", "key", "id", "UserDefinedC");
			if (!udc.empty()) {
				this.setUserDefined("C", udc.get(0).getTextContent());
			}
		}
		NODELIST cms = ieAmdTech.filter("attrEquals", "section", "id", "CMS");
		if (!cms.empty()) {
			NODE record = cms.get(0).getChildren().filter("nameEquals", "record").get(0);
			String cmsSystem = record.getChildren().filter("attrEquals", "key", "id", "system").get(0).getTextContent();
			String cmsRecordId = record.getChildren().filter("attrEquals", "key", "id", "recordId").get(0)
					.getTextContent();
			this.setCms(cmsSystem, cmsRecordId);
		}
		NODELIST sourceMDs = ieAmd.getChildren().filter("attrStartsWith", "mets:sourceMD", "ID", "ie-amd-source");
		if (sourceMDs != null) {
			for (NODE sourceMD : sourceMDs) {
				String id = sourceMD.getAttributes().get("ID");
				if (!id.startsWith("ie-amd-source-DC")) {
					throw new Exception("SourceMD sollte DC sein: " + id);
				}
				NODE sourceMDContent = sourceMD.getChildren().filter("nameEquals", "mets:mdWrap").get(0).getChildren()
						.filter("nameEquals", "mets:xmlData").get(0).getChildren().filter("nameEquals", "dc:record")
						.get(0);
				this.setSourceMD(MDTYPE.DC, XmlObject.Factory.parse(sourceMDContent.getNode()), null);
			}
		}
		NODELIST accessRightsPolicy = ieAmd.getChildren().filter("nameEquals", "mets:rightsMD").get(0).getChildren()
				.filter("nameEquals", "mets:mdWrap").get(0).getChildren().filter("nameEquals", "mets:xmlData").get(0)
				.getChildren().filter("nameEquals", "dnx").get(0).getChildren()
				.filter("attrEquals", "section", "id", "accessRightsPolicy");
		if (accessRightsPolicy.size() > 0) {
			NODE arPolicy = accessRightsPolicy.get(0).getChildren().filter("nameEquals", "record").get(0);
			this.setARPolicy(
					arPolicy.getChildren().filter("attrEquals", "key", "id", "policyId").get(0).getTextContent(),
					arPolicy.getChildren().filter("attrEquals", "key", "id", "policyDescription").get(0)
							.getTextContent());
		}
		NODELIST repAmds = metsNode.getChildren().filter("attrStartsWith", "mets:amdSec", "ID", "rep");
		for (NODE repAmd : repAmds) {
			String preservationType = repAmd.getChildren().filter("nameEquals", "mets:techMD").get(0).getChildren()
					.filter("nameEquals", "mets:mdWrap").get(0).getChildren().filter("nameEquals", "mets:xmlData")
					.get(0).getChildren().filter("nameEquals", "dnx").get(0).getChildren()
					.filter("attrEquals", "section", "id", "generalRepCharacteristics").get(0).getChildren()
					.filter("nameEquals", "record").get(0).getChildren()
					.filter("attrEquals", "key", "id", "preservationType").get(0).getTextContent();
			REP rep = this.newREP(preservationType);
			String id = repAmd.getAttributes().get("ID");
			id = id.substring(0, id.indexOf("-"));
			rep.loadFromSip(metsFile, id);
		}
		return this;
	}

	public SIP loadFromSip(File ingestFile) throws Exception {
		Utilities.checkFile(ingestFile);
		String ingestFilePath = ingestFile.toString();
		String contentPath = ingestFilePath.substring(0, ingestFilePath.lastIndexOf(fs) + 1);
		Utilities.checkFolder(new File(contentPath + fs + "streams"));
		if (ingestFilePath.endsWith(".xml")) {
			return loadFromMetsSip(ingestFile);
		}
		throw new Exception(
				"IngestDatei Dateiendung nicht implementiert: " + ingestFilePath.substring(contentPath.length()));
	}

	public SIP loadFromRosetta(String rosettaInstance, String iePid, XML metsXml) throws Exception {
		if (metsXml == null) {
			metsXml = IEWS.getIE(iePid, rosettaInstance);
		}
//		System.out.println(XML.getStringFromDocument(metsXml.getDocument()));
		NODE metsNode = metsXml.getNode().getChildren().filter("nameEquals", "mets:mets").get(0);
		NODELIST ieMetadata = metsNode.getChildren().filter("attrEquals", "mets:dmdSec", "ID", "ie-dmd").get(0)
				.getChildren().filter("nameEquals", "mets:mdWrap").get(0).getChildren()
				.filter("nameEquals", "mets:xmlData").get(0).getChildren().filter("nameEquals", "dc:record").get(0)
				.getChildren();
		for (NODE md : ieMetadata) {
			this.addMetadata(md.getXPathKey(), md.getTextContent());
		}
		NODE ieAmd = metsNode.getChildren().filter("attrEquals", "mets:amdSec", "ID", "ie-amd").get(0);
		NODELIST ieAmdTech = ieAmd.getChildren().filter("nameEquals", "mets:techMD").get(0).getChildren()
				.filter("nameEquals", "mets:mdWrap").get(0).getChildren().filter("nameEquals", "mets:xmlData").get(0)
				.getChildren().filter("nameEquals", "dnx").get(0).getChildren();
		NODELIST generalIECharacteristics = ieAmdTech.filter("attrEquals", "section", "id", "generalIECharacteristics");
		if (!generalIECharacteristics.empty()) {
			NODELIST generalIECharacteristicsKeys = generalIECharacteristics.get(0).getChildren()
					.filter("nameEquals", "record").get(0).getChildren();
			NODELIST uda = generalIECharacteristicsKeys.filter("attrEquals", "key", "id", "UserDefinedA");
			if (!uda.empty()) {
				this.setUserDefined("A", uda.get(0).getTextContent());
			}
			NODELIST udb = generalIECharacteristicsKeys.filter("attrEquals", "key", "id", "UserDefinedB");
			if (!udb.empty()) {
				this.setUserDefined("B", udb.get(0).getTextContent());
			}
			NODELIST udc = generalIECharacteristicsKeys.filter("attrEquals", "key", "id", "UserDefinedC");
			if (!udc.empty()) {
				this.setUserDefined("C", udc.get(0).getTextContent());
			}
		}
		NODELIST cms = ieAmdTech.filter("attrEquals", "section", "id", "CMS");
		if (!cms.empty()) {
			NODE record = cms.get(0).getChildren().filter("nameEquals", "record").get(0);
			String cmsSystem = record.getChildren().filter("attrEquals", "key", "id", "system").get(0).getTextContent();
			String cmsRecordId = record.getChildren().filter("attrEquals", "key", "id", "recordId").get(0)
					.getTextContent();
			this.setCms(cmsSystem, cmsRecordId);
		}
		NODELIST sourceMDs = ieAmd.getChildren().filter("attrStartsWith", "mets:sourceMD", "ID", "ie-amd-source");
		if (sourceMDs != null) {
			for (NODE sourceMD : sourceMDs) {
				NODE sourceMDContent = sourceMD.getChildren().filter("nameEquals", "mets:mdWrap").get(0).getChildren()
						.filter("nameEquals", "mets:xmlData").get(0);
				this.setSourceMD(MDTYPE.DC, XmlObject.Factory.parse(sourceMDContent.getNode()), null);
			}
		}
		NODE arPolicy = ieAmd.getChildren().filter("nameEquals", "mets:rightsMD").get(0).getChildren()
				.filter("nameEquals", "mets:mdWrap").get(0).getChildren().filter("nameEquals", "mets:xmlData").get(0)
				.getChildren().filter("nameEquals", "dnx").get(0).getChildren()
				.filter("attrEquals", "section", "id", "accessRightsPolicy").get(0).getChildren()
				.filter("nameEquals", "record").get(0);
		this.setARPolicy(arPolicy.getChildren().filter("attrEquals", "key", "id", "policyId").get(0).getTextContent(),
				arPolicy.getChildren().filter("attrEquals", "key", "id", "policyDescription").get(0).getTextContent());
		NODELIST repAmds = metsNode.getChildren().filter("attrStartsWith", "mets:amdSec", "ID", "REP");
		for (NODE repAmd : repAmds) {
			String preservationType = repAmd.getChildren().filter("nameEquals", "mets:techMD").get(0).getChildren()
					.filter("nameEquals", "mets:mdWrap").get(0).getChildren().filter("nameEquals", "mets:xmlData")
					.get(0).getChildren().filter("nameEquals", "dnx").get(0).getChildren()
					.filter("attrEquals", "section", "id", "generalRepCharacteristics").get(0).getChildren()
					.filter("nameEquals", "record").get(0).getChildren()
					.filter("attrEquals", "key", "id", "preservationType").get(0).getTextContent();
			REP rep = this.newREP(preservationType);
			String id = repAmd.getAttributes().get("ID");
			id = id.substring(0, id.indexOf("-"));
			rep.loadFromRosetta(rosettaInstance, iePid, metsXml, id);
		}
		return this;
	}

	public void printoutDiff(SIP otherSip) throws Exception {
		if (!Objects.equals(this.userDefinedA, otherSip.userDefinedA)) {
			System.out.println("Hier: UserDefinedA " + this.userDefinedA);
			System.out.println("Dort: UserDefinedA " + otherSip.userDefinedA);
		}
		if (!Objects.equals(this.userDefinedB, otherSip.userDefinedB)) {
			System.out.println("Hier: userDefinedB " + this.userDefinedB);
			System.out.println("Dort: userDefinedB " + otherSip.userDefinedB);
		}
		if (!Objects.equals(this.userDefinedC, otherSip.userDefinedC)) {
			System.out.println("Hier: userDefinedC " + this.userDefinedC);
			System.out.println("Dort: userDefinedC " + otherSip.userDefinedC);
		}
		if (!Objects.equals(this.cmsSystem, otherSip.cmsSystem)
				|| !Objects.equals(this.cmsRecordId, otherSip.cmsRecordId)) {
			StringBuilder cms = new StringBuilder("Hier: CMS (");
			cms.append(quote(this.cmsSystem));
			cms.append(") = ");
			cms.append(quote(this.cmsRecordId));
			System.out.println(cms);
			StringBuilder cms2 = new StringBuilder("Dort: CMS (");
			cms2.append(quote(otherSip.cmsSystem));
			cms2.append(") = ");
			cms2.append(quote(otherSip.cmsRecordId));
			System.out.println(cms2);
		}
		StringBuilder mds = null;
		for (Metadata md : this.metadata) {
			boolean same = false;
			for (Metadata md2 : otherSip.metadata) {
				if (Objects.equals(md.xPathKey, md2.xPathKey) && Objects.equals(md.value, md2.value)) {
					same = true;
					break;
				}
			}
			if (!same) {
				if (mds == null) {
					mds = new StringBuilder("Metadaten Unterschiede:");
				}
				mds.append("\nHier: ");
				mds.append(quote(md.xPathKey));
				mds.append(" = ");
				mds.append(quote(md.value));
			}
		}
		for (Metadata md : otherSip.metadata) {
			boolean same = false;
			for (Metadata md2 : this.metadata) {
				if (Objects.equals(md.xPathKey, md2.xPathKey) && Objects.equals(md.value, md2.value)) {
					same = true;
					break;
				}
			}
			if (!same) {
				if (mds == null) {
					mds = new StringBuilder("Metadaten Unterschiede:");
				}
				mds.append("\nDort: ");
				mds.append(quote(md.xPathKey));
				mds.append(" = ");
				mds.append(quote(md.value));
			}
		}
		if (mds != null) {
			System.out.println(mds);
		}
		String smd = this.sourceMd == null ? null : this.sourceMd.toString();
		String smd2 = otherSip.sourceMd == null ? null : otherSip.sourceMd.toString();
		if (!Objects.equals(this.sourceMdType, otherSip.sourceMdType) || !Objects.equals(smd, smd2)) {
			StringBuilder smdp = new StringBuilder("Hier: SourceMD (");
			smdp.append(this.sourceMdType);
			if (this.otherSourceMdType != null) {
				smdp.append(" -> ");
				smdp.append(this.otherSourceMdType);
			}
			smdp.append(")");
			smdp.append("\n-----------------------------------\n");
			smdp.append(this.sourceMd);
			smdp.append("\n-----------------------------------\n");
			smdp.append("Dort: SourceMD (");
			smdp.append(otherSip.sourceMdType);
			if (otherSip.otherSourceMdType != null) {
				smdp.append(" -> ");
				smdp.append(otherSip.otherSourceMdType);
			}
			smdp.append(")");
			smdp.append("\n-----------------------------------\n");
			smdp.append(otherSip.sourceMd);
			smdp.append("\n-----------------------------------");
			System.out.println(smdp);
		}
		if (!Objects.equals(this.arPolicyId, otherSip.arPolicyId)
				|| !Objects.equals(this.arPolicyDescription, otherSip.arPolicyDescription)) {
			StringBuilder arp = new StringBuilder("Hier: AccessRights Policy = ");
			arp.append(this.arPolicyId);
			arp.append(" (");
			arp.append(this.arPolicyDescription);
			arp.append(")");
			arp.append("\n");
			arp.append("Dort: AccessRights Policy = ");
			arp.append(otherSip.arPolicyId);
			arp.append(" (");
			arp.append(otherSip.arPolicyDescription);
			arp.append(")");
			System.out.println(arp);
		}
		for (REP rep : reps) {
			rep.printoutDiff(otherSip);
		}
	}
}
