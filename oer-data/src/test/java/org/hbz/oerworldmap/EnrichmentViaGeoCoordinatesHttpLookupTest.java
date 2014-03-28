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
public class EnrichmentViaGeoCoordinatesHttpLookupTest {
	private static final String OCWC_GEO_SOURCE = "ocwc/small/geoList";
	private static final String OCWC_GEO_TARGET = "ocwc/small/geo";
	private static final String TARGET_PATH = "tmp/";

	private final String OCWC_PATH = "ocwc/";
	private final String TEST_FILENAME = "geoOsmTestResult.nt";

	@Test
	public void transformDataInDirectory() throws URISyntaxException {
		FileUtils.deleteQuietly(new File(TARGET_PATH));
		Transform.geoWithHttpLookup(OCWC_GEO_SOURCE, OCWC_GEO_TARGET,
				TARGET_PATH);
		try {
			File testFile;
			testFile = AbstractIngestTests
					.concatenateGeneratedFilesIntoOneFile(TARGET_PATH,
							TARGET_PATH + OCWC_PATH + TEST_FILENAME);
			AbstractIngestTests.compareFilesDefaultingBNodes(testFile,
					new File(Thread.currentThread().getContextClassLoader()
							.getResource(OCWC_PATH + TEST_FILENAME).toURI()));
			FileUtils.deleteDirectory(new File(TARGET_PATH));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
