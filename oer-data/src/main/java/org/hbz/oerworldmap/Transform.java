package org.hbz.oerworldmap;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.commons.io.FileUtils;
import org.culturegraph.mf.morph.Metamorph;
import org.culturegraph.mf.stream.converter.LiteralExtractor;
import org.culturegraph.mf.stream.pipe.StreamTee;
import org.culturegraph.mf.stream.source.DirReader;
import org.culturegraph.mf.stream.source.FileOpener;
import org.culturegraph.mf.stream.source.HttpOpener;
import org.lobid.lodmill.PipeEncodeTriples;
import org.lobid.lodmill.RdfModelFileWriter;
import org.lobid.lodmill.Stats;
import org.lobid.lodmill.Triples2RdfModel;

import com.google.common.io.Files;

/**
 * Run as Java application to transform the full data. <br/>
 * Static methods are also used by the tests.
 */
public class Transform {

	private static final String GEO_LIST = "geoList";
	private static final String ORGANIZATION_ID = "organizationId";
	private static final String CONSORTIUM_MEMBERS = "consortiumMembers";
	private static final String OCWC_PATH = "ocwc/";
	private static final String TARGET_PATH = "tmp/";

	public static void main(String[] args) throws URISyntaxException,
			IOException {
		FileUtils.deleteQuietly(new File(TARGET_PATH));
		dataInDirectory(TARGET_PATH, OCWC_PATH + CONSORTIUM_MEMBERS);
		dataInDirectory(TARGET_PATH, OCWC_PATH + ORGANIZATION_ID);
		dataInDirectory(TARGET_PATH, OCWC_PATH + GEO_LIST);
		geoWithHttpLookup("ocwc/geoList", "ocwc/geo", "tmp/");
		Files.copy(new File(
				"doc/scripts/additionalDataOcwcItself.ntriple.template"),
				new File(TARGET_PATH + OCWC_PATH, "ocwc.nt"));
	}

	static void dataInDirectory(String targetPath, final String pathToDirectory)
			throws URISyntaxException {
		final DirReader dirReader = new DirReader();
		final FileOpener opener = new FileOpener();
		final JsonDecoder jsonDecoder = new JsonDecoder();
		final Metamorph morph = new Metamorph(Thread.currentThread()
				.getContextClassLoader()
				.getResource("morph-ocwConsortiumMembers-to-rdf.xml").getFile());
		final PipeEncodeTriples encoder = new PipeEncodeTriples();
		encoder.setStoreUrnAsUri("true");
		final Triples2RdfModel triple2model = new Triples2RdfModel();
		triple2model.setInput("N-TRIPLE");
		final RdfModelFileWriter writer = createOerWriter(targetPath
				+ pathToDirectory);
		final StreamTee streamTee = new StreamTee();
		final Stats stats = new Stats();
		streamTee.addReceiver(stats);
		streamTee.addReceiver(encoder);
		encoder.setReceiver(triple2model).setReceiver(writer);
		opener.setReceiver(jsonDecoder);
		jsonDecoder.setReceiver(morph).setReceiver(streamTee);
		dirReader.setReceiver(opener);
		dirReader.process((new File(Thread.currentThread()
				.getContextClassLoader().getResource(pathToDirectory).toURI()))
				.getAbsolutePath());
		opener.closeStream();

	}

	private static RdfModelFileWriter createOerWriter(final String PATH) {
		final RdfModelFileWriter writer = new RdfModelFileWriter();
		writer.setProperty("http://purl.org/dc/elements/1.1/identifier");
		writer.setEndIndex(1);
		writer.setStartIndex(0);
		writer.setFileSuffix("nt");
		writer.setSerialization("N-TRIPLE");
		writer.setTarget(PATH);
		return writer;
	}

	static void geoWithHttpLookup(String ocwcGeoSource, String ocwcGeoTarget,
			String targetPath) throws URISyntaxException {
		final DirReader dirReader = new DirReader();
		final FileOpener opener = new FileOpener();
		final JsonDecoder jsonDecoder = new JsonDecoder();
		final JsonDecoder jsonDecoder1 = new JsonDecoder();
		final Metamorph morphGeo = new Metamorph(Thread.currentThread()
				.getContextClassLoader()
				.getResource("morph-ocwConsortiumMembers-buildGeoOsmUrl.xml")
				.getFile());
		final Metamorph morphOSM = new Metamorph(Thread.currentThread()
				.getContextClassLoader()
				.getResource("morph-ocwConsortiumMembers-osm.xml").getFile());
		final PipeEncodeTriples geoEncoder = new PipeEncodeTriples();
		geoEncoder.setStoreUrnAsUri("true");
		final Triples2RdfModel triple2modelGeo = new Triples2RdfModel();
		final RdfModelFileWriter geoWriter = createWriter(targetPath
				+ ocwcGeoTarget);
		final StreamTee streamTee = new StreamTee();
		final Stats stats = new Stats();
		streamTee.addReceiver(stats);
		final HttpOpener httpOpener = new HttpOpener();
		httpOpener.setReceiver(jsonDecoder1);
		jsonDecoder1.setReceiver(morphOSM);
		morphOSM.setReceiver(geoEncoder);
		geoEncoder.setReceiver(triple2modelGeo);
		triple2modelGeo.setReceiver(geoWriter);
		final LiteralExtractor literalExtractor = new LiteralExtractor();
		streamTee.addReceiver(literalExtractor);//
		literalExtractor.setReceiver(httpOpener);
		opener.setReceiver(jsonDecoder);
		jsonDecoder.setReceiver(morphGeo).setReceiver(streamTee);
		dirReader.setReceiver(opener);
		dirReader.process((new File(Thread.currentThread()
				.getContextClassLoader().getResource(ocwcGeoSource).toURI()))
				.getAbsolutePath());
		opener.closeStream();
	}

	private static RdfModelFileWriter createWriter(final String PATH) {
		final RdfModelFileWriter writer = new RdfModelFileWriter();
		writer.setProperty("http://purl.org/dc/elements/1.1/identifier");
		writer.setEndIndex(1);
		writer.setStartIndex(0);
		writer.setFileSuffix("nt");
		writer.setSerialization("N-TRIPLE");
		writer.setTarget(PATH);
		return writer;
	}

}
