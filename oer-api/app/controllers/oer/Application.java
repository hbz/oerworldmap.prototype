/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers.oer;

import java.util.ArrayList;
import java.util.List;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.oer_index;

import com.fasterxml.jackson.databind.JsonNode;

public class Application extends Controller {

	final static Client client = new TransportClient(ImmutableSettings
			.settingsBuilder().put("cluster.name", "quaoar").build())
			.addTransportAddress(new InetSocketTransportAddress(
					"193.30.112.170", 9300));

	public static Result index() {
		MatchQueryBuilder query = QueryBuilders.matchQuery(
				"@graph.http://purl.org/lobid/lv#isil.@value", "DE-605");
		SearchResponse response = search(query);
		String jsonString = response.getHits().getAt(0).getSourceAsString();
		JsonNode graph = Json.parse(jsonString).get("@graph");
		List<JsonNode> nodes = new ArrayList<JsonNode>();
		for (JsonNode entity : graph)
			nodes.add(entity);
		return ok(oer_index.render(client.toString(), nodes.get(2).toString()));
	}

	private static SearchResponse search(final QueryBuilder queryBuilder) {
		SearchRequestBuilder requestBuilder = client
				.prepareSearch("lobid-organisations")
				.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
				.setQuery(queryBuilder);
		SearchResponse response = requestBuilder.setFrom(0).setSize(50)
				.setExplain(false).execute().actionGet();
		return response;
	}
}
