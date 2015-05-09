package upem.jarret.server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
	private static final Charset CHARSET_UTF8 = Charset.forName("UTF-8");
	private static final int COMBEBACK_IN_SECONDS = 300;

	public static void main(String[] args) throws NumberFormatException,
			IOException, InterruptedException {
		if (1 != args.length) {
			usage();
			return;
		}
		ServerJarRet server = ServerJarRet.construct(Integer.parseInt(args[0]),
				"workerdescription.json");
		try (Scanner scan = new Scanner(System.in)) {
			ServerJarRet.help();
			while (scan.hasNextLine()) {
				try {
					String command = scan.nextLine();
					String lowerCase = command.toLowerCase();
					if (lowerCase.equals("quit")) {
						break;
					}
					if (lowerCase.equals("start")) {
						System.out.println("Starting server...");
						try {
							server.launch();
						} catch (IllegalStateException e) {
							e.printStackTrace(System.out);
						}
						continue;
					}
					if (lowerCase.equals("help")) {
						ServerJarRet.help();
						continue;
					}
					if (lowerCase.equals("shutdown")) {
						System.out.println("Stopping server...");
						server.shutdown();
						continue;
					}
					if (lowerCase.equals("shutdownnow")) {
						System.out.println("Shutting down server...");
						server.shutdownNow();
						continue;
					}
					if (lowerCase.equals("info")) {
						server.info();
						continue;
					}
					String commands[] = lowerCase.split("\\s");
					if (commands.length == 2 && commands[0].equals("loadtasks")) {
						System.out.println("Adding tasks...");
						server.addTasks(commands[1]);
						continue;
					}
				} catch (Exception e) {
					// Nothing to do
					e.printStackTrace(System.out);
				}
			}
		}
	}

	private static void help() {
		System.out.println("Available commands:");
		System.out.println("help             - Display this message.");
		System.out
				.println("info             - Display informations about server.");
		System.out.println("start            - start the server.");
		System.out
				.println("shutdown         - Stop server after all current task.");
		System.out.println("shutdownnow      - Stop server.");
		System.out
				.println("loadtasks <file> - Add tasks description to server.");
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
	private final String pathResults;
	private final Thread serverThread;

	private boolean isShutdown = false;
	private TasksManager taskManager;
	private final AtomicBoolean running = new AtomicBoolean(false);

	private ServerJarRet(int port) throws IOException {
		this(port, "results");
	}

	private ServerJarRet(int port, String pathResults) throws IOException {
		selector = Selector.open();
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		this.pathResults = pathResults;

		this.serverThread = new Thread(() -> {
			try {
				Set<SelectionKey> selectedKeys = selector.selectedKeys();
				logger.logInfos("Server started");
				while (!Thread.interrupted()
						&& (!isShutdown || selectedKeys.size() > 1)) {
					try {
						selector.select(TIMEOUT);
					} catch (IOException e) {
						e.printStackTrace(System.err);
					}
					processSelectedKeys(selectedKeys);
					selectedKeys.clear();
				}
			} finally {
				running.set(false);
				logger.logInfos("Server stopped");
			}
		});
	}

	public static ServerJarRet construct(int port, String confFile)
			throws IOException {
		ServerJarRet server = new ServerJarRet(port);
		server.taskManager = new TasksManager();
		server.taskManager.addTaskFromFile(confFile);

		return server;
	}

	/**
	 * Start the server.
	 * 
	 * @throws IllegalStateException
	 *             - if server already running.
	 */
	public void launch() {
		if (running.getAndSet(true)) {
			logger.logError("Attempt to launch server, but it is already running");
			throw new IllegalStateException("Server is already running");
		}
		logger.logInfos("Server starting...");
		try {
			createResultDirectory();
		} catch (IllegalAccessException e) {
			logger.logError("Result folder is not valid", e);
		}
		serverThread.start();
	}

	private void createResultDirectory() throws IllegalAccessException {
		File file = new File(pathResults);
		if (!file.exists()) {
			if (file.mkdirs()) {
				logger.logInfos("Result directory created ("
						+ file.getAbsolutePath() + ")");
			} else {
				throw new IllegalAccessException(
						"Cannot create result directory");
			}
		} else if (!file.isDirectory()) {
			throw new IllegalAccessException("Result path is not a directory");
		} else if (!file.canWrite()) {
			throw new IllegalAccessException("Cannot write in result directory");
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
		TaskServer task;

		void setActive() {
			lastActivity = System.currentTimeMillis();
		}

		boolean isTimeOut() {
			return System.currentTimeMillis() - lastActivity > TIMEOUT;
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		// Do not accept new client after shutdown command
		if (isShutdown) {
			logger.logInfos("Client refused (server stopped)");
			return;
		}
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
			// Do not accept new request after shutdown command
			if (isShutdown) {
				logger.logInfos("New task request refused");
				close(key);
				return;
			}
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
		attachment.bb.flip();
		String json = CHARSET_UTF8.decode(attachment.bb).toString();
		Map<String, Object> map = new HashMap<String, Object>();
		ObjectMapper mapper = new ObjectMapper();
		try {
			// convert JSON string to Map
			map = mapper.readValue(json,
					new TypeReference<HashMap<String, Object>>() {
					});
			attachment.bb.clear();
			if (!validResult(map)) {
				addAnswerHeader(attachment.bb, "400 Bad Request");
				logger.logWarning("Result from "
						+ ((SocketChannel) key.channel()).getRemoteAddress()
						+ " is not valid json.");
				return;
			}

			try {
				saveResult(key, map);
			} catch (IOException e) {
				logger.logError("Cannot write result", e);
			}
			attachment.task = null;
			addAnswerHeader(attachment.bb, "200 OK");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private boolean validResult(Map<String, Object> map) {
		System.out.println(map);
		String requiredFields[] = { "JobTaskNumber", "WorkerVersionNumber",
				"WorkerURL", "WorkerClassName", "JobId", "ClientId" };
		for (String field : requiredFields) {
			if (!map.containsKey(field)) {
				return false;
			}
		}
		String oneOfFields[] = { "Error", "Answer" };
		for (String field : oneOfFields) {
			if (map.containsKey(field)) {
				return true;
			}
		}
		return false;
	}

	private void saveResult(SelectionKey key, Map<String, Object> map)
			throws IOException {
		String path = pathResults + "/" + map.get("JobTaskNumber") + "-"
				+ map.get("JobId");
		File file = new File(path);
		if (!file.exists()) {
			if (file.createNewFile()) {
				logger.logInfos("Result file created");
			} else {
				throw new IOException("Cannot create a result file");
			}
		}

		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(map.get("ClientId")).append("    ")
				.append(((SocketChannel) key.channel()).getRemoteAddress())
				.append("    ")
				.append(map.getOrDefault("Answer", map.get("Error")));
		try (PrintWriter out = new PrintWriter(new BufferedWriter(
				new FileWriter(path, true)))) {
			out.println(stringBuilder.toString());
		}
		logger.logInfos("Result saved in " + path);
	}

	private void prepareNewTask(SelectionKey key) throws IOException {
		Attachment attachment = (Attachment) key.attachment();
		try {
			attachment.task = taskManager.nextTask();
			logger.logInfos(attachment.task);
			setBufferAnswer(attachment.bb, attachment.task.buildMap());
		} catch (NoTaskException e) {
			logger.logWarning("No more tasks to compute");
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("ComeBackInSeconds", COMBEBACK_IN_SECONDS);
			setBufferAnswer(attachment.bb, map);
		}
	}

	private void addSendHeader(ByteBuffer bb, int size) throws IOException {
		Map<String, String> fields = new HashMap<>();
		fields.put("Content-Type",
				"application/json; charset=" + CHARSET_UTF8.name());
		fields.put("Content-Length", size + "");
		HTTPHeader header = HTTPHeader.create("HTTP/1.1 200 OK", fields);
		bb.put(header.toBytes());
	}

	private void addAnswerHeader(ByteBuffer bb, String code) throws IOException {
		// TODO Build header correctly
		String answer = "HTTP/1.1 " + code + "\r\n\r\n";
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
		ByteBuffer bb = CHARSET_UTF8.encode(mapper.writeValueAsString(map));
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
		SocketChannel sc = (SocketChannel) key.channel();
		Attachment attachment = (Attachment) key.attachment();
		if (attachment != null) {
			if (attachment.task != null) {
				attachment.task.incrementPriority();
			}
		}
		try {
			sc.close();
			logger.logInfos("Disconnected");
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
			// Do not accept new request after shutdown command
			if (isShutdown && attachment.task == null) {
				close(key);
				return;
			}
			key.attach(new Attachment());
			key.interestOps(SelectionKey.OP_READ);
			logger.logInfos("Now listenning...");
		}
		attachment.bb.compact();
	}

	/**
	 * Check if server is currently running.
	 * 
	 * @return true if server is running.
	 */
	public boolean isRunning() {
		return running.get();
	}

	/**
	 * Shutdown server. It won't accept new client or new task request.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void shutdown() {
		logger.logInfos("Shutdown command received");
		isShutdown = true;
		while (isRunning()) {
		}
	}

	/**
	 * Add tasks from file configuration to server.
	 * 
	 * @param fileConfig
	 *            - path to file config.
	 */
	public void addTasks(String fileConfig) {
		try {
			taskManager.addTaskFromFile(fileConfig);
			logger.logInfos("Tasks added");
		} catch (IOException e) {
			logger.logError("Tasks description file is not valid");
			e.printStackTrace();
		}
	}

	/**
	 * Kill all connection and stop server.
	 */
	public void shutdownNow() {
		serverThread.interrupt();
	}

	/**
	 * Display informations about server on standard output.
	 */
	public void info() {
		if (!isRunning()) {
			System.out.println("Server is not running.");
		} else {
			System.out.println("There is " + (selector.keys().size() - 1)
					+ " client(s) connected");
		}
		taskManager.info(System.out);
	}
}
