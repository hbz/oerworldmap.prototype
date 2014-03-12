/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers.oer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.IndicesAdminClient;

import play.Play;

import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JSONUtils;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

/**
 * Convert N-Triples to JSON-LD and index it in Elasticsearch.
 * 
 * @author Fabian Steeg (fsteeg)
 */
public class NtToEs {

	static final String CONFIG = "public/data/index-config.json";
	static final String CONTEXT = "public/data/context.json";
	static final String TYPE = "oer-type";
	static final String INDEX = "oer-index";

	public static void main(String... args) throws ElasticSearchException,
			FileNotFoundException, IOException {
		if (args.length != 1) {
			System.err
					.println("Pass a single arg: the root directory to crawl. "
							+ "Will recursively gather all content from *.nt "
							+ "files with identical names, convert these to "
							+ "compact JSON-LD and index it in ES.");
			System.exit(-1);
		}
		TripleCrawler crawler = new TripleCrawler();
		Files.walkFileTree(Paths.get(args[0]), crawler);
		createIndex(CharStreams.toString(new FileReader(CONFIG)));
		process(crawler.data);
	}

	private static final class TripleCrawler extends SimpleFileVisitor<Path> {
		Map<String, StringBuilder> data = new HashMap<String, StringBuilder>();

		@Override
		public FileVisitResult visitFile(Path path, BasicFileAttributes attr)
				throws IOException {
			if (path.toString().endsWith(".nt")) {
				String content = com.google.common.io.Files.toString(
						path.toFile(), Charsets.UTF_8);
				collectContent(path.getFileName().toString(), content);
			}
			return FileVisitResult.CONTINUE;
		}

		private void collectContent(String name, String content) {
			if (!data.containsKey(name))
				data.put(name, new StringBuilder());
			data.get(name).append("\n").append(content);
		}
	}

	private static void createIndex(String config) {
		IndicesAdminClient admin = Application.client.admin().indices();
		if (!admin.prepareExists(INDEX).execute().actionGet().isExists())
			admin.prepareCreate(INDEX).setSource(config).execute().actionGet();
	}

	private static void process(Map<String, StringBuilder> map) {
		for (Entry<String, StringBuilder> e : map.entrySet()) {
			try {
				indexData(uuid(e.getKey()),
						rdfToJsonLd(e.getValue().toString(), Lang.NTRIPLES));
			} catch (Exception x) {
				System.err.printf("Could not process file %s due to %s\n",
						e.getKey(), x.getMessage());
				x.printStackTrace();
			}
		}
	}

	private static void indexData(String id, String data) {
		IndexResponse r = Application.client.prepareIndex(INDEX, TYPE, id)
				.setSource(data).execute().actionGet();
		System.out.printf(
				"Indexed into index %s, type %s, id %s, version %s: %s\n",
				r.getIndex(), r.getType(), r.getId(), r.getVersion(), data);
	}

	private static String uuid(String id) {
		return UUID.nameUUIDFromBytes(id.getBytes()).toString();
	}

	static String rdfToJsonLd(String data, Lang lang) {
		StringWriter stringWriter = new StringWriter();
		InputStream in = new ByteArrayInputStream(data.getBytes(Charsets.UTF_8));
		Model model = ModelFactory.createDefaultModel();
		RDFDataMgr.read(model, in, lang);
		RDFDataMgr.write(stringWriter, model, Lang.JSONLD);
		return compact(stringWriter.toString());
	}

	private static String compact(String json) {
		try {
			Object contextJson = JSONUtils.fromURL(context());
			JsonLdOptions options = new JsonLdOptions();
			options.setCompactArrays(false); // ES needs consistent data
			Map<String, Object> compact = JsonLdProcessor.compact(
					JSONUtils.fromString(json), contextJson, options);
			return JSONUtils.toString(compact);
		} catch (IOException | JsonLdError e) {
			throw new IllegalStateException("Could not compact JSON-LD: \n"
					+ json, e);
		}
	}

	public static URL context() throws MalformedURLException {
		final String path = "public/data";
		final String file = "context.json";
		if (new File(path, file).exists())
			return new File(path, file).toURI().toURL();
		return Play.application().resource("/" + path + "/" + file);
	}
}