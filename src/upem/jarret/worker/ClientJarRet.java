package upem.jarret.worker;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.upem.net.tcp.http.HTTPHeader;

public class ClientJarRet {
	private static final long TIMEOUT = 1000;
	private static final int BUFFER_SIZE = 4096;

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

	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	private final ByteBuffer bb;
	private Task task;

	public ClientJarRet(String clientID, String address, int port)
			throws IOException {

		SocketChannel socketChannel = SocketChannel.open();
		socketChannel.bind(new InetSocketAddress(address, port));
		selector = Selector.open();
		selectedKeys = selector.selectedKeys();
		socketChannel.register(selector, SelectionKey.OP_ACCEPT);
		bb = ByteBuffer.allocate(BUFFER_SIZE);
	}

	/**
	 * Start the client.
	 * 
	 * @throws IOException
	 */
	public void launch() throws IOException {
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
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			}
			if (key.isValid() && key.isReadable()) {
				try {
					doRead(key);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
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
		// TODO Auto-generated method stub
		if (hasTask()) {
			try {
				task = newTask((SocketChannel) key.channel());
			} catch (NoTaskException e) {
				long time;
				while (e.getUntil() > (time = System.currentTimeMillis())) {
					try {
						Thread.sleep(e.getUntil() - time);
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			}
			task.compute();
			return;
		}
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
		if (hasTask()) {
			requestNewTask((SocketChannel) key.channel());
			return;
		}
		// Task done
		sendResultAndReset((SocketChannel) key.channel());
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
		Map<String, String> fields = new HashMap<>();
		fields.put("Host", channel.getRemoteAddress().toString());
		fields.put("Content-Type", "application/json");
		fields.put("Content-Length", result.length + "");
		HTTPHeader header = HTTPHeader.create("POST Answer HTTP/1.1", fields);
		bb.put(header.toBytes());
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
	 * @return new Task
	 * @throws IOException
	 * @throws NoTaskException
	 */
	private Task newTask(SocketChannel channel) throws IOException,
			NoTaskException {
		// TODO Read from channel
		String response = null;

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
}
