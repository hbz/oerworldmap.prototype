/* Copyright 2014  Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.hbz.oerworldmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.lobid.lodmill.AbstractIngestTests;

/**
 * @author Pascal Christoph (dr0i)
 * 
 */
@SuppressWarnings("javadoc")
public class OerJson2RdfWriterTest {
	private static final String MORPH_OCW_CONSORTIUM_MEMBERS_TO_RDF_XML = "morph-ocwConsortiumMembers-to-rdf.xml";
	static final String GEO_LIST = "small/geoList";
	private static final String ORGANIZATION_ID = "small/organizationId";
	private static final String CONSORTIUM_MEMBERS = "small/consortiumMembers";

	final static String TARGET_PATH = "tmp/";
	private final String TEST_FILENAME = "oerWorldmapTestResult.nt";

	// wsis
	final static String WSIS_PATH_TEST_SOURCE = "small/wsis-initiative-data.json";

	@Test
	public void testFlow() {
		try {
			FileUtils.deleteQuietly(new File(TARGET_PATH));
			Transform.dataInDirectory(MORPH_OCW_CONSORTIUM_MEMBERS_TO_RDF_XML,
					TARGET_PATH, Transform.OCWC_PATH + CONSORTIUM_MEMBERS);
			Transform.dataInDirectory(MORPH_OCW_CONSORTIUM_MEMBERS_TO_RDF_XML,
					TARGET_PATH, Transform.OCWC_PATH + ORGANIZATION_ID);
			Transform.dataInDirectory(MORPH_OCW_CONSORTIUM_MEMBERS_TO_RDF_XML,
					TARGET_PATH, Transform.OCWC_PATH + GEO_LIST);
			Transform.dataInDirectory(
					"morph-ocwConsortiumMembersServices-to-rdf.xml",
					TARGET_PATH, Transform.OCWC_PATH + ORGANIZATION_ID);
			Transform.dataInDirectory("wsis/morph-WsisInitiativesJson2ld.xml",
					TARGET_PATH, Transform.WSIS_PATH + WSIS_PATH_TEST_SOURCE);
			Transform.dataInDirectory("wsis/morph-wsisPersons-to-rdf.xml",
					TARGET_PATH, Transform.WSIS_PATH
							+ "small/wsis-person-data.json");
			BuildMembershipReziprocally.main("tmp/ocwc/small/");
			File testFile;
			testFile = AbstractIngestTests
					.concatenateGeneratedFilesIntoOneFile(TARGET_PATH,
							TARGET_PATH + TEST_FILENAME);
			AbstractIngestTests.compareFilesDefaultingBNodes(testFile,
					new File(Thread.currentThread().getContextClassLoader()
							.getResource(TEST_FILENAME).toURI()));
			// FileUtils.deleteDirectory(new File(TARGET_PATH));
		} catch (final FileNotFoundException e) {
			e.printStackTrace();
		} catch (final IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}
}
