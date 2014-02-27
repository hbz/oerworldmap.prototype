/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers.oer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.oer_index;

import com.google.common.base.Joiner;

public class Application extends Controller {

	final static Client client = new TransportClient(ImmutableSettings
			.settingsBuilder().put("cluster.name", "quaoar").build())
			.addTransportAddress(new InetSocketTransportAddress(
					"193.30.112.170", 9300));

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
		SearchRequestBuilder requestBuilder = client.prepareSearch("oer-index")
				.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
				.setQuery(queryBuilder);
		SearchResponse response = requestBuilder.setFrom(0).setSize(50)
				.setExplain(false).execute().actionGet();
		return response;
	}
}
