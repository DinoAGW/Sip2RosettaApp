package de.zbmed.utilities;

import java.io.File;

public class Utilities {
	public static void checkFolder(File folder) throws Exception {
		if (!folder.exists()) {
			throw new Exception("Ordner " + folder + " existiert nicht");
		}
		if (!folder.isDirectory()) {
			throw new Exception("Argument " + folder + " ist kein Ordner");
		}
	}

	public static void checkFile(File file) throws Exception {
		if (!file.exists()) {
			throw new Exception("Datei " + file + " existiert nicht");
		}
		if (!file.isFile()) {
			throw new Exception("Argument " + file + " ist keine Datei");
		}
	}
	
	public static File tempFile() throws Exception {
		return File.createTempFile("s2ra", null);
	}
}
