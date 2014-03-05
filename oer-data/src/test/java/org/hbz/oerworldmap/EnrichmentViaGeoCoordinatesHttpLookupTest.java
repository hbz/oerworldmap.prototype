/* Copyright 2014  Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.hbz.oerworldmap;

import java.io.File;
import java.net.URISyntaxException;

import org.culturegraph.mf.morph.Metamorph;
import org.culturegraph.mf.stream.converter.LiteralExtractor;
import org.culturegraph.mf.stream.pipe.StreamTee;
import org.culturegraph.mf.stream.source.DirReader;
import org.culturegraph.mf.stream.source.FileOpener;
import org.culturegraph.mf.stream.source.HttpOpener;
import org.junit.Test;
import org.lobid.lodmill.PipeEncodeTriples;
import org.lobid.lodmill.RdfModelFileWriter;
import org.lobid.lodmill.Stats;
import org.lobid.lodmill.Triples2RdfModel;

/**
 * @author Pascal Christoph (dr0i)
 * 
 */
@SuppressWarnings("javadoc")
public class EnrichmentViaGeoCoordinatesHttpLookupTest {
	private static final String targetPath = "tmp/";

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

	@Test
	public void testFlow() throws URISyntaxException {
		transformDataInDirectory("ocwc/small/geoList");
	}

	// @Test
	// public void testFlux() throws IOException, URISyntaxException,
	// RecognitionException {
	// final File fluxFile = new
	// File(Thread.currentThread().getContextClassLoader()
	// .getResource("xmlSplitterRdfWriter.flux").toURI());
	// Flux.main(new String[] { fluxFile.getAbsolutePath() });
	// FileUtils.deleteDirectory(new
	// File(EnrichmentViaGeoCoordinatesHttpLookupTest.targetPath));
	// }

	private void transformDataInDirectory(final String pathToDirectory)
			throws URISyntaxException {
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
		final Triples2RdfModel triple2modelGeo = new Triples2RdfModel();
		final RdfModelFileWriter geoWriter = EnrichmentViaGeoCoordinatesHttpLookupTest
				.createWriter(EnrichmentViaGeoCoordinatesHttpLookupTest.targetPath
						+ "/geo/" + pathToDirectory);
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
				.getContextClassLoader().getResource(pathToDirectory).toURI()))
				.getAbsolutePath());
		opener.closeStream();

	}
}
