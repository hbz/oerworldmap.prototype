package controllers.oer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Scanner;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import com.fasterxml.jackson.core.JsonParseException;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JSONUtils;
import com.google.common.collect.ImmutableMap;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

public class Geonames {

	private static final String DATA = "public/data/countriesGeonamesId.txt";
	private static final String WS = "http://www.geonames.org/%s/about.rdf";
	private static final String ID = "http://sws.geonames.org/%s/";
	private static final String TYPE = "geonames-type";

	public static void main(String[] args) throws InterruptedException,
			IOException, JsonLdError {
		try (Scanner scanner = new Scanner(new File(DATA))) {
			while (scanner.hasNextLine()) {
				String geonamesId = scanner.nextLine().trim();
				String jsonLd = convertToJsonLd(geonamesId);
				index(String.format(ID, geonamesId), jsonLd);
				Thread.sleep(1000);
			}
		}
	}

	private static String convertToJsonLd(String geonamesId)
			throws JsonLdError, JsonParseException, IOException {
		String geonamesRdfUrl = String.format(WS, geonamesId);
		StringWriter stringWriter = new StringWriter();
		Model model = ModelFactory.createDefaultModel();
		RDFDataMgr.read(model, geonamesRdfUrl, Lang.RDFXML);
		RDFDataMgr.write(stringWriter, model, Lang.JSONLD);
		List<Object> expanded = JsonLdProcessor.expand(JSONUtils
				.fromString(stringWriter.toString()));
		return JSONUtils.toString(ImmutableMap.of("@graph", expanded));
	}

	private static void index(String geonamesId, String jsonLd)
			throws FileNotFoundException, IOException {
		NtToEs.createIndex(NtToEs.config(), Application.DATA_INDEX);
		NtToEs.indexData(geonamesId, jsonLd, Application.DATA_INDEX, TYPE, null);
	}
}
