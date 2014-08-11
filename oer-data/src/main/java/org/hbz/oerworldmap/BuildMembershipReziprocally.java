/* Copyright 2013, 2014 Pascal Christoph, hbz.
 * Licensed under the Eclipse Public License 1.0 */

package org.hbz.oerworldmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

import org.apache.commons.io.IOUtils;

/**
 * Adds some reziprocal data to consortia. Used by {@link Transform}. Not to be
 * confused with createOcwcDescription.sh which builds essential data describing
 * OCWC itself.
 * 
 * @author Pascal Christoph (dr0i)
 */
abstract class BuildMembershipReziprocally {
	static HashMap<String, String> concordance = new HashMap<String, String>();
	Map<String, StringBuilder> data = new HashMap<String, StringBuilder>();
	private static String memberOfPattern = "<http://schema.org/memberOf> <http://lobid.org/oer/";
	private static String PATH = Transform.TARGET_PATH + Transform.OCWC_PATH
			+ Transform.ORGANIZATION_ID;

	public static void main(String... args) throws FileNotFoundException, IOException {

		concordance.put("b87cd10f-c537-499c-803c-c15bdb76a15c", "ocwc408");
		concordance.put("0d3c90a8-20fd-412e-8ea8-8640d4571c09", "ocwc215");
		concordance.put("ba48e2ff-6851-4c12-9a37-d4fa3594fbe6", "ocwc577");
		concordance.put("ee479855-3c03-47c4-b256-1b35f78da110", "ocwc370");
		concordance.put("03d57204-3215-4a7e-9132-a3411a8f4ee0", "ocwc262");
		concordance.put("95308f8c-bea0-46d0-8b61-bf39566c6e22", "ocwc213");
		concordance.put("cd613b01-a235-4a92-83fc-014728f1da66", "ocwc214");
		TripleCrawler crawler = new TripleCrawler();
		if (args.length >= 1) {
			PATH = args[0];
		}
		Files.walkFileTree(Paths.get(PATH), crawler);
		process(crawler.data);
	}

	private static final class TripleCrawler extends SimpleFileVisitor<Path> {
		Map<String, StringBuilder> data = new HashMap<String, StringBuilder>();

		@Override
		public FileVisitResult visitFile(Path path, BasicFileAttributes attr) throws IOException {
			if (path.toString().endsWith(".nt")) {
				Scanner scanner = new Scanner(path.toFile());
				collectContent(scanner);
			}
			return FileVisitResult.CONTINUE;
		}

		private void collectContent(Scanner scanner) {
			while (scanner.hasNextLine()) {
				String statement = scanner.nextLine();
				int indexStart = 0;
				if ((indexStart = statement.indexOf(memberOfPattern)) != -1) {
					String isMemberOfUuid = statement.substring(
							indexStart + memberOfPattern.length(), statement.lastIndexOf("#"));
					if (concordance.containsKey(isMemberOfUuid)) {
						String fname = concordance.get(isMemberOfUuid);
						if (!data.containsKey(fname))
							data.put(fname, new StringBuilder());
						if (!data.get(fname).toString().contains(isMemberOfUuid))
							data.get(fname)
									.append("\n")
									.append("<http://lobid.org/oer/" + isMemberOfUuid + "#!>"
											+ " <http://schema.org/member> "
											+ statement.substring(0, statement.indexOf(" ")) + " .");
					}
				}
			}
		}
	}

	private static void process(Map<String, StringBuilder> map) {
		for (Entry<String, StringBuilder> e : map.entrySet()) {
			File file = new File(PATH + "/" + e.getKey() + ".nt");
			try {
				final Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
				IOUtils.write(e.getValue() + "\n", writer);
				writer.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}
}