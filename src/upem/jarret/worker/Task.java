package upem.jarret.worker;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public class Task {
	private void loadJar() throws MalformedURLException {
		URL urls[] = new URL[1];
		urls[0] = new URL("");
		URLClassLoader classLoader = new URLClassLoader(urls);
	}

	/**
	 * Compute task.
	 */
	public void compute() {
		// TODO Auto-generated method stub

	}

	/**
	 * Get the JSon formated result of the task.
	 * 
	 * @return Result, ready to be sent to server.
	 */
	public byte[] getResult() {
		// TODO Auto-generated method stub
		return null;
	}
}
