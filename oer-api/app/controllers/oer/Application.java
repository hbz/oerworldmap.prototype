/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers.oer;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.jena.riot.Lang;
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
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.mindrot.jbcrypt.BCrypt;

import play.Logger;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Http.RawBuffer;
import play.mvc.Result;
import views.html.oer_index;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.BaseEncoding;

public class Application extends Controller {

	public static final String INDEX = "oer-index";
	private static final String DATA_TYPE = "oer-type";
	private static final String USER_TYPE = "users";

	final static Client productionClient = new TransportClient(
			ImmutableSettings.settingsBuilder().put("cluster.name", "quaoar")
					.build())
			.addTransportAddress(new InetSocketTransportAddress(
					"193.30.112.170", 9300));
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
			RDF_XML(Lang.RDFXML, Arrays.asList("application/rdf+xml", "text/xml", "application/xml")),
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
				.prepareIndex(INDEX, USER_TYPE, user)
				.setSource("user", user, "pass", pass).execute().actionGet()));
	}

	public static Result query(String q, String t) {
		if (q.trim().isEmpty() && t.trim().isEmpty())
			return ok(oer_index.render(Arrays.asList(
					// @formatter:off@
					"/oer?q=\"Cape+Town\"",
					"/oer?q=*&t=http://schema.org/CollegeOrUniversity",
					"/oer?q=Africa&t=http://schema.org/CollegeOrUniversity",
					"/oer?q=Africa&t=http://schema.org/CollegeOrUniversity,"
					+ "http://www.w3.org/ns/org#OrganizationalCollaboration")));
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
			Logger.info("Storing under ID '{}' data from user '{}': {}", id,
					userAndPass(authHeader)[0], jsonLd);
			return ok(responseInfo(client.prepareIndex(INDEX, DATA_TYPE, id)
					.setSource(jsonLd).execute().actionGet()));
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
		SearchResponse search = search(QueryBuilders.idsQuery(USER_TYPE).ids(
				userAndPass[0]));
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

	private static Result processQuery(String q, String t) {
		BoolQueryBuilder query = QueryBuilders.boolQuery().must(
				QueryBuilders.queryString(q).field("_all"));
		if (!t.trim().isEmpty())
			query = query.must(typeQuery(t));
		SearchResponse response = search(query);
		List<String> hits = new ArrayList<String>();
		for (SearchHit hit : response.getHits())
			hits.add(hit.getSourceAsString());
		String jsonString = "[" + Joiner.on(",").join(hits) + "]";
		return ok(Json.parse(jsonString));
	}

	private static BoolQueryBuilder typeQuery(String t) {
		final String[] types = t.split(",");
		BoolQueryBuilder query = QueryBuilders.boolQuery();
		for (String type : types)
			query = query.should(QueryBuilders.matchQuery("@type", type)
					.operator(MatchQueryBuilder.Operator.AND));
		return query;
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
