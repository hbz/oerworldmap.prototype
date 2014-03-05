/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package org.hbz.oerworldmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.UUID;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

import com.google.common.io.CharStreams;
import com.hp.hpl.jena.rdf.model.Model;

/**
 * Convert N-Triples to JSON-LD and index it in Elasticsearch.
 * 
 * @author Fabian Steeg (fsteeg)
 */
public class NtToEs {

	static final String DATA = "src/main/resources/ocwc/ocw-example.nt";
	static final String CONFIG = "src/main/resources/index-config.json";
	static final String TYPE = "oer-type";
	static final String INDEX = "oer-index";
	static final String ID = UUID.nameUUIDFromBytes(DATA.getBytes()).toString();
	static final Client CLIENT = new TransportClient(ImmutableSettings
			.settingsBuilder().put("cluster.name", "quaoar").build())
			.addTransportAddress(new InetSocketTransportAddress(
					"193.30.112.170", 9300));

	public static void main(String[] args) throws ElasticSearchException,
			FileNotFoundException, IOException {
		createIndex(CharStreams.toString(new FileReader(CONFIG)));
		indexData(ntToJsonLd(DATA));
	}

	private static void indexData(String jsonLdString) {
		IndexResponse r = CLIENT.prepareIndex(INDEX, TYPE, ID)
				.setSource(jsonLdString).execute().actionGet();
		System.out.printf(
				"Indexed into index %s, type %s, id %s, version %s\n",
				r.getIndex(), r.getType(), r.getId(), r.getVersion());
	}

	private static void createIndex(String indexConfig) {
		IndicesAdminClient admin = CLIENT.admin().indices();
		if (!admin.prepareExists(INDEX).execute().actionGet().isExists())
			admin.prepareCreate(INDEX).setSource(indexConfig).execute()
					.actionGet();
	}

	private static String ntToJsonLd(String ntFile) {
		Model model = RDFDataMgr.loadModel(new File(ntFile).toURI().toString());
		StringWriter stringWriter = new StringWriter();
		RDFDataMgr.write(stringWriter, model, Lang.JSONLD);
		return stringWriter.toString();
	}

}
