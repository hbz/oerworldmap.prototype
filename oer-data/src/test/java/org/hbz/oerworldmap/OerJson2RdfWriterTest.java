/* Copyright 2014  Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.hbz.oerworldmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;

import org.antlr.runtime.RecognitionException;
import org.apache.commons.io.FileUtils;
import org.culturegraph.mf.Flux;
import org.culturegraph.mf.morph.Metamorph;
import org.culturegraph.mf.stream.pipe.StreamTee;
import org.culturegraph.mf.stream.source.DirReader;
import org.culturegraph.mf.stream.source.FileOpener;
import org.junit.Test;
import org.lobid.lodmill.AbstractIngestTests;
import org.lobid.lodmill.PipeEncodeTriples;
import org.lobid.lodmill.RdfModelFileWriter;
import org.lobid.lodmill.Stats;
import org.lobid.lodmill.Triples2RdfModel;

/**
 * @author Pascal Christoph (dr0i)
 * 
 */
@SuppressWarnings("javadoc")
public class OerJson2RdfWriterTest {
	private final String OCWC_PATH = "ocwc/";
	private final String TARGET_PATH = "tmp/";
	private final String TEST_FILENAME = "ocwcTestResult.nt";

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
		transformDataInDirectory(OCWC_PATH + "small/consortiumMembers");
		transformDataInDirectory(OCWC_PATH + "small/organizationId");
		transformDataInDirectory(OCWC_PATH + "small/geoList");
		// FileUtils.deleteDirectory(new File(PATH));
		File testFile;
		try {
			testFile = AbstractIngestTests.concatenateGeneratedFilesIntoOneFile(TARGET_PATH,
					TARGET_PATH + OCWC_PATH + TEST_FILENAME);
			AbstractIngestTests.compareFilesDefaultingBNodes(testFile, new File(Thread
					.currentThread().getContextClassLoader().getResource(OCWC_PATH + TEST_FILENAME)
					.toURI()));
		} catch (final FileNotFoundException e) {
			e.printStackTrace();
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	// @Test
	public void testFlux() throws IOException, URISyntaxException, RecognitionException {
		final File fluxFile = new File(Thread.currentThread().getContextClassLoader()
				.getResource("xmlSplitterRdfWriter.flux").toURI());
		Flux.main(new String[] { fluxFile.getAbsolutePath() });
		FileUtils.deleteDirectory(new File(TARGET_PATH));
	}

	private void transformDataInDirectory(final String pathToDirectory) throws URISyntaxException {
		final DirReader dirReader = new DirReader();
		final FileOpener opener = new FileOpener();
		final JsonDecoder jsonDecoder = new JsonDecoder();

		final Metamorph morph = new Metamorph(Thread.currentThread().getContextClassLoader()
				.getResource("morph-ocwConsortiumMembers-to-rdf.xml").getFile());
		final PipeEncodeTriples encoder = new PipeEncodeTriples();
		final Triples2RdfModel triple2model = new Triples2RdfModel();
		triple2model.setInput("N-TRIPLE");
		final RdfModelFileWriter writer = OerJson2RdfWriterTest.createWriter(pathToDirectory);
		final StreamTee streamTee = new StreamTee();
		final Stats stats = new Stats();
		streamTee.addReceiver(stats);
		streamTee.addReceiver(encoder);
		encoder.setReceiver(triple2model).setReceiver(writer);
		opener.setReceiver(jsonDecoder);
		jsonDecoder.setReceiver(morph).setReceiver(streamTee);
		dirReader.setReceiver(opener);
		dirReader.process((new File(Thread.currentThread().getContextClassLoader()
				.getResource(pathToDirectory).toURI())).getAbsolutePath());
		opener.closeStream();

	}
}
