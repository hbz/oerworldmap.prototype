/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package test;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.HeaderNames.AUTHORIZATION;
import static play.mvc.Http.HeaderNames.CONTENT_TYPE;
import static play.mvc.Http.Status.BAD_REQUEST;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.UNAUTHORIZED;
import static play.mvc.Http.Status.UNSUPPORTED_MEDIA_TYPE;
import static play.test.Helpers.callAction;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.contentType;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.status;

import java.util.Arrays;

import org.junit.Test;

import play.mvc.Content;
import play.mvc.HandlerRef;
import play.mvc.Result;
import play.test.FakeRequest;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

/**
 * 
 * Simple (JUnit) tests that can call all parts of a play app. If you are
 * interested in mocking a whole application, see the wiki for more details.
 * 
 */
public class ApplicationTest extends IndexTestsHarness {

	private static final HandlerRef PUT_HANDLER = controllers.oer.routes.ref.Application
			.put("abc123");

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
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
		FakeRequest request = fakeRequest()
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
		FakeRequest request = fakeRequest()
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
		FakeRequest request = fakeRequest()
				.withHeader(AUTHORIZATION, authString("user", "pass"))
				.withRawBody(body.getBytes(Charsets.UTF_8))
				.withHeader(CONTENT_TYPE, "application/json");
		Result result = callAction(PUT_HANDLER, request);
		assertThat(status(result)).isEqualTo(OK);
	}

	@Test
	public void put_successNtriples() {
		String body = "<http://www.w3.org/2001/sw/RDFCore/ntriples/> "
				+ "<http://purl.org/dc/elements/1.1/creator> "
				+ "\"Dave Beckett\" .";
		FakeRequest request = fakeRequest()
				.withHeader(AUTHORIZATION, authString("user", "pass"))
				.withRawBody(body.getBytes(Charsets.UTF_8))
				.withHeader(CONTENT_TYPE, "text/plain");
		Result result = callAction(PUT_HANDLER, request);
		assertThat(status(result)).isEqualTo(OK);
	}

	private String authString(String user, String pass) {
		return "Basic "
				+ BaseEncoding.base64().encode(
						(user + ":" + pass).getBytes(Charsets.UTF_8));
	}
}
