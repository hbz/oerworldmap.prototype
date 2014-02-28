/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers.oer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.mindrot.jbcrypt.BCrypt;

import play.Logger;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.oer_index;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Joiner;
import com.google.common.io.BaseEncoding;

public class Application extends Controller {

	private static final String INDEX = "oer-index";
	private static final String DATA_TYPE = "oer-type";
	private static final String USER_TYPE = "users";

	final static Client client = new TransportClient(ImmutableSettings
			.settingsBuilder().put("cluster.name", "quaoar").build())
			.addTransportAddress(new InetSocketTransportAddress(
					"193.30.112.170", 9300));

	/**
	 * Create a new user account. Pass two arguments: username and password.
	 */
	public static void main(String[] args) {
		if (args.length != 2) {
			System.err.println("Pass two arguments: username and password");
			System.exit(-1);
		}
		String user = args[0];
		String pass = BCrypt.hashpw(args[1], BCrypt.gensalt());
		System.out.print(responseInfo(client
				.prepareIndex(INDEX, USER_TYPE, user)
				.setSource("user", user, "pass", pass).execute().actionGet()));
	}

	public static Result query(String q, String t) {
		if (q.trim().isEmpty() && t.trim().isEmpty())
			return ok(oer_index.render(Arrays.asList(
					// @formatter:off@
					"/oer?q=\"Cape+Town\"",
					"/oer?q=*&t=http://schema.org/CollegeOrUniversity",
					"/oer?q=Africa&t=http://schema.org/CollegeOrUniversity")));
					// @formatter:on@
		return processQuery(q, t);
	}

	public static Result get(String id) {
		try {
			GetResponse response = client.prepareGet(INDEX, DATA_TYPE, id)
					.execute().actionGet();
			String r = response.isExists() ? response.getSourceAsString() : "";
			return ok(Json.parse("[" + r + "]"));
		} catch (Exception x) {
			x.printStackTrace();
			return internalServerError(x.getMessage());
		}
	}

	public static Result put(String id) {
		if (!authorized())
			return unauthorized("Not authorized to write data!\n");
		JsonNode json = request().body().asJson();
		if (json == null || !json.isObject())
			return badRequest("Expecting single JSON object in request body!\n");
		Logger.info("Storing under ID '{}' data from user '{}': {}", id,
				userAndPass()[0], json);
		try {
			return ok(responseInfo(client.prepareIndex(INDEX, DATA_TYPE, id)
					.setSource(json.toString()).execute().actionGet()));
		} catch (Exception x) {
			x.printStackTrace();
			return internalServerError(x.getMessage());
		}
	}

	private static boolean authorized() {
		String[] userAndPass = userAndPass();
		SearchResponse search = search(QueryBuilders.idsQuery(USER_TYPE).ids(
				userAndPass[0]));
		return search.getHits().getTotalHits() == 1
				&& BCrypt.checkpw(userAndPass[1], (String) search.getHits()
						.getAt(0).getSource().get("pass"));
	}

	private static String[] userAndPass() {
		try {
			String header = request().getHeader(AUTHORIZATION)
					.replace("Basic", "").trim();
			return new String(BaseEncoding.base64().decode(header), "UTF-8")
					.split(":");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new String[] { "unauthorized", "" };
	}

	private static Result processQuery(String q, String t) {
		BoolQueryBuilder query = QueryBuilders.boolQuery().must(
				QueryBuilders.queryString(q).field("_all"));
		if (!t.trim().isEmpty())
			query = query.must(QueryBuilders.matchQuery("@type", t));
		SearchResponse response = search(query);
		List<String> hits = new ArrayList<String>();
		for (SearchHit hit : response.getHits())
			hits.add(hit.getSourceAsString());
		String jsonString = "[" + Joiner.on(",").join(hits) + "]";
		return ok(Json.parse(jsonString));
	}

	private static SearchResponse search(final QueryBuilder queryBuilder) {
		SearchRequestBuilder requestBuilder = client.prepareSearch(INDEX)
				.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
				.setQuery(queryBuilder);
		SearchResponse response = requestBuilder.setFrom(0).setSize(50)
				.setExplain(false).execute().actionGet();
		return response;
	}

	private static String responseInfo(IndexResponse r) {
		return String.format(
				"Indexed into index %s, type %s, id %s, version %s\n",
				r.getIndex(), r.getType(), r.getId(), r.getVersion());
	}
}
