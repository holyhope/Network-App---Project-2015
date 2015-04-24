package upem.jarret.worker;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import upem.jarret.task.NoTaskException;
import upem.jarret.task.Task;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;

import fr.upem.net.tcp.http.HTTPException;
import fr.upem.net.tcp.http.HTTPHeader;
import fr.upem.net.tcp.http.HTTPReader;

public class ClientJarRet {
	private final SocketChannel sc;
	private final InetSocketAddress serverAddress;
	private static final int BUFFER_SIZE = 4096;
	private static final Charset CHARSET_UTF8 = Charset.forName("utf-8");

	public static void main(String[] args) throws IOException {
		if (3 != args.length) {
			usage();
			return;
		}
		ClientJarRet client = new ClientJarRet(args[0], args[1],
				Integer.parseInt(args[2]));

		client.launch();
	}

	/**
	 * Display usage in <i>System.out</i>
	 */
	public static void usage() {
		usage(System.out);
	}

	/**
	 * Display usage in <i>out</i>
	 * 
	 * @param out
	 *            - Where to display usage infos.
	 */
	public static void usage(PrintStream out) {
		out.println("Usage: ClientJarRet <clientID> <serverAddress> <serverPort>\n");
	}

	private final ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
	private final String clientID;
	private Task task;

	public ClientJarRet(String clientID, String address, int port)
			throws IOException {

		sc = SocketChannel.open();
		serverAddress = new InetSocketAddress(address, port);
		sc.connect(serverAddress);
		this.clientID = clientID;
	}

	/**
	 * Start the client.
	 * 
	 * @throws IOException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws ClassNotFoundException
	 */
	public void launch() throws IOException {
		while (!Thread.interrupted()) {
			if (isIdle()) {
				initializeTaskAndCompute();
			}
			sendAnswer();
			getAnswerAndReset();
		}
		close();
	}

	private void sendAnswer() throws IOException {
		bb.flip();
		System.out.println(CHARSET_UTF8.decode(bb));
		sc.write(bb);
		bb.compact();
	}

	private void getAnswerAndReset() throws IOException {
		// Answer sent
		HTTPReader reader = new HTTPReader(sc, bb);
		HTTPHeader header;
		try {
			header = reader.readHeader();
		} catch (IllegalStateException e) {
			// Header is not fully received
			return;
		} catch (HTTPException e) {
			resetClient();
			return;
		}
		// Get code from server (200 or 400).
		int code = header.getCode();
		switch (code) {
		case 200:
			break;
		default:
			System.err.println("Error (code " + code + ")");
			try (Scanner scanner = new Scanner(System.in)) {
				while (scanner.hasNextLine()) {
					// Let user choose if he wants to retry.
					System.out.println("Try again ? (" + YesNo.YES + "/"
							+ YesNo.NO + ")");
					try {
						YesNo yesNo = YesNo.fromString(scanner.nextLine());
						if (yesNo.equals(YesNo.YES)) {
							// Stop before resetClient
							return;
						} else if (yesNo.equals(YesNo.NO)) {
							break;
						}
					} catch (IllegalArgumentException e) {
						// Nothing to do, user did not write Yes or No
					}
				}
			}
			break;
		}
		resetClient();
	}

	private void initializeTaskAndCompute() throws IOException,
			MalformedURLException {
		// No task yet
		requestNewTask();
		HTTPReader reader = new HTTPReader(sc, bb);
		HTTPHeader header;
		try {
			header = reader.readHeader();
		} catch (IllegalStateException e) {
			// Header is not fully received
			return;
		} catch (HTTPException e) {
			resetClient();
			return;
		}
		try {
			task = newTask(header, reader);
		} catch (IllegalStateException e) {
			// Content is not fully received
			return;
		} catch (NoTaskException e) {
			// No task to work on
			long time;
			while (e.getUntil() > (time = System.currentTimeMillis())) {
				try {
					Thread.sleep(e.getUntil() - time);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
			return;
		}
		Worker worker;
		try {
			worker = task.getWorker();
		} catch (ClassNotFoundException | IllegalAccessException
				| InstantiationException e) {
			e.printStackTrace(System.err);
			resetClient();
			return;
		}
		int taskNumber = Integer.parseInt(task.getJobId());
		String result = null;
		try {
			System.out.println("------- Task computing -------");
			result = worker.compute(taskNumber);
			System.out.println("------- ClientJarRet result -------");
			System.out.println(result);
			System.out.println("-----------------------------------");
		} catch (Exception e) {
			setBufferError("Computation error");
			return;
		}
		if (null == result) {
			setBufferError("Computation error");
			return;
		} else {
			try {
				checkResponse(result);
			} catch (JsonMappingException e) {
				setBufferError("Answer is nested");
				return;
			} catch (JsonGenerationException e) {
				setBufferError("Answer is not valid JSON");
				return;
			}

		}
		setBufferAnswer(result);
		return;
	}

	private void setBufferError(String errorMessage) throws IOException {
		System.err.println("error occured: " + errorMessage);
		ByteBuffer resultBb = getContentError(errorMessage);
		addSendHeader(sc, resultBb.limit());
	}

	private void setBufferAnswer(String answer) throws IOException {
		try {
			ByteBuffer resultBb = getContent(answer);
			addSendHeader(sc, resultBb.limit());
			bb.put(resultBb);
		} catch (BufferOverflowException e) {
			bb.clear();
			setBufferError("Too Long");
		}
	}

	private ByteBuffer getContent(String result) throws JsonProcessingException {
		Map<String, String> map = task.buildMap();
		map.put("ClientId", clientID);
		map.put("Error", result);
		ObjectMapper mapper = new ObjectMapper();
		return CHARSET_UTF8.encode(mapper.writeValueAsString(map));
	}

	private ByteBuffer getContentError(String error)
			throws JsonProcessingException {
		Map<String, String> map = task.buildMap();
		map.put("ClientId", clientID);
		map.put("Error", error);
		ObjectMapper mapper = new ObjectMapper();
		return CHARSET_UTF8.encode(mapper.writeValueAsString(map));
	}

	private void addSendHeader(SocketChannel channel, int size)
			throws IOException {
		Map<String, String> fields = new HashMap<>();
		fields.put("Host", channel.getRemoteAddress().toString());
		fields.put("Content-Type",
				"application/json; charset=" + CHARSET_UTF8.name());
		fields.put("Content-Length", size + "");
		HTTPHeader header = HTTPHeader.createRequestHeader(
				"POST Answer HTTP/1.1", fields);
		bb.put(header.toBytes());
	}

	private void resetClient() {
		task = null;
	}

	/**
	 * Close channel of the key.
	 * 
	 * @param key
	 *            - Selected key containing an opened channel.
	 * @throws IOException
	 */
	private void close() throws IOException {
		System.out.println("Connexion " + sc.getRemoteAddress() + " closed");
		sc.close();
	}

	/**
	 * Check if there is a task.
	 * 
	 * @return
	 */
	private boolean isIdle() {
		return task == null;
	}

	/**
	 * Send a request to get a new task.
	 * 
	 * @param sc
	 *            - Server channel.
	 * @throws IOException
	 */
	private void requestNewTask() throws IOException {
		Map<String, String> fields = new HashMap<>();
		fields.put("Host", sc.getRemoteAddress().toString());
		HTTPHeader header = HTTPHeader.createRequestHeader("GET Task HTTP/1.1",
				fields);
		bb.put(header.toBytes());
		bb.flip();
		sc.write(bb);
		bb.compact();
	}

	/**
	 * Get a task from server.
	 * 
	 * @param header
	 * 
	 * @param channel
	 * 
	 * @return new Task
	 * @throws IOException
	 * @throws NoTaskException
	 */
	private Task newTask(HTTPHeader header, HTTPReader reader)
			throws IOException, NoTaskException, IllegalStateException {
		System.out.println("------- ClientJarRet newTask START -------");
		ByteBuffer bbIn = reader.readBytes(header.getContentLength());
		bbIn.flip();
		String response = header.getCharset().decode(bbIn).toString();
		System.out.println(response);
		ObjectMapper mapper = new ObjectMapper();

		// http://stackoverflow.com/questions/23469784/com-fasterxml-jackson-databind-exc-unrecognizedpropertyexception-unrecognized-f
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		mapper.setVisibilityChecker(VisibilityChecker.Std.defaultInstance()
				.withFieldVisibility(JsonAutoDetect.Visibility.ANY));

		Task task;
		try {
			task = mapper.readValue(response, Task.class);
			System.out.println(task.toString());
		} catch (JsonMappingException e) {
			JsonFactory factory = new JsonFactory();
			JsonParser parser = factory.createParser(response);
			if (parser.nextValue() == null) {
				throw new IllegalStateException("Empty response.");
			}
			throw new NoTaskException(parser.getIntValue());
		}
		System.out.println("-------- ClientJarRet newTask END --------");
		return task;
	}

	private void checkResponse(final String json)
			throws JsonGenerationException, JsonMappingException {
		boolean valid = false;
		boolean isNested = false;
		try {
			JsonFactory factory = new JsonFactory();
			JsonParser parser = factory.createParser(json);
			JsonToken jsonToken;
			while ((jsonToken = parser.nextToken()) != null) {
				if (jsonToken.isStructStart() && isNested) {
					throw new JsonMappingException("Answer is nested");
				}
			}
			valid = true;
		} catch (JsonParseException jpe) {
			jpe.printStackTrace();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		if (!valid) {
			throw new JsonGenerationException("Answer is not valid JSON");
		}

	}
}
