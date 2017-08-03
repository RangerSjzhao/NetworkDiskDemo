package Util;

import java.io.File;
import java.io.IOException;

public class FileUtil {

	public static void mkdir(String path) {
		File f = new File(path);
		if (!f.exists()) {
			f.mkdirs();
		}
	}

	public static void createNewFile(String path) throws IOException {
		File f = new File(path);
		if (!f.exists()) {
			f.createNewFile();
		}
	}
}
