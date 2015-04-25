package upem.jarret.worker;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import upem.jarret.task.NoTaskException;
import upem.jarret.task.Task;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonFactory;
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
	private static final int BUFFER_SIZE = 4096;
	private static final Charset CHARSET_UTF8 = Charset.forName("UTF-8");

	public static void main(String[] args) {
		if (3 != args.length) {
			usage();
			return;
		}
		ClientJarRet client;
		try {
			client = new ClientJarRet(args[0], args[1],
					Integer.parseInt(args[2]));
		} catch (NumberFormatException e) {
			usage();
			return;
		} catch (IOException e) {
			System.err.println(e);
			return;
		}
		client.start();
		// TODO scan System.in for shutdown in order to terminate client.
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

	private final Thread thread;
	private final String clientID;
	private final SocketChannel sc;
	private final InetSocketAddress serverAddress;
	private final ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
	private final AtomicBoolean running = new AtomicBoolean(false);

	private Task task;

	public ClientJarRet(String clientID, String address, int port)
			throws IOException {

		serverAddress = new InetSocketAddress(address, port);
		this.clientID = clientID;
		sc = SocketChannel.open();
		thread = new Thread(() -> {
			try {
				sc.connect(serverAddress);
				while (!Thread.interrupted()) {
					try {
						initializeTaskAndCompute();
						sendAnswer();
						getAnswerAndReset();
					} catch (ClosedByInterruptException e) {
						break;
					} catch (Exception e) {
						System.err.println(e);
					} finally {
						endTask();
					}
				}
			} catch (IOException e) {
				System.err.println(e);
			} finally {
				running.set(false);
				System.out.println("Client terminated.");
			}
		});
	}

	/**
	 * Start the client.
	 */
	public void start() {
		if (running.getAndSet(true)) {
			throw new IllegalStateException("Client already running.");
		}
		thread.start();
	}

	/**
	 * Stop the client.
	 */
	public void shutdown() {
		System.out.println("Shutting down...");
		thread.interrupt();
	}

	/**
	 * Check if client is running.
	 * 
	 * @return True if client is running.
	 */
	public boolean isrunning() {
		return running.get();
	}

	private void sendAnswer() throws IOException {
		bb.flip();
		sc.write(bb);
		bb.clear();
	}

	private void getAnswerAndReset() throws IOException {
		// Answer sent
		HTTPReader reader = new HTTPReader(sc, bb);
		HTTPHeader header;
		try {
			header = reader.readHeader();
		} catch (HTTPException e) {
			System.err.println(e);
			return;
		}
		int code = header.getCode();
		switch (code) {
		case 200:
			break;
		default:
			System.err.println("Error from server: " + code);
		}
	}

	private void initializeTaskAndCompute() throws IOException {
		// No task yet
		requestNewTask();
		HTTPReader reader = new HTTPReader(sc, bb);
		HTTPHeader header = reader.readHeader();
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
			// setBufferError(e.getMessage());
			// This error should not be reported to the server.
			throw new IOException("Invalid jar file.");
		}
		int taskNumber = Integer.parseInt(task.getJobId());
		String result = null;
		try {
			result = worker.compute(taskNumber);
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
			} catch (IOException e) {
				setBufferError("Answer is not valid JSON");
				return;
			}

		}
		setBufferAnswer(result);
		return;
	}

	private void setBufferError(String errorMessage) throws IOException {
		ByteBuffer resultBb = constructResponse("Error", errorMessage);
		addSendHeader(sc, resultBb.position());
		resultBb.flip();
		bb.put(resultBb);
	}

	private void setBufferAnswer(String answer) throws IOException {
		try {
			ByteBuffer resultBb = constructResponse("Answer", answer);
			addSendHeader(sc, resultBb.position());
			resultBb.flip();
			bb.put(resultBb);
		} catch (BufferOverflowException e) {
			bb.clear();
			setBufferError("Too Long");
		}
	}

	private ByteBuffer constructResponse(String key, String msg)
			throws JsonProcessingException {
		Map<String, String> map = task.buildMap();
		map.put("ClientId", clientID);
		map.put(key, msg);
		return getEncodedResponse(map);
	}

	private ByteBuffer getEncodedResponse(Map<String, String> map)
			throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		ByteBuffer bb = CHARSET_UTF8.encode(mapper.writeValueAsString(map));
		bb.compact();
		return bb;
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

	private void endTask() {
		task = null;
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

	private void checkResponse(final String json) throws JsonMappingException,
			IOException {
		JsonFactory factory = new JsonFactory();
		JsonParser parser = factory.createParser(json);
		JsonToken jsonToken;
		while ((jsonToken = parser.nextToken()) != null) {
			if (jsonToken.isStructStart()) {
				throw new JsonMappingException("Answer is nested");
			}
		}

	}
}
