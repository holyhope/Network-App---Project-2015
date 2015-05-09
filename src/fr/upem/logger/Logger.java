package fr.upem.logger;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Logger {
	private final PrintStream outInfos;
	private final PrintStream outWarning;
	private final PrintStream outError;
	private final SimpleDateFormat dateFormatLog = new SimpleDateFormat(
			"yyyy/MM/dd HH:mm:ss");

	public Logger(String logInfoPath, String logWarningPath, String logErrorPath)
			throws IOException {
		if (null == logInfoPath || logInfoPath.isEmpty()) {
			this.outInfos = System.out;
		} else {
			File file = new File(logInfoPath);
			if (!file.exists()) {
				if (!file.createNewFile()) {
					throw new IOException("Cannot create logInfo file");
				}
			}
			this.outInfos = new PrintStream(file);
		}

		if (null == logWarningPath || logWarningPath.isEmpty()) {
			this.outWarning = System.err;
		} else {
			File file = new File(logWarningPath);
			if (!file.exists()) {
				if (!file.createNewFile()) {
					throw new IOException("Cannot create logWarning file");
				}
			}
			this.outWarning = new PrintStream(file);
		}

		if (null == logErrorPath || logErrorPath.isEmpty()) {
			this.outError = System.err;
		} else {
			File file = new File(logErrorPath);
			if (!file.exists()) {
				if (!file.createNewFile()) {
					throw new IOException("Cannot create logError file");
				}
			}
			this.outError = new PrintStream(file);
		}

	}

	public Logger() {
		this.outInfos = System.out;
		this.outWarning = System.err;
		this.outError = System.err;
	}

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
