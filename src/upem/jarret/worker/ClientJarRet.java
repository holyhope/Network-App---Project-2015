package upem.jarret.worker;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import upem.jarret.task.NoTaskException;
import upem.jarret.task.Task;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.upem.net.tcp.http.HTTPException;
import fr.upem.net.tcp.http.HTTPHeader;

public class ClientJarRet {
	private static final long TIMEOUT = 1000;
	private static final int BUFFER_SIZE = 4096;
	private static final Charset CHARSET_UTF8 = Charset.forName("utf-8");

	public static void main(String[] args) throws IOException {
		if (3 != args.length) {
			usage();
			return;
		}
		ClientJarRet client = new ClientJarRet(args[0], args[1],
				Integer.parseInt(args[2]));

		try {
			client.launch();
		} catch (ClassNotFoundException | IllegalAccessException
				| InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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

	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	private final ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
	private Task task;

	public ClientJarRet(String clientID, String address, int port)
			throws IOException {

		SocketChannel socketChannel = SocketChannel.open();
		socketChannel.bind(new InetSocketAddress(address, port));
		selector = Selector.open();
		selectedKeys = selector.selectedKeys();
		socketChannel.register(selector, SelectionKey.OP_ACCEPT);
	}

	/**
	 * Start the client.
	 * 
	 * @throws IOException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws ClassNotFoundException
	 */
	public void launch() throws IOException, ClassNotFoundException,
			IllegalAccessException, InstantiationException {
		Set<SelectionKey> selectedKeys = selector.selectedKeys();
		while (!Thread.interrupted()) {
			selector.select(TIMEOUT);
			processSelectedKeys();
			selectedKeys.clear();
		}
	}

	/**
	 * Compute selected channels.
	 */
	private void processSelectedKeys() {
		for (SelectionKey key : selectedKeys) {
			if (key.isValid() && key.isWritable()) {
				try {
					doWrite(key);
				} catch (IOException e) {
					try {
						close(key);
					} catch (IOException e1) {
						e1.printStackTrace();
						resetClient(key);
					}
				}
			}
			if (key.isValid() && key.isReadable()) {
				try {
					doRead(key);
				} catch (IOException e) {
					e.printStackTrace();
					resetClient(key);
				}
			}
		}
	}

	/**
	 * Read channel of key.
	 * 
	 * @param key
	 *            - selected key containing a SocketChannel.
	 * @throws IOException
	 */
	private void doRead(SelectionKey key) throws IOException {
		if (!hasTask()) {
			HTTPHeader header;
			try {
				header = readHeader((SocketChannel) key.channel());
			} catch (IllegalStateException e) {
				// Header is not fully received
				return;
			} catch (HTTPException e) {
				resetClient(key);
				return;
			}
			try {
				task = newTask(header, (SocketChannel) key.channel());
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

			try {
				boolean hasError = false;
				Worker worker = task.getWorker();
				int taskNumber = Integer.parseInt(task.getJobId());
				String result = null;
				try {
					result = worker.compute(taskNumber);
				} catch (Exception e) {
					hasError = true;
					result = "Computation error";
				}
				if (null == result) {
					hasError = true;
					result = "Computation error";
				} else {
					try {
						checkResponse(result);
					} catch (JsonMappingException | JsonGenerationException e) {
						hasError = true;
						result = e.getLocalizedMessage();
					}
				}
				ByteBuffer resultBb = CHARSET_UTF8.encode(result);
				addSendHeader((SocketChannel) key.channel(), resultBb.limit());
				// TODO build answer depending on hasError value
				// TODO write response in bb.
				// TODO check length
				// TODO check json format
				// TODO check json values are not object
				key.interestOps(SelectionKey.OP_WRITE);
			} catch (ClassNotFoundException | IllegalAccessException
					| InstantiationException e) {
				e.printStackTrace();
				// TODO send Computing error
			}
		} else {
			// TODO Get code from server (200 or 400).
		}
	}

	private void addSendHeader(SocketChannel channel, int size)
			throws IOException {
		Map<String, String> fields = new HashMap<>();
		fields.put("Host", channel.getRemoteAddress().toString());

		// TODO use charset constant's name
		fields.put("Content-Type", "application/json; charset=utf-8");

		fields.put("Content-Length", size + "");
		HTTPHeader header = HTTPHeader.create("POST Answer HTTP/1.1", fields);
		bb.put(header.toBytes());
	}

	private void resetClient(SelectionKey key) {
		task = null;
		key.interestOps(SelectionKey.OP_WRITE);
	}

	private HTTPHeader readHeader(SocketChannel channel) throws IOException,
			IllegalStateException {
		channel.read(bb);
		return HTTPHeader.fromByteBuffer(bb);
	}

	/**
	 * Close channel of the key.
	 * 
	 * @param key
	 *            - Selected key containing an opened channel.
	 * @throws IOException
	 */
	private void close(SelectionKey key) throws IOException {
		System.out.println("Connexion "
				+ ((SocketChannel) key.channel()).getRemoteAddress()
				+ " closed");
		key.channel().close();
	}

	/**
	 * Write into key's channel.
	 * 
	 * @param key
	 *            - Selected key containing a channel.
	 * @throws IOException
	 */
	private void doWrite(SelectionKey key) throws IOException {
		// No task computing
		if (!hasTask()) {
			requestNewTask((SocketChannel) key.channel());
			return;
		}
		// Task done
		sendResultAndReset((SocketChannel) key.channel());
		resetClient(key);
	}

	/**
	 * Check if there is a task.
	 * 
	 * @return
	 */
	private boolean hasTask() {
		return task == null;
	}

	/**
	 * Send task's result to the server through channel. And reset task value.
	 * 
	 * @param channel
	 *            - Server channel.
	 * @throws IOException
	 */
	private void sendResultAndReset(SocketChannel channel) throws IOException {
		byte result[] = task.getResult();
		bb.put(result);
		bb.flip();
		channel.write(bb);
		task = null;
	}

	/**
	 * Send a request to get a new task.
	 * 
	 * @param channel
	 *            - Server channel.
	 * @throws IOException
	 */
	private void requestNewTask(SocketChannel channel) throws IOException {
		Map<String, String> fields = new HashMap<>();
		fields.put("Host", channel.getRemoteAddress().toString());
		HTTPHeader header = HTTPHeader.create("Get Task HTTP/1.1", fields);
		bb.put(header.toBytes());
		bb.flip();
		channel.write(bb);
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
	private Task newTask(HTTPHeader header, SocketChannel channel)
			throws IOException, NoTaskException, IllegalStateException {
		channel.read(bb);
		if (bb.limit() < header.getContentLength()) {
			throw new IllegalStateException("Response not fully received");
		}

		String response = header.getCharset().decode(bb).toString();

		ObjectMapper mapper = new ObjectMapper();
		Task task;
		try {
			task = mapper.readValue(response, Task.class);
		} catch (JsonMappingException e) {
			JsonFactory factory = new JsonFactory();
			JsonParser parser = factory.createParser(response);
			if (parser.nextValue() == null) {
				throw new IllegalStateException("Empty response.");
			}
			throw new NoTaskException(parser.getIntValue());
		}

		return task;
	}

	public void checkResponse(final String json)
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
			throw new JsonGenerationException("Answer is nested");
		}

	}
}
