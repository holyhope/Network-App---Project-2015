package upem.jarret.server;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import upem.jarret.task.NoTaskException;
import upem.jarret.task.TaskServer;
import upem.jarret.task.TasksManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.upem.logger.Logger;
import fr.upem.net.tcp.http.HTTPException;
import fr.upem.net.tcp.http.HTTPHeader;
import fr.upem.net.tcp.http.HTTPReaderServer;
import fr.upem.net.tcp.http.HTTPStateException;

public class ServerJarRet {
	private static final long TIMEOUT = 1000;
	private static final int BUFFER_SIZE = 4096;
	private static final Charset CHARSET = Charset.forName("UTF-8");

	// TODO Handle worker priority and more than one task in
	// workerdescription.json
	public static void main(String[] args) throws IOException {
		if (1 != args.length) {
			usage();
			return;
		}
		ServerJarRet server = ServerJarRet.construct(Integer.parseInt(args[0]),
				"workerdescription.json");
		server.launch();
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
		out.println("Usage: ServerJarRet <port>\n");
	}

	private final Logger logger = new Logger();
	private final Selector selector;
	private final ServerSocketChannel serverSocketChannel;

	private TasksManager taskManager;

	private ServerJarRet(int port) throws IOException {
		selector = Selector.open();
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
	}

	public static ServerJarRet construct(int port, String confFile)
			throws IOException {
		ServerJarRet server = new ServerJarRet(port);
		server.taskManager = TasksManager.construct(confFile);

		return server;
	}

	/**
	 * Start the client.
	 * 
	 * @throws IOException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws ClassNotFoundException
	 */
	public void launch() {
		Set<SelectionKey> selectedKeys = selector.selectedKeys();
		logger.logInfos("Server started");
		while (!Thread.interrupted()) {
			try {
				selector.select(TIMEOUT);
			} catch (IOException e) {
				e.printStackTrace(System.err);
			}
			processSelectedKeys(selectedKeys);
			selectedKeys.clear();
		}
	}

	/**
	 * Compute selected channels.
	 */
	private void processSelectedKeys(Set<SelectionKey> selectedKeys) {
		for (SelectionKey key : selectedKeys) {
			try {
				if (!key.isValid()) {
					continue;
				}
				Attachment attachment = ((Attachment) key.attachment());
				if (attachment != null) {
					logger.logInfos("Processing with "
							+ ((SocketChannel) key.channel())
									.getRemoteAddress());
				}
				if (key.isAcceptable()) {
					try {
						doAccept(key);
					} catch (IOException e) {
						// Nothing to do
					}
				}
				if (key.isWritable()) {
					doWrite(key);
				}
				if (key.isReadable()) {
					doRead(key);
				}
				// Check for time out
				if (attachment != null && attachment.isTimeOut()) {
					logger.logInfos("Timed out");
					throw new IllegalStateException("timed out");
				}
			} catch (IOException e) {
				close(key);
			} catch (Exception e1) {
				try {
					logger.logWarning(
							"An error with one client ("
									+ ((SocketChannel) key.channel())
											.getRemoteAddress() + ") occured",
							e1);
				} catch (Exception e2) {
					logger.logError("Cannot identify client (" + key + ")", e2);
					logger.logWarning("An error with one client occured", e1);
				}
				close(key);
			}
		}
	}

	private static class Attachment {
		final ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		HTTPHeader header;
		long lastActivity = System.currentTimeMillis();

		void setActive() {
			lastActivity = System.currentTimeMillis();
		}

		boolean isTimeOut() {
			return System.currentTimeMillis() - lastActivity > TIMEOUT;
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		// only the ServerSocketChannel is register in OP_ACCEPT
		SocketChannel sc = serverSocketChannel.accept();
		if (sc == null)
			return; // In case, the selector gave a bad hint
		sc.configureBlocking(false);
		sc.register(selector, SelectionKey.OP_READ, new Attachment());
		logger.logInfos("Connected to: " + sc.getRemoteAddress());
	}

	/**
	 * Read channel of key.
	 * 
	 * @param key
	 *            - selected key containing a SocketChannel.
	 * @throws IOException
	 */
	private void doRead(SelectionKey key) throws IOException {
		// Client requests a new task
		Attachment attachment = (Attachment) key.attachment();
		SocketChannel sc = (SocketChannel) key.channel();
		// Server will exit when reading client's task answer
		HTTPReaderServer reader = new HTTPReaderServer(sc, attachment.bb);
		if (0 == ((SocketChannel) key.channel()).read(attachment.bb)) {
			return;
		}
		attachment.setActive();
		try {
			attachment.header = reader.readHeader();
		} catch (HTTPStateException e) {
			// HTTP header is not complete
			return;
		} catch (HTTPException e) {
			logger.logWarning("HTTP header is not valid for ");
			close(key);
			return;
		}

		// Check for request content
		if (attachment.bb.position() < attachment.header.getContentLength()) {
			// Not all response yet.
			return;
		}

		String[] tokens = attachment.header.getResponse().split(" ");

		if (tokens[0].equals("GET") && tokens[1].equals("TaskWorker")) {
			logger.logInfos("Task request received");
			prepareNewTask(key);
		} else if (tokens[0].equals("POST") && tokens[1].equals("Answer")) {
			logger.logInfos("Answer received");
			computeAnswer(key);
		}
		logger.logInfos("Sending response...");
		key.interestOps(SelectionKey.OP_WRITE);
	}

	private void computeAnswer(SelectionKey key) {
		Attachment attachment = (Attachment) key.attachment();
		// TODO Check if client answer is correct
		attachment.bb.flip();
		String json = CHARSET.decode(attachment.bb).toString();
		Map<String, Object> map = new HashMap<String, Object>();
		ObjectMapper mapper = new ObjectMapper();
		try {
			// convert JSON string to Map
			map = mapper.readValue(json,
					new TypeReference<HashMap<String, Object>>() {
					});

			System.out.println("-------- MAP CONVERTED FROM JSON --------");
			System.out.println(map);
			attachment.bb.clear();
			// TODO Build correct header
			addAnswerHeader(attachment.bb);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void prepareNewTask(SelectionKey key) throws IOException {
		Attachment attachment = (Attachment) key.attachment();
		try {
			TaskServer task = taskManager.nextTask();
			logger.logInfos(task);
			setBufferAnswer(attachment.bb, task.buildMap());
		} catch (NoTaskException e) {
			logger.logWarning("No more tasks to compute");
			// TODO send no task response
		}
	}

	private void addSendHeader(ByteBuffer bb, int size) throws IOException {
		Map<String, String> fields = new HashMap<>();
		fields.put("Content-Type",
				"application/json; charset=" + CHARSET.name());
		fields.put("Content-Length", size + "");
		HTTPHeader header = HTTPHeader.create("HTTP/1.1 200 OK", fields);
		bb.put(header.toBytes());
	}

	private void addAnswerHeader(ByteBuffer bb) throws IOException {
		String answer = "HTTP/1.1 200 OK\r\n\r\n";
		bb.put(Charset.defaultCharset().encode(answer));
	}

	private void setBufferAnswer(ByteBuffer bb, Map<String, Object> map)
			throws IOException {
		ByteBuffer resultBb = getEncodedResponse(map);
		addSendHeader(bb, resultBb.position());
		resultBb.flip();
		bb.put(resultBb);
	}

	private ByteBuffer getEncodedResponse(Map<String, Object> map)
			throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		ByteBuffer bb = CHARSET.encode(mapper.writeValueAsString(map));
		bb.compact();
		return bb;
	}

	/**
	 * Close channel of the key.
	 * 
	 * @param key
	 *            - Selected key containing an opened channel.
	 * @throws IOException
	 */
	private void close(SelectionKey key) {
		try {
			logger.logInfos("Disconnected");
			key.channel().close();
		} catch (IOException e) {
			// Nothing to do
		}
	}

	/**
	 * Write into key's channel.
	 * 
	 * @param key
	 *            - Selected key containing a channel.
	 * @throws IOException
	 */
	private void doWrite(SelectionKey key) throws IOException {
		Attachment attachment = (Attachment) key.attachment();
		attachment.bb.flip();
		int write = ((SocketChannel) key.channel()).write(attachment.bb);
		if (write != 0) {
			attachment.setActive();
		}
		if (!attachment.bb.hasRemaining()) {
			logger.logInfos("Response sent");
			key.attach(new Attachment());
			key.interestOps(SelectionKey.OP_READ);
			logger.logInfos("Now listenning...");
			// TODO does not seem to read after that
		}
		attachment.bb.compact();
	}
}