package fr.upem.net.tcp.http;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;

import static fr.upem.net.tcp.http.HTTPException.ensure;

/**
 * @author carayol Class representing a HTTP header
 */

public class HTTPHeader {

	/**
	 * Supported versions of the HTTP Protocol
	 */

	private static final String[] LIST_SUPPORTED_VERSIONS = new String[] {
			"HTTP/1.0", "HTTP/1.1", "HTTP/1.2" };
	public static final Set<String> SUPPORTED_VERSIONS = Collections
			.unmodifiableSet(new HashSet<>(Arrays
					.asList(LIST_SUPPORTED_VERSIONS)));

	private static final Charset CHARSET = Charset.forName("ASCII");

	private final String response;
	private final String version;
	private final int code;
	private final Map<String, String> fields;

	private HTTPHeader(String response, String version, int code,
			Map<String, String> fields) throws HTTPException {
		this.response = response;
		this.version = version;
		this.code = code;
		this.fields = Collections.unmodifiableMap(fields);
	}

	public static HTTPHeader create(String response, Map<String, String> fields)
			throws HTTPException {
		String[] tokens = response.split(" ");
		// Treatment of the response line
		ensure(tokens.length >= 2, "Badly formed response:\n" + response);
		String version = tokens[0];
		ensure(HTTPHeader.SUPPORTED_VERSIONS.contains(version),
				"Unsupported version in response:\n" + response);
		int code = 0;
		try {
			code = Integer.valueOf(tokens[1]);
			ensure(code >= 100 && code < 600, "Invalid code in response:\n"
					+ response);
		} catch (NumberFormatException e) {
			ensure(false, "Invalid response:\n" + response);
		}
		Map<String, String> fieldsCopied = new HashMap<>();
		for (String s : fields.keySet())
			fieldsCopied.put(s, fields.get(s).trim());
		return new HTTPHeader(response, version, code, fieldsCopied);
	}

	public static HTTPHeader fromByteBuffer(ByteBuffer bb)
			throws HTTPException, IllegalStateException {
		String response = null;
		final Map<String, String> fields = new HashMap<>();

		int realLimit = -1;

		final int limit = bb.limit();
		boolean carret = false;
		boolean readingKey = true;
		StringBuilder key = new StringBuilder();
		StringBuilder value = new StringBuilder();
		for (int i = 0; i < limit; i++) {
			final char actualChar = bb.getChar(i);
			if (carret && actualChar == '\n') {
				// If empty line
				if (key.length() == 0) {
					realLimit = i;
					break;
				}

				// If start of parsing
				if (fields.size() == 0) {
					if (value.length() > 0) {
						throw new HTTPException("No response found.");
					}
					response = key.toString();
					continue;
				}

				// Otherwise key is not empty, so add it to header.
				fields.put(key.toString(), value.toString());

				// Reset key, value and flags
				key = new StringBuilder();
				value = new StringBuilder();
				readingKey = true;
				carret = false;

				continue;
			}
			carret = actualChar == '\r';

			if (readingKey && actualChar == ':') {
				readingKey = false;
				continue;
			}

			if (readingKey) {
				key.append(actualChar);
			} else {
				value.append(actualChar);
			}
		}

		if (realLimit < 0) {
			throw new IllegalStateException("No HTTP header found");
		}

		if (response == null) {
			throw new IllegalStateException("No HTTP header found");
		}

		bb.position(realLimit);
		bb.compact();

		return create(response, fields);
	}

	public String getResponse() {
		return response;
	}

	public String getVersion() {
		return version;
	}

	public int getCode() {
		return code;
	}

	public Map<String, String> getFields() {
		return fields;
	}

	/**
	 * @return the value of the Content-Length field in the header -1 if the
	 *         field does not exists
	 * @throws HTTPError
	 *             when the value of Content-Length is not a number
	 */
	public int getContentLength() throws HTTPException {
		String s = fields.get("Content-Length");
		if (s == null)
			return -1;
		else {
			try {
				return Integer.valueOf(s.trim());
			} catch (NumberFormatException e) {
				throw new HTTPException(
						"Invalid Content-Length field value :\n" + s);
			}
		}
	}

	/**
	 * @return the Content-Type null if there is no Content-Type field
	 */
	public String getContentType() {
		String s = fields.get("Content-Type");
		if (s != null) {
			return s.split(";")[0].trim();
		} else
			return null;
	}

	/**
	 * @return the charset corresponding to the Content-Type field null if
	 *         charset is unknown or unavailable on the JVM
	 */
	public Charset getCharset() {
		Charset cs = null;
		String s = fields.get("Content-Type");
		if (s == null)
			return cs;
		for (String t : s.split(";")) {
			if (t.contains("charset=")) {
				try {
					cs = Charset.forName(t.split("=")[1].trim());
				} catch (Exception e) {
					// If the Charset is unknown or unavailable we turn null
				}
				return cs;
			}
		}
		return cs;
	}

	/**
	 * @return true if the header correspond to a chunked response
	 */
	public boolean isChunkedTransfer() {
		return fields.containsKey("Transfer-Encoding")
				&& fields.get("Transfer-Encoding").trim().equals("chunked");
	}

	public String toString() {
		return response + "\r\n" + version + " " + code + "\r\n"
				+ fields.toString();
	}

	public byte[] toBytes() {
		return CHARSET.encode(toString()).array();
	}

}