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
	private static final String GEO_LIST = "small/geoList";
	private static final String ORGANIZATION_ID = "small/organizationId";
	private static final String CONSORTIUM_MEMBERS = "small/consortiumMembers";

	private final String OCWC_PATH = "ocwc/";
	private final String TARGET_PATH = "tmp/";
	private final String TEST_FILENAME = "ocwcTestResult.nt";

	@Test
	public void testFlow() {
		try {
			FileUtils.deleteQuietly(new File(TARGET_PATH));
			Transform.dataInDirectory(TARGET_PATH, OCWC_PATH
					+ CONSORTIUM_MEMBERS);
			Transform.dataInDirectory(TARGET_PATH, OCWC_PATH + ORGANIZATION_ID);
			Transform.dataInDirectory(TARGET_PATH, OCWC_PATH + GEO_LIST);
			File testFile;
			testFile = AbstractIngestTests
					.concatenateGeneratedFilesIntoOneFile(TARGET_PATH,
							TARGET_PATH + OCWC_PATH + TEST_FILENAME);
			AbstractIngestTests.compareFilesDefaultingBNodes(testFile,
					new File(Thread.currentThread().getContextClassLoader()
							.getResource(OCWC_PATH + TEST_FILENAME).toURI()));
			FileUtils.deleteDirectory(new File(TARGET_PATH));
		} catch (final FileNotFoundException e) {
			e.printStackTrace();
		} catch (final IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}
}
