/* Copyright 2014 hbz, Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.hbz.oerworldmap;

import java.io.IOException;
import java.io.Reader;

import org.culturegraph.mf.exceptions.MetafactureException;
import org.culturegraph.mf.framework.DefaultObjectPipe;
import org.culturegraph.mf.framework.StreamReceiver;
import org.culturegraph.mf.framework.annotations.Description;
import org.culturegraph.mf.framework.annotations.In;
import org.culturegraph.mf.framework.annotations.Out;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.io.CharStreams;

/**
 * Decodes JSON. The JSON (or JSONP) record may consist of one or n records.
 * However, if there are objects in arrays, these will be handled as new objects
 * and not as part of the root object.
 * 
 * @author Pascal Christoph (dr0i)
 * 
 */
@Description("Decodes a json record as literals (as key-value pairs).")
@In(Reader.class)
@Out(StreamReceiver.class)
public final class JsonDecoder extends DefaultObjectPipe<Reader, StreamReceiver> {
	private static final String JSON_START_CHAR = "{";
	private static final String JSON_CALLBACK = "json_callback";
	private JsonParser jsonParser;
	private static final Logger LOG = LoggerFactory.getLogger(JsonDecoder.class);
	private static boolean STARTED;

	private void handleValue(final JsonToken currentToken, final String key) throws IOException,
			JsonParseException {
		{
			if (JsonToken.VALUE_STRING == currentToken
					|| JsonToken.VALUE_NUMBER_INT == currentToken
					|| JsonToken.VALUE_NUMBER_FLOAT == currentToken) {
				final String value = this.jsonParser.getText();
				JsonDecoder.LOG.debug("key=" + key + " value=" + value);
				getReceiver().literal(key, value);
			}
		}
	}

	@Override
	public void process(final Reader reader) {
		JsonDecoder.STARTED = false;
		JsonDecoder.LOG.debug("############################ New");
		// necessary if it is JSONP
		String text;
		try {
			text = CharStreams.toString(reader);
			this.jsonParser = new JsonFactory().createParser(text);
			// find start
			JsonToken currentToken = null;
			try {
				currentToken = this.jsonParser.nextToken();
			} catch (final JsonParseException e) {
				// assuming JSONP :
				final String callbackString = text.substring(0,
						text.indexOf(JsonDecoder.JSON_START_CHAR) - 1);
				text = text.substring(text.indexOf(JsonDecoder.JSON_START_CHAR), text.length() - 1);
				this.jsonParser = new JsonFactory().createParser(text);
				JsonDecoder.LOG.debug("key=" + JsonDecoder.JSON_CALLBACK + " value="
						+ callbackString);
				getReceiver().startRecord("");
				JsonDecoder.STARTED = true;
				getReceiver().literal(JsonDecoder.JSON_CALLBACK, callbackString);
				JsonDecoder.LOG.debug("Text=" + text);
				currentToken = this.jsonParser.nextToken();
			}

			while (JsonToken.START_OBJECT != currentToken) {
				this.jsonParser.nextToken();
			}
			String key = null;
			while (currentToken != null) {
				if (JsonToken.START_OBJECT == currentToken) {
					if (!JsonDecoder.STARTED) {
						getReceiver().startRecord("");
						JsonDecoder.STARTED = true;
					}
					currentToken = this.jsonParser.nextToken();
					while (currentToken != null) {
						if (JsonToken.FIELD_NAME == currentToken) {
							key = this.jsonParser.getCurrentName();
						}
						if (JsonToken.START_ARRAY == currentToken) {
							currentToken = this.jsonParser.nextToken();
							// treat objects in arrays as new objects
							if (JsonToken.START_OBJECT == currentToken) {
								break;
							}
							// values of arrays are submitted with an index so
							// you can handle
							// semantics in the morph
							int i = 0;
							while (JsonToken.END_ARRAY != currentToken) {
								final String value = this.jsonParser.getText();
								JsonDecoder.LOG.debug("key=" + key + i + " valueArray=" + value);
								getReceiver().literal(key + i, value);
								currentToken = this.jsonParser.nextToken();
								i++;
							}
						}
						if (JsonToken.START_OBJECT == currentToken) {
							if (this.jsonParser.getCurrentName() == null) {
								break;
							}
						} else {
							handleValue(currentToken, key);
						}
						currentToken = this.jsonParser.nextToken();
					}
				}
				JsonDecoder.LOG.debug("############################ End");
				if (JsonDecoder.STARTED) {
					getReceiver().endRecord();
					JsonDecoder.STARTED = false;
				}
			}
		} catch (final IOException e) {
			throw new MetafactureException(e);
		}
	}
}
