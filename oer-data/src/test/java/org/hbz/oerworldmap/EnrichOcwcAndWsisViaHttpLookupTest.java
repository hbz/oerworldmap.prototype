/* Copyright 2014  Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.hbz.oerworldmap;

import java.io.File;
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
public class EnrichOcwcAndWsisViaHttpLookupTest {
	private static final String TARGET_PATH = OerJson2RdfWriterTest.TARGET_PATH;
	// ocwc:
	private static final String OCWC_GEO_SOURCE = Transform.OCWC_PATH
			+ OerJson2RdfWriterTest.GEO_LIST;
	private static final String OCWC_GEO_TARGET = Transform.OCWC_PATH
			+ "/small/geo";
	private static final String TEST_OCWC_FILENAME = "geoOsmOcwcTestResult.nt";

	// wsis:
	private static final String WSIS_GEO_SOURCE = Transform.WSIS_PATH
			+ OerJson2RdfWriterTest.WSIS_PATH_TEST_SOURCE;
	private static final String WSIS_GEO_TARGET = Transform.WSIS_PATH
			+ "/small/geo";
	private static final String TEST_WSIS_FILENAME = "geoWsisTestResult.nt";
	private static final String MORPH_WSIS_BUILD_URL = Transform.WSIS_PATH
			+ "morph-WsisInitiativesJson2GeonamesUrl.xml";
	private static final String MORPH_WSIS_LOOKUP = Transform.WSIS_PATH
			+ "morph-wsis-geonames-lookup.xml";

	@Test
	public void transformDataInDirectory() throws URISyntaxException {
		transform(TARGET_PATH, OCWC_GEO_SOURCE, OCWC_GEO_TARGET,
				Transform.MORPH_OCWC_BUILD_URL, Transform.MORPH_OCWC_LOOKUP,
				TEST_OCWC_FILENAME, Transform.OCWC_PATH);
		transform(TARGET_PATH, WSIS_GEO_SOURCE, WSIS_GEO_TARGET,
				MORPH_WSIS_BUILD_URL, MORPH_WSIS_LOOKUP, TEST_WSIS_FILENAME,
				Transform.WSIS_PATH);
	}

	private void transform(String targetPath, String geoSource,
			String geoTarget, String morphBuildUrl, String morphLookup,
			String testFilename, String PATH) throws URISyntaxException {
		FileUtils.deleteQuietly(new File(targetPath));
		Transform.geoWithHttpLookup(geoSource, geoTarget, targetPath,
				morphBuildUrl, morphLookup);
		try {
			File testFile;
			testFile = AbstractIngestTests
					.concatenateGeneratedFilesIntoOneFile(targetPath,
							targetPath + PATH + testFilename);
			System.out.println(testFile.toString());
			AbstractIngestTests.compareFilesDefaultingBNodes(testFile,
					new File(Thread.currentThread().getContextClassLoader()
							.getResource(PATH + testFilename).toURI()));
			// FileUtils.deleteDirectory(new File(targetPath));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
