package upem.jarret.server;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.upem.net.tcp.http.HTTPHeader;
import fr.upem.net.tcp.http.HTTPReader;

public class ServerJarRet {
	private static final long TIMEOUT = 1000;
	private static final int BUFFER_SIZE = 4096;
	private static final Charset CHARSET_UTF8 = Charset.forName("utf-8");

	
	// TODO Handle worker priority and more than one task in workerdescription.json 
	public static void main(String[] args) throws IOException {
		if (1 != args.length) {
			usage();
			return;
		}
		ServerJarRet server = new ServerJarRet(Integer.parseInt(args[0]));

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

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;

	public ServerJarRet(int port) throws IOException {
		selector = Selector.open();
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
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
			if (key.isValid() && key.isAcceptable()) {
				try {
					doAccept(key);
				} catch (IOException e) {
					// Nothing to do
				}
			}
			if (key.isValid() && key.isWritable()) {
				try {
					doWrite(key);
				} catch (IOException e) {
					close(key);
				}
			}
			if (key.isValid() && key.isReadable()) {
				try {
					doRead(key);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		// only the ServerSocketChannel is register in OP_ACCEPT
		SocketChannel sc = serverSocketChannel.accept();
		if (sc == null)
			return; // In case, the selector gave a bad hint
		sc.configureBlocking(false);
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		sc.register(selector, SelectionKey.OP_READ, bb);
	}

	/**
	 * Read channel of key.
	 * 
	 * @param key
	 *            - selected key containing a SocketChannel.
	 * @throws IOException
	 */
	private void doRead(SelectionKey key) throws IOException {
		// TODO Receive a request for a new task or a response
		// Client requests a new task
		ByteBuffer bb = (ByteBuffer) key.attachment();
		SocketChannel sc = (SocketChannel) key.channel();
		bb.clear();
		// Server will exit when reading client's task answer
		HTTPReader reader = new HTTPReader(sc, bb);
		HTTPHeader header = reader.readClientHeader();
		System.out.println("-------- HEADER RECEIVED FROM CLIENT --------");
		System.out.println(header.toString());
		String[] tokens = header.getResponse().split(" ");
		if(tokens[0].equals("GET") && tokens[1].equals("Task")) {
			// TODO FIX JSON mismatch between server and client : JobTaskNumber&Task and WorkerVersionNumber&WorkerVersion
			// TODO remove JobDescription & JobPriority from map 
			// Create a map from workerdescription.json
			
			try {
				ObjectMapper mapper = new ObjectMapper();
		 
				// read JSON from a file
				Map<String, String> map = mapper.readValue(
					new File("workerdescription.json"),
					new TypeReference<Map<String, String>>() {
				});
				setBufferAnswer(bb, map);
				bb.flip();
				key.interestOps(SelectionKey.OP_WRITE);
				return;
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		} else if(tokens[0].equals("POST") && tokens[1].equals("Answer")) {
			// TODO Check is client answer is correct
			bb.flip();
			String json = CHARSET_UTF8.decode(bb).toString();
			Map<String,Object> map = new HashMap<String,Object>();
			ObjectMapper mapper = new ObjectMapper();
		 
			try {
		 
				//convert JSON string to Map
				map = mapper.readValue(json, 
				    new TypeReference<HashMap<String,Object>>(){});
				
				System.out.println("-------- MAP CONVERTED FROM JSON --------");
				System.out.println(map);
				bb.clear();
				// TODO Build correct header 
				addAnswerHeader(bb);
				bb.flip();
				key.interestOps(SelectionKey.OP_WRITE);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private void addSendHeader(ByteBuffer bb, int size)
			throws IOException {
		Map<String, String> fields = new HashMap<>();
		fields.put("Content-Type",
				"application/json; charset=" + CHARSET_UTF8.name());
		fields.put("Content-Length", size + "");
		HTTPHeader header = HTTPHeader.create("HTTP/1.1 200 OK", fields);
		bb.put(header.toBytes());
	}
	
	private void addAnswerHeader(ByteBuffer bb)
			throws IOException {
		String answer = "HTTP/1.1 200 OK";
		bb.put(Charset.defaultCharset().encode(answer));
	}
	
	private void setBufferAnswer(ByteBuffer bb, Map<String, String> fields) throws IOException {
		try {
			ByteBuffer resultBb = getEncodedResponse(fields);
			addSendHeader(bb, resultBb.position());
			resultBb.flip();
			bb.put(resultBb);
		} catch (BufferOverflowException e) {
			bb.clear();
			//setBufferError("Too Long");
		}
	}

	private ByteBuffer getEncodedResponse(Map<String, String> map)
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
		try {
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
		ByteBuffer bb = (ByteBuffer) key.attachment();
		SocketChannel sc = (SocketChannel) key.channel();
		sc.write(bb);
		key.interestOps(SelectionKey.OP_READ);
	}
}