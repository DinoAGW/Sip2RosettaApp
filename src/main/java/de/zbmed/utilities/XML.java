package de.zbmed.utilities;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

public class XML {
	public class NODE {
		Node node;

		NODE(Node node) {
			this.node = node;
		}

		public Node getNode() {
			return this.node;
		}

		public NODELIST getChildren() {
			NODELIST ret = new NODELIST();
			NodeList nl = this.node.getChildNodes();
			for (int i = 0; i < nl.getLength(); ++i) {
				ret.add(new NODE(nl.item(i)));
			}
			return ret;
		}

		public Map<String, String> getAttributes() {
			Map<String, String> ret = new HashMap<>();
			NamedNodeMap nnm = node.getAttributes();
			for (int i = 0; i < nnm.getLength(); ++i) {
				Node node = nnm.item(i);
				ret.put(node.getNodeName(), node.getNodeValue());
			}
			return ret;
		}

		public String getNodeName() {
			return this.node.getNodeName();
		}

		public String getXPathKey() throws Exception {
			NamedNodeMap nnm = this.node.getAttributes();
			if (nnm == null || nnm.getLength() == 0) {
				return this.node.getNodeName();
			} else {
				StringBuilder ret = new StringBuilder(this.node.getNodeName());
				Node attribute = nnm.item(0);
				if (attribute.getNodeName().equals("xsi:type")) {
					ret.append("@");
					ret.append(attribute.getNodeValue());
				} else if (!attribute.getNodeName().startsWith("xmlns:")) {
					throw new Exception("Kein Attribut ungleich xsi:type bei Node '" + this.getNodeName()
							+ "' erwartet: '" + attribute.getNodeName() + "'");
				}
				return ret.toString();
			}
		}

		public String getTextContent() {
			return this.node.getTextContent();
		}
	}

	public class NODELIST extends Stack<NODE> {
		private static final long serialVersionUID = -7755948019680811821L;

		public NODELIST filter(String type, String... args) throws Exception {
			NODELIST ret = new NODELIST();
			switch (type) {
			case "nameEquals":
				if (args.length != 1)
					throw new Exception("Es müssten 2 Argumente sein");
				for (NODE node : this) {
					if (node.getNodeName().contentEquals(args[0])) {
						ret.add(node);
					}
				}
				break;
			case "attrEquals":
				if (args.length != 3)
					throw new Exception("Es müssten 3 Argumente sein");
				for (NODE node : this) {
					if (node.getNodeName().contentEquals(args[0])) {
						String value = node.getAttributes().get(args[1]);
						if (value != null && value.contentEquals(args[2])) {
							ret.add(node);
						}
					}
				}
				break;
			case "attrStartsWith":
				if (args.length != 3)
					throw new Exception("Es müssten 3 Argumente sein");
				for (NODE node : this) {
					if (node.getNodeName().contentEquals(args[0])) {
						String value = node.getAttributes().get(args[1]);
						if (value != null && value.startsWith(args[2])) {
							ret.add(node);
						}
					}
				}
				break;
			default:
				throw new Exception("Kenne type nicht: " + type);
			}
			return ret;
		}

		public void printNames() throws Exception {
			for (NODE node : this) {
				System.out.println(node.getXPathKey());
			}
		}
	}

	private Document metsDoc;

	public Document getDocument() {
		return this.metsDoc;
	}

	public XML(File metsFile) throws Exception {
		if (!metsFile.exists()) {
			throw new Exception("Datei " + metsFile + " existiert nicht");
		}
		if (!metsFile.isFile()) {
			throw new Exception("Argument " + metsFile + " ist keine Datei");
		}
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);// wird gebraucht damit der Namespace nicht verschludert wird
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document metsDoc = builder.parse(metsFile);
		metsDoc.setXmlStandalone(true);
		sanitize(metsDoc);
		this.metsDoc = metsDoc;
	}

	public XML(String xmlString) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);// wird gebraucht damit der Namespace nicht verschludert wird
		DocumentBuilder builder = factory.newDocumentBuilder();
		InputSource is = new InputSource(new StringReader(xmlString));
		Document xmlDoc = builder.parse(is);
		xmlDoc.setXmlStandalone(true);
		sanitize(xmlDoc);
		this.metsDoc = xmlDoc;
	}

	private void sanitize(Node node) {
		NodeList nl = node.getChildNodes();
		Node child = nl.item(0);
		if (nl.getLength() == 1 && child.getNodeType() == Node.TEXT_NODE) {
			String text = child.getTextContent();
			node.removeChild(child);
			node.setTextContent(text);
		} else {
			for (int i = nl.getLength() - 1; i >= 0; --i) {
				child = nl.item(i);
				if (child.getNodeType() == Node.TEXT_NODE) {
					node.removeChild(child);
				} else {
					sanitize(child);
				}
			}
		}
	}

	public static String getStringFromDocument(Document doc) throws Exception {
		DOMSource domSource = new DOMSource(doc);
		StringWriter writer = new StringWriter();
		StreamResult result = new StreamResult(writer);
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		transformer.transform(domSource, result);
		String ret = writer.toString();
//		int stelle = ret.indexOf(">") + 1;
//		ret = ret.substring(0, stelle) + "\n  " + ret.substring(stelle);
//		ret = ret.replaceAll("<dnx xmlns=\"http://www.exlibrisgroup.com/dps/dnx\" version=\"5.0\">", "<dnx version=\"5.0\" xmlns=\"http://www.exlibrisgroup.com/dps/dnx\">");
		return ret;
	}

	public NODE getNode() {
		return new NODE(this.metsDoc);
	}
}
