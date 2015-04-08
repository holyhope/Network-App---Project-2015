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

	public static Task empty() {
		return new Task();
	}
}
