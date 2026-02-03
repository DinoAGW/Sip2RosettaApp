import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.xmlbeans.XmlObject;

import de.zbmed.intern.REP;
import de.zbmed.intern.SIP;
import de.zbmed.rosetta.IEWS;
import de.zbmed.rosetta.SRU;
import de.zbmed.utilities.XML;
import gov.loc.mets.MdSecType.MdWrap.MDTYPE;

public class Examples {
	private static final String fs = System.getProperty("file.separator");
	public static final String home = System.getProperty("user.home");

	private static String testDatei = "file:///bin" + fs + "Test.txt";
	private static String sourceMDQuelle = "bin" + fs + "SourceMD.xml";
	private static XmlObject sourceMD = createSourceMd();

	private static XmlObject createSourceMd() {
		try {
			return XmlObject.Factory.parse(new File(sourceMDQuelle));
		} catch (Exception e) {
			System.err.println("Fehler beim parsen der SourceMD.");
			return null;
		}
	}

	public static void deleteExamples() throws Exception {
		File sip = new File("bin" + fs + "minimalSip");
		if (sip.exists()) {
//			FileUtils.deleteDirectory(sip);
		}

		sip = new File("bin" + fs + "maximalSip");
		if (sip.exists()) {
			FileUtils.deleteDirectory(sip);
		}
	}

	public static void createExamples() throws Exception {
		SIP sip = new SIP(); // Erstelle neue SIP
		sip.addMetadata("dc:title", "Titel"); // Füge ein Metadatum hinzu
		REP rep1 = sip.newREP(null); // Erstelle Repräsentation. null bedeutet PRESERVATION_MASTER
		rep1.newFile(testDatei, null); // Füge eine Datei hinzu mit null bedeutet in Hauptverzeichnis der SIP und null
										// bedeutet erkenne mimeType selbst
//		sip.printout();
//		sip.saveAllToSip("bin" + fs + "minimalSip"); //Prüfe ob SIP vollständig ist und liefere sie an die Stelle aus

		sip.setRepFolderMode(true);
		sip.addMetadata("dcterms:URI", "uri1");
		sip.addMetadata("dc:identifier@dcterms:URI", "uri2");
		sip.setUserDefined("A", "Ich bins"); // Füge UserDefinedA hinzu
		sip.setCms("HBZ01", "HT020566828"); // Füge CMS hinzu
		sip.setSourceMD(MDTYPE.DC, sourceMD, null); // Füge SourceMD vom Type DC hinzu. Bei Type OTHER wird das dritte
													// Argument benötigt um den Type zu spezifizieren.
		sip.setARPolicy("433120", "ZB MED_STAFF only"); // Setze ARPolicy auf SIP-Ebene
		rep1.newFile(testDatei, "1".concat(fs)).setARPolicy("433120", "ZB MED_STAFF only"); // Füge Datei in den
																							// Unterordner 1\ ein und
																							// setze ARPolicy auf
																							// File-Ebene
		REP rep2 = sip.newREP("MODIFIED_MASTER").setLabel("Andere Representation"); // Füge neue Repräsentation hinzu
		/*
		 * Speichere testDatei unter einen anderen Namen in den Unterordner 2\ und
		 * notiere md5Summe dazu (wird beim deploy überprüft, ob die stimmt)
		 */
		rep2.newFile(testDatei, "2".concat(fs).concat("Test2.txt")).setMd5sum("dfcd625d3138ed3d84e077161d579617");
		rep2.newFile(testDatei, "Test3.txt").addMetadata("dc:title", "DateiTitel"); // Füge dc-Metadatum auf File-Ebene
																					// hinzu
		rep2.newFile(testDatei, "2".concat(fs).concat("a").concat(fs)).setLabel("Eine Datei"); // Füge eine Datei mit
																								// alternativem Label
																								// hinzu
//		sip.printout();
		sip.saveAllToSip("bin" + fs + "maximalSip");
	}

	public static void loadExamples() throws Exception {
		SIP minimalSip = new SIP();
		minimalSip.loadFromSip(new File("bin" + fs + "minimalSip" + fs + "content" + fs + "mets.xml"));
//		minimalSip.printout();
		SIP maximalSip = new SIP();
		maximalSip.loadFromSip(new File("bin" + fs + "maximalSip" + fs + "content" + fs + "mets.xml"));
//		maximalSip.printout();
	}

	public static void compareExampleWithReload() throws Exception {
		SIP maximalSip = new SIP(); // Erstelle neue SIP
		maximalSip.addMetadata("dc:title", "Titel"); // Füge ein Metadatum hinzu
		REP rep1 = maximalSip.newREP(null); // Erstelle Repräsentation. null bedeutet PRESERVATION_MASTER
		rep1.newFile(testDatei, null); // Füge eine Datei hinzu mit null bedeutet in Hauptverzeichnis der SIP und null
										// bedeutet erkenne mimeType selbst
		maximalSip.setRepFolderMode(true);
		maximalSip.addMetadata("dcterms:URI", "uri1");
		maximalSip.addMetadata("dc:identifier@dcterms:URI", "uri2");
		maximalSip.setUserDefined("A", "Ich bins"); // Füge UserDefinedA hinzu
		maximalSip.setCms("HBZ01", "HT020566828"); // Füge CMS hinzu
		maximalSip.setSourceMD(MDTYPE.DC, sourceMD, null); // Füge SourceMD vom Type DC hinzu. Bei Type OTHER wird das
															// dritte
		// Argument benötigt um den Type zu spezifizieren.
		maximalSip.setARPolicy("433120", "ZB MED_STAFF only"); // Setze ARPolicy auf SIP-Ebene
		rep1.newFile(testDatei, "1".concat(fs)).setARPolicy("433120", "ZB MED_STAFF only"); // Füge Datei in den
																							// Unterordner 1\ ein und
																							// setze ARPolicy auf
																							// File-Ebene
		REP rep2 = maximalSip.newREP("MODIFIED_MASTER").setLabel("Andere Representation"); // Füge neue Repräsentation
																							// hinzu
		/*
		 * Speichere testDatei unter einen anderen Namen in den Unterordner 2\ und
		 * notiere md5Summe dazu (wird beim deploy überprüft, ob die stimmt)
		 */
		rep2.newFile(testDatei, "2".concat(fs).concat("Test2.txt")).setMd5sum("dfcd625d3138ed3d84e077161d579617");
		rep2.newFile(testDatei, "Test3.txt").addMetadata("dc:title", "DateiTitel"); // Füge dc-Metadatum auf File-Ebene
																					// hinzu
		rep2.newFile(testDatei, "2".concat(fs).concat("a").concat(fs)).setLabel("Eine Datei"); // Füge eine Datei mit
																								// alternativem Label
																								// hinzu

		SIP sipHD = new SIP().loadFromSip(new File("bin" + fs + "minimalSip" + fs + "content" + fs + "mets.xml"));

		sipHD.newREP("MODIFIED_MASTER").newFile(testDatei, "Test4.txt");
		maximalSip.printoutDiff(sipHD);
	}

	public static void loadRosettaExample() throws Exception {
		String rosettaInstance = "prod";
		String userDefinedA = "GMSKON_24dgpp10";
		String iePid = SRU.getIePidToUserDefinedA(rosettaInstance, userDefinedA);
		System.out.println(iePid);
		XML mets = IEWS.getIE(iePid, rosettaInstance);
		System.out.println(XML.getStringFromDocument(mets.getDocument()));
		SIP rosettaSip = new SIP();
		rosettaSip.loadFromRosetta(rosettaInstance, iePid, null);
		rosettaSip.printout();
	}

	public static void compareRosettaExampleWithSip() throws Exception {
		String rosettaInstance = "prod";
		String userDefinedA = "GMSKON_24dgpp10";
		String iePid = SRU.getIePidToUserDefinedA(rosettaInstance, userDefinedA);
		SIP localSip = new SIP().loadFromSip(new File(
				home + fs + "workspace" + fs + "versuchssip_dgpp2024_24dgpp10" + fs + "content" + fs + "ie1.xml"));
		SIP rosettaSip = new SIP().loadFromRosetta(rosettaInstance, iePid, null);
//		localSip.printout();
//		rosettaSip.printout();
		localSip.printoutDiff(rosettaSip);
	}

	/*
	 * update eine IE in Rosetta derart, dass sie wie eine SIP von der Festplatte
	 * aussieht
	 */
	public static void makeOneToOther(String rosettaInstance) throws Exception {
		int version = 1;
		String sipPath = "bin" + fs + "vorbildSip" + version;
		SIP sip = new SIP();
		String userDefinedA = "uda" + version;
		sip.setUserDefined("A", userDefinedA);
		sip.addMetadata("dc:title", "Titel");
		REP rep1 = sip.newREP(null);
		rep1.newFile(testDatei, null);
		sip.printout();
		sip.saveAllToSip(sipPath);
		File ingestFile = new File(sipPath + fs + "ie.xml");
		System.out.println("Vorher:");
		String devIePid = SRU.getIePidToUserDefinedA(rosettaInstance, userDefinedA);
		SIP devIeSip = new SIP().loadFromRosetta(rosettaInstance, devIePid, null).printout();
		System.out.println("Vorbild:");
		SIP vorbildSip = new SIP().loadFromSip(ingestFile).printout();
		System.out.println("Differenz:");
		devIeSip.printoutDiff(vorbildSip);

	}

	public static void main(String[] args) throws Exception {
//		deleteExamples();
//		createExamples();
//		loadExamples();
//		compareExampleWithReload();
//		loadRosettaExample();
//		compareRosettaExampleWithSip();
//		makeOneToOther(new File("bin" + fs + "minimalSip" + fs + "content" + fs + "mets.xml"), "dev", "3103");
		System.out.println("Example Ende");
	}
}
