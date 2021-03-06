/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers.oer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.riot.Lang;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.GeoPolygonFilterBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.mindrot.jbcrypt.BCrypt;

import play.Logger;
import play.api.http.MediaRange;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Http.RawBuffer;
import play.mvc.Result;
import views.html.oer_index;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.jsonldjava.utils.JSONUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.BaseEncoding;

public class Application extends Controller {

	public static final String DATA_INDEX = "oer-data";
	static final String DATA_TYPE = "oer-type";
	public static final String USER_INDEX = "oer-users";
	private static final String USER_TYPE = "user-type";

	final static Client productionClient = new TransportClient(
			ImmutableSettings.settingsBuilder()
					.put("cluster.name", "aither").build())
			.addTransportAddress(new InetSocketTransportAddress(
					"193.30.112.84", 9300));
	static Client client = productionClient;

	/**
	 * @param newClient
	 *            The new elasticsearch client to use.
	 */
	public static void clientSet(Client newClient) {
		client = newClient;
	}

	/** Reset the elasticsearch client. */
	public static void clientReset() {
		client = productionClient;
	}

	@SuppressWarnings("javadoc")
	/* no javadoc for elements */
	public static enum Serialization {/* @formatter:off */
			JSON_LD(Lang.JSONLD, Arrays.asList("application/json", "application/ld+json")),
			RDF_XML(Lang.RDFXML, Arrays.asList("application/rdf+xml")),
			N_TRIPLE(Lang.NTRIPLES, Arrays.asList("text/plain")),
			N3(Lang.N3, Arrays.asList("text/rdf+n3", "text/n3")),
			TURTLE(Lang.TURTLE, Arrays.asList("application/x-turtle", "text/turtle"));/* @formatter:on */

		Lang format;
		List<String> types;

		/** @return The content types associated with this serialization. */
		public List<String> getTypes() {
			return types;
		}

		private Serialization(final Lang format, final List<String> types) {
			this.format = format;
			this.types = types;
		}
	}

	/**
	 * Create a new user account. Pass two arguments: username and password.
	 */
	public static void main(String... args) {
		if (args.length != 2) {
			System.err.println("Pass two arguments: username and password");
			System.exit(-1);
		}
		String user = args[0];
		String pass = BCrypt.hashpw(args[1], BCrypt.gensalt());
		System.out.print(responseInfo(client
				.prepareIndex(USER_INDEX, USER_TYPE, user)
				.setSource("user", user, "pass", pass).execute().actionGet()));
	}

	public static Result query(String q, String t, String location) {
		if (q.trim().isEmpty() && t.trim().isEmpty())
			return ok(oer_index.render(Arrays.asList(
					// @formatter:off@
					"/oer?q=\"Cape+Town\"",
					"/oer?q=*&t=http://schema.org/Organization",
					"/oer?q=Africa&t=http://schema.org/Organization",
					"/oer?q=\"http\\://www.oerafrica.org\""
					+ "&t=http://schema.org/Organization,http://schema.org/Service",
					"/oer?q=*&location=40.8,-86.6+40.8,-88.6+42.8,-88.6+42.8,-86.6",
					"/oer?q=*&location=germany",
					"/oer?q=\"Cape+Town\"&callback=callbackFunction",
					"/oer?q=University&from=0&size=5")));
					// @formatter:on@
		return processQuery(q, t, location);
	}

	public static Result get(String id) {
		try {
			String value = id.startsWith("http://") ? id : String.format(
					"http://lobid.org/oer/%s#!", id);
			MatchQueryBuilder query = QueryBuilders.matchQuery("@graph.@id",
					value);
			SearchResponse response = search(DATA_INDEX, query, "", DATA_TYPE);
			boolean found = response.getHits().getTotalHits() > 0;
			return !found ? notFound() : response(Json.parse("["
					+ withoutLocation(response.getHits().getAt(0)
							.getSourceAsString()) + "]"));
		} catch (Exception x) {
			x.printStackTrace();
			return internalServerError(x.getMessage());
		}
	}

	@BodyParser.Of(BodyParser.Raw.class)
	public static Result put(String id) throws UnsupportedEncodingException {
		RawBuffer rawBody = request().body().asRaw();
		if (rawBody == null)
			return badRequest("Expecting content in request body!\n");
		String contentType = request().getHeader(CONTENT_TYPE);
		if (contentType == null || contentType.isEmpty())
			return badRequest("Content-Type header required!");
		String authHeader = request().getHeader(AUTHORIZATION);
		if (authHeader == null || authHeader.isEmpty())
			return badRequest("Authorization required to write data!\n");
		if (!authorized(authHeader))
			return unauthorized("Not authorized to write data!\n");
		String requestBody = new String(rawBody.asBytes(), Charsets.UTF_8);
		return processRequest(id, authHeader, requestBody, contentType);
	}

	public static Result delete(String id) {
		String authHeader = request().getHeader(AUTHORIZATION);
		if (authHeader == null || authHeader.isEmpty())
			return badRequest("Authorization required to delete data!\n");
		if (!authorized(authHeader))
			return unauthorized("Not authorized to delete data!\n");
		try {
			DeleteResponse response = client
					.prepareDelete(DATA_INDEX, DATA_TYPE, id).execute()
					.actionGet();
			return !response.isFound() ? notFound() : ok("Deleted " + id);
		} catch (Exception x) {
			x.printStackTrace();
			return internalServerError(x.getMessage());
		}
	}

	private static Result processRequest(String id, String auth,
			String requestBody, String contentType) {
		for (Serialization serialization : Serialization.values())
			for (String mimeType : serialization.getTypes())
				if (mimeType.equalsIgnoreCase(contentType)) {
					Logger.info(
							"Incoming Content-Type '{}' supported as format '{}'",
							mimeType, serialization.format.getLabel());
					return executeRequest(id, auth, serialization, requestBody);
				}
		return status(UNSUPPORTED_MEDIA_TYPE);
	}

	private static Result executeRequest(String id, String authHeader,
			Serialization serialization, String requestBody) {
		try {
			String jsonLd = NtToEs.rdfToJsonLd(requestBody,
					serialization.format);
			String parent = NtToEs.findParent(jsonLd);
			Logger.info(
					"Storing under ID '{}' and parent '{}' data from user '{}': {}",
					id, parent, userAndPass(authHeader)[0], jsonLd);
			return ok(responseInfo(client
					.prepareIndex(DATA_INDEX, DATA_TYPE, id).setSource(jsonLd)
					.setParent(parent).execute().actionGet()));
		} catch (Exception e) {
			e.printStackTrace();
			String message = String.format(
					"Could not process request body as format '%s': %s\n",
					serialization.format.getLabel(), e.getMessage());
			return internalServerError(message);
		}
	}

	private static boolean authorized(String authHeader) {
		String[] userAndPass = userAndPass(authHeader);
		SearchResponse search = search(USER_INDEX,
				QueryBuilders.idsQuery(USER_TYPE).ids(userAndPass[0]), "",
				USER_TYPE);
		return search.getHits().getTotalHits() == 1
				&& BCrypt.checkpw(userAndPass[1], (String) search.getHits()
						.getAt(0).getSource().get("pass"));
	}

	private static String[] userAndPass(String authHeader) {
		try {
			String header = authHeader.replace("Basic", "").trim();
			return new String(BaseEncoding.base64().decode(header), "UTF-8")
					.split(":");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new String[] { "unauthorized", "" };
	}

	private static Result processQuery(String q, String t, String location) {
		List<String> hits = hits(q, t, location);
		if (location.isEmpty())
			hits.addAll(useQueryTermAsLocation(q, t));
		String jsonString = "[" + Joiner.on(",").join(hits) + "]";
		return response(Json.parse(jsonString));
	}

	private static List<String> useQueryTermAsLocation(String q, String t) {
		return hits("*", t, q);
	}

	private static List<String> hits(String q, String t, String location) {
		BoolQueryBuilder query = QueryBuilders.boolQuery().must(
				QueryBuilders.queryString(q).field("_all"));
		if (!t.trim().isEmpty())
			query = query.must(typeQuery(t));
		SearchResponse response = search(DATA_INDEX, query, location, DATA_TYPE);
		List<String> hits = new ArrayList<String>();
		for (SearchHit hit : response.getHits())
			hits.add(withoutLocation(hit.getSourceAsString()));
		return hits;
	}

	private static Status response(JsonNode json) {
		/* JSONP callback support for remote server calls with JavaScript: */
		final String[] callback = request() == null
				|| request().queryString() == null ? null : request()
				.queryString().get("callback");
		Pair<String, Lang> negotiatedContent = negotiateContent(json);
		final Status notAcceptable = status(406,
				"Not acceptable: unsupported content type requested\n");
		if (invalidAcceptHeader() || negotiatedContent == null)
			return notAcceptable;
		if (callback != null)
			return ok(String.format("%s(%s)", callback[0],
					negotiatedContent.getLeft()));
		if (negotiatedContent.getRight().equals(Lang.JSONLD))
			return ok(Json.parse(negotiatedContent.getLeft()));
		return ok(negotiatedContent.getLeft());
	}

	private static Pair<String, Lang> negotiateContent(JsonNode json) {
		for (MediaRange mediaRange : request().acceptedTypes())
			for (Serialization serialization : Serialization.values())
				for (String mimeType : serialization.getTypes())
					if (mediaRange.accepts(mimeType)) {
						if (serialization.format.equals(Lang.JSONLD))
							return Pair.of(withRemoteContext(json.toString()),
									Lang.JSONLD);
						Logger.debug("Matching mime {}, converting JSON to {}",
								mimeType, serialization.format);
						return Pair.of(
								NtToEs.jsonLdToRdf(json, serialization.format),
								serialization.format);
					}
		return null;
	}

	private static String withRemoteContext(String string) {
		try {
			// JSON-LD compact, always an object (resulting in a map)
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> maps = (List<Map<String, Object>>) JSONUtils
					.fromString(string);
			for (Map<String, Object> map : maps) {
				map.put("@context",
						"http://api.lobid.org/oer/data/context.json");
			}
			return JSONUtils.toString(maps);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return string;
	}

	private static boolean invalidAcceptHeader() {
		if (request() == null)
			return true;
		final String acceptHeader = request().getHeader("Accept");
		return (acceptHeader == null || acceptHeader.trim().isEmpty());
	}

	private static String withoutLocation(String sourceAsString) {
		try {
			// JSON-LD compact, always an object (resulting in a map)
			@SuppressWarnings("unchecked")
			Map<String, Object> json = (Map<String, Object>) JSONUtils
					.fromString(sourceAsString);
			json.remove("location");
			return JSONUtils.toString(json);
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return sourceAsString;
	}

	private static BoolQueryBuilder typeQuery(String t) {
		final String[] types = t.split(",");
		BoolQueryBuilder query = QueryBuilders.boolQuery();
		for (String type : types)
			query = query.should(QueryBuilders.matchQuery("@graph.@type", type)
					.operator(MatchQueryBuilder.Operator.AND));
		return query;
	}

	private static SearchResponse search(String index,
			QueryBuilder queryBuilder, String location, String type) {
		SearchRequestBuilder requestBuilder = client.prepareSearch(index)
				.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
				.setQuery(queryBuilder).setTypes(type);
		if (!location.trim().isEmpty())
			requestBuilder = requestBuilder.setPostFilter(locationFilter(location));
		Logger.debug("Request:\n" + requestBuilder);
		SearchResponse response = requestBuilder.setFrom(get("from", 0))
				.setSize(get("size", 50)).setExplain(false).execute()
				.actionGet();
		Logger.debug("Response:\n" + response);
		return response;
	}

	private static int get(String parameterName, int defaultValue) {
		final String[] params = request() == null
				|| request().queryString() == null ? null : request()
				.queryString().get(parameterName);
		if (params != null)
			try {
				return Integer.parseInt(params[0]);
			} catch (NumberFormatException e) {
				e.printStackTrace();
				return defaultValue;
			}
		return defaultValue;
	}

	private static FilterBuilder locationFilter(String location) {
		if (location.matches(".*\\d+.*"))
			return polygonFilter(location);
		else
			return FilterBuilders.hasParentFilter("geonames-type",
					QueryBuilders.queryString(location).field("_all"));
	}

	private static FilterBuilder polygonFilter(String location) {
		GeoPolygonFilterBuilder filter = FilterBuilders
				.geoPolygonFilter("oer-type.location");
		String[] points = location.split(" ");
		for (String point : points) {
			String[] latLon = point.split(",");
			filter = filter.addPoint(Double.parseDouble(latLon[0].trim()),
					Double.parseDouble(latLon[1].trim()));
		}
		return filter;
	}

	private static String responseInfo(IndexResponse r) {
		return String.format(
				"Indexed into index %s, type %s, id %s, version %s\n",
				r.getIndex(), r.getType(), r.getId(), r.getVersion());
	}
}
