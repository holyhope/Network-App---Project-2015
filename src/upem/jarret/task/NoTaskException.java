package upem.jarret.task;

public class NoTaskException extends Exception {
	private static final long serialVersionUID = 6081252199682423291L;
	private final long until;

	public NoTaskException(long time) {
		until = System.currentTimeMillis() + time;
	}

	public NoTaskException() {
		this(0);
	}

	public long getUntil() {
		return until;
	}
}
