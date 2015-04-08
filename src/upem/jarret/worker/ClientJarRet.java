package upem.jarret.worker;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Scanner;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ClientJarRet {
	private static final long TIMEOUT = 1000;

	public static void main(String[] args) throws IOException {
		if (3 != args.length) {
			usage();
			return;
		}
		ClientJarRet client = new ClientJarRet(args[0], args[1],
				Integer.parseInt(args[2]));

		client.launch();

		try (Scanner scanner = new Scanner(System.in)) {
			while (scanner.hasNext()) {
				try {
					Task task = client.newTask();
					client.compute(task);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace(System.err);
				}
			}
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

	private final SocketChannel socketChannel;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;

	public ClientJarRet(String clientID, String address, int port)
			throws IOException {

		socketChannel = SocketChannel.open();
		socketChannel.bind(new InetSocketAddress(address, port));
		selector = Selector.open();
		selectedKeys = selector.selectedKeys();
	}

	public void launch() throws IOException {
		socketChannel.configureBlocking(false);
		socketChannel.register(selector, SelectionKey.OP_ACCEPT);
		Set<SelectionKey> selectedKeys = selector.selectedKeys();
		while (!Thread.interrupted()) {
			selector.select(TIMEOUT);
			processSelectedKeys();
			selectedKeys.clear();
		}
	}

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

	private void doRead(SelectionKey key) throws IOException {
		// TODO Auto-generated method stub

	}

	private void close(SelectionKey key) throws IOException {
		System.out.println("Connexion "
				+ ((SocketChannel) key.channel()).getRemoteAddress()
				+ " closed");
		key.channel().close();
	}

	private void doWrite(SelectionKey key) throws IOException {
		// TODO Auto-generated method stub

	}

	private Task newTask() throws IOException {
		HttpURLConnection connection = getNewTaskRequest();

		// Send request
		DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
		wr.flush();
		wr.close();

		// Get Response
		InputStream is = connection.getInputStream();
		BufferedReader rd = new BufferedReader(new InputStreamReader(is));
		String line;
		StringBuilder responseBuilder = new StringBuilder();
		while ((line = rd.readLine()) != null) {
			responseBuilder.append(line);
		}
		rd.close();
		String response = responseBuilder.toString();

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
			int comeBackIn = parser.getIntValue();
			// TODO
			task = Task.empty();
		}
		return task;
	}

	private void compute(Task task) {
		// TODO Auto-generated method stub

	}

	private HttpURLConnection getNewTaskRequest() throws MalformedURLException,
			IOException, ProtocolException {
		String address = socketChannel.getRemoteAddress().toString();
		URL url = new URL(address);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET Task HTTP/1.1");
		connection.setRequestProperty("Host", address);
		return connection;
	}
}
