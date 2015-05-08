package fr.upem.logger;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Logger {
	private final PrintStream outInfos = System.out;
	private final PrintStream outWarning = System.err;
	private final PrintStream outError = System.err;
	private final SimpleDateFormat dateFormatLog = new SimpleDateFormat(
			"yyyy/MM/dd HH:mm:ss");

	public boolean logInfos(Object message) {
		if (outInfos == null) {
			return false;
		}
		logLevel(outInfos, message.toString(), "Infos");
		return true;
	}

	public boolean logInfos(Object message, Exception e) {
		if (outInfos == null) {
			return false;
		}
		logLevel(outInfos, message.toString(), e, "Error");
		return true;
	}

	public boolean logWarning(Object message) {
		if (outWarning == null) {
			return false;
		}
		logLevel(outWarning, message.toString(), "Warning");
		return true;
	}

	public boolean logWarning(Object message, Exception e) {
		if (outWarning == null) {
			return false;
		}
		logLevel(outWarning, message.toString(), e, "Error");
		return true;
	}

	public boolean logError(Object message) {
		if (outError == null) {
			return false;
		}
		logLevel(outError, message.toString(), "Error");
		return true;
	}

	public boolean logError(Object message, Exception e) {
		if (outError == null) {
			return false;
		}
		logLevel(outError, message.toString(), e, "Error");
		return true;
	}

	private void logLevel(PrintStream out, String message, String level) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("[").append(level).append("][")
				.append(dateFormatLog.format(Calendar.getInstance().getTime()))
				.append("] ").append(message);
		out.println(stringBuilder.toString());
	}

	private void logLevel(PrintStream out, String message, Exception e,
			String level) {
		logLevel(out, message, level);
		e.printStackTrace(out);
	}
}
