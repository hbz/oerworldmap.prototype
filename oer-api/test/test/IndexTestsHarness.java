/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package test;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;
import static play.test.Helpers.testServer;

import java.io.IOException;

import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.AdminClient;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.node.Node;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import play.test.TestServer;
import controllers.oer.Application;
import controllers.oer.NtToEs;

/**
 * Tests harness for the search tests. Creates an in-memory ES index.
 * 
 * @author Fabian Steeg (fsteeg)
 */
@SuppressWarnings("javadoc")
public class IndexTestsHarness {

	private static final String TEST_DATA = "public/data/samples";
	private static final String TEST_INDEX = Application.INDEX;
	static final int TEST_SERVER_PORT = 5000;
	static final TestServer TEST_SERVER = testServer(TEST_SERVER_PORT);
	private static Node node;
	protected static Client client;
	private static final Logger LOG = LoggerFactory
			.getLogger(IndexTestsHarness.class);

	@BeforeClass
	public static void setup() throws IOException {
		node = nodeBuilder().local(true).node();
		client = node.client();
		AdminClient admin = client.admin();
		IndicesAdminClient indices = admin.indices();
		if (indices.prepareExists(TEST_INDEX).execute().actionGet().isExists())
			indices.prepareDelete(TEST_INDEX).execute().actionGet();
		admin.cluster().prepareHealth().setWaitForYellowStatus().execute()
				.actionGet();
		Application.clientSet(client);
		NtToEs.main(TEST_DATA);
		Application.main("user", "pass");
		indices.refresh(new RefreshRequest()).actionGet();
		LOG.info("Local testing index done");
	}

	@AfterClass
	public static void down() {
		client.admin().indices().prepareDelete(TEST_INDEX).execute()
				.actionGet();
		node.close();
		Application.clientReset();
	}
}
