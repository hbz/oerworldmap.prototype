/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package test;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.HeaderNames.AUTHORIZATION;
import static play.mvc.Http.HeaderNames.CONTENT_TYPE;
import static play.mvc.Http.Status.BAD_REQUEST;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.UNAUTHORIZED;
import static play.mvc.Http.Status.UNSUPPORTED_MEDIA_TYPE;
import static play.mvc.Http.Status.NOT_FOUND;
import static play.test.Helpers.callAction;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.contentType;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.running;
import static play.test.Helpers.status;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import play.libs.Json;
import play.mvc.Content;
import play.mvc.HandlerRef;
import play.mvc.Result;
import play.test.FakeRequest;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import com.google.common.io.CharStreams;

import controllers.oer.routes.*;

/**
 * 
 * Simple (JUnit) tests that can call all parts of a play app. If you are
 * interested in mocking a whole application, see the wiki for more details.
 * 
 */
public class ApplicationTest extends IndexTestsHarness {

	private static final String BODY = "<http://www.w3.org/2001/sw/RDFCore/ntriples/> "
			+ "<http://purl.org/dc/elements/1.1/creator> "
			+ "\"Dave Beckett\" .";
	private static final HandlerRef PUT_HANDLER = controllers.oer.routes.ref.Application
			.put("abc123");
	private static final FakeRequest FAKE_REQUEST = fakeRequest().withHeader(
			"Accept", "application/json");

	@Test
	public void queryAll() throws JsonParseException, IOException {
		Result result = callAction(
				controllers.oer.routes.ref.Application.query("*", "", ""),
				FAKE_REQUEST);
		assertThat(status(result)).isEqualTo(OK);
		assertThat(Json.parse(contentAsString(result)).size()).isEqualTo(2);
	}

	@Test
	public void queryType0() {
		Result result = callAction(
				controllers.oer.routes.ref.Application.query("*",
						"http://schema.org/CollegeOrUniversityTest0", ""),
				FAKE_REQUEST);
		assertThat(status(result)).isEqualTo(OK);
		assertThat(Json.parse(contentAsString(result)).size()).isEqualTo(0);
	}

	@Test
	public void queryType1() {
		Result result = callAction(
				controllers.oer.routes.ref.Application.query("*",
						"http://schema.org/CollegeOrUniversityTest1", ""),
				FAKE_REQUEST);
		assertThat(status(result)).isEqualTo(OK);
		assertThat(Json.parse(contentAsString(result)).size()).isEqualTo(1);
	}

	@Test
	public void queryType2() {
		Result result = callAction(
				controllers.oer.routes.ref.Application.query("*",
						"http://schema.org/CollegeOrUniversityTest1,"
								+ "http://schema.org/CollegeOrUniversityTest2",
						""), FAKE_REQUEST);
		assertThat(status(result)).isEqualTo(OK);
		assertThat(Json.parse(contentAsString(result)).size()).isEqualTo(2);
	}

	@Test
	public void queryAllFilterLocation() {
		Result result = callAction(
				controllers.oer.routes.ref.Application.query("*", "",
						"40.8,-86.6 40.8,-88.6 42.8,-88.6 42.8,-86.6"),
				FAKE_REQUEST);
		assertThat(status(result)).isEqualTo(OK);
		assertThat(Json.parse(contentAsString(result)).size()).isEqualTo(1);
	}

	@Test
	public void renderTemplate() {
		Content html = views.html.oer_index.render(Arrays.asList(""));
		assertThat(contentType(html)).isEqualTo("text/html");
		assertThat(contentAsString(html)).contains("OER API");
	}

	@Test
	public void put_missingAuthorizationHeader() {
		FakeRequest request = fakeRequest().withRawBody(new byte[1])
				.withHeader(CONTENT_TYPE, "application/json");
		Result result = callAction(PUT_HANDLER, request);
		assertThat(status(result)).isEqualTo(BAD_REQUEST);
		assertThat(contentAsString(result)).contains(
				"Authorization required to write data");
	}

	@Test
	public void put_missingRequestBody() {
		FakeRequest request = fakeRequest().withHeader(AUTHORIZATION,
				authString("user", "pass")).withHeader(CONTENT_TYPE,
				"application/json");
		Result result = callAction(PUT_HANDLER, request);
		assertThat(status(result)).isEqualTo(BAD_REQUEST);
		assertThat(contentAsString(result)).contains(
				"Expecting content in request body");
	}

	@Test
	public void put_notAuthorized() {
		FakeRequest request = FAKE_REQUEST
				.withHeader(AUTHORIZATION, authString("the", "king"))
				.withRawBody(new byte[1])
				.withHeader(CONTENT_TYPE, "application/json");
		Result result = callAction(PUT_HANDLER, request);
		assertThat(status(result)).isEqualTo(UNAUTHORIZED);
		assertThat(contentAsString(result)).contains(
				"Not authorized to write data");
	}

	@Test
	public void put_unsupportedMediaType() {
		FakeRequest request = FAKE_REQUEST
				.withHeader(AUTHORIZATION, authString("user", "pass"))
				.withRawBody("c'est ne pas un png".getBytes(Charsets.UTF_8))
				.withHeader(CONTENT_TYPE, "image/png");
		Result result = callAction(PUT_HANDLER, request);
		assertThat(status(result)).isEqualTo(UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	public void put_successJsonLd() {
		String body = "{\"@id\" : \"http://www.w3.org/2001/sw/RDFCore/ntriples/\", "
				+ "\"creator\" : \"Dave Beckett\", "
				+ "\"@context\" : { "
				+ "\"creator\" : \"http://purl.org/dc/elements/1.1/creator\" } }";
		FakeRequest request = FAKE_REQUEST
				.withHeader(AUTHORIZATION, authString("user", "pass"))
				.withRawBody(body.getBytes(Charsets.UTF_8))
				.withHeader(CONTENT_TYPE, "application/json");
		Result result = callAction(PUT_HANDLER, request);
		assertThat(status(result)).isEqualTo(OK);
	}

	@Test
	public void put_successNtriples() {
		FakeRequest request = FAKE_REQUEST
				.withHeader(AUTHORIZATION, authString("user", "pass"))
				.withRawBody(BODY.getBytes(Charsets.UTF_8))
				.withHeader(CONTENT_TYPE, "text/plain");
		Result result = callAction(PUT_HANDLER, request);
		assertThat(status(result)).isEqualTo(OK);
	}

	@Test
	public void delete() {
		FakeRequest request = fakeRequest()
				.withHeader(AUTHORIZATION, authString("user", "pass"))
				.withRawBody(BODY.getBytes(Charsets.UTF_8))
				.withHeader(CONTENT_TYPE, "text/plain");
		assertThat(status(callAction(ref.Application.put("abc123"), request)))
				.isEqualTo(OK);
		assertThat(
				status(callAction(ref.Application.get("abc123"), FAKE_REQUEST)))
				.isEqualTo(OK);
		assertThat(
				status(callAction(ref.Application.delete("123abc"), request)))
				.isEqualTo(NOT_FOUND);
		assertThat(
				status(callAction(ref.Application.delete("abc123"), request)))
				.isEqualTo(OK);
		assertThat(
				status(callAction(ref.Application.get("abc123"), FAKE_REQUEST)))
				.isEqualTo(NOT_FOUND);
	}

	private final static String ENDPOINT = "oer?q=*";

	@Test
	public void searchViaApiWithContentNegotiationNTriples() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final String response = call(ENDPOINT, "text/plain");
				Assert.assertTrue(response.startsWith("<http")
						|| response.startsWith("_:"));
			}
		});
	}

	@Test
	public void searchViaApiWithContentNegotiationTurtle() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final String response = call(ENDPOINT, "text/turtle");
				assertThat(response).isNotEmpty().contains("      a       ");
			}
		});
	}

	@Test
	public void searchViaApiWithContentNegotiationRdfXml() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				assertThat(call(ENDPOINT, "application/rdf+xml")).isNotEmpty()
						.contains("<rdf:RDF");
			}
		});
	}

	@Test
	public void searchViaApiWithContentNegotiationN3() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final String turtle = call(ENDPOINT, "text/turtle");
				final String n3 = call(ENDPOINT, "text/n3"); // NOPMD
				/* turtle is a subset of n3 for RDF */
				assertThat(n3).isNotEmpty();
				assertThat(n3).isNotEmpty().isEqualTo(turtle);
			}
		});
	}

	@Test
	public void searchViaApiWithContentNegotiationJson() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				assertJsonResponse(call(ENDPOINT, "application/json"));
			}
		});
	}

	@Test
	public void searchViaApiWithContentNegotiationDefault() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				assertJsonResponse(call(ENDPOINT, "*/*"));
			}
		});
	}

	@Test
	public void searchViaApiWithContentNegotiationBrowser() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				assertJsonResponse(call(ENDPOINT,
						"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"));
			}
		});
	}

	static String call(final String request, final String contentType) {
		try {
			final URLConnection url = new URL("http://localhost:"
					+ TEST_SERVER_PORT + "/" + request).openConnection();
			url.setRequestProperty("Accept", contentType);
			return CharStreams.toString(new InputStreamReader(url
					.getInputStream(), Charsets.UTF_8));
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private static void assertJsonResponse(final String response) {
		assertThat(response).isNotEmpty().startsWith("[{\"@")
				.contains("@context").contains("@graph").endsWith("}]");
	}

	private String authString(String user, String pass) {
		return "Basic "
				+ BaseEncoding.base64().encode(
						(user + ":" + pass).getBytes(Charsets.UTF_8));
	}
}
