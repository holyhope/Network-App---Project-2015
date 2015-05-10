package fr.upem.logger;

import java.io.File;
import java.io.FileNotFoundException;
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

	private Logger(PrintStream logInfoPath, PrintStream logWarningPath, PrintStream logErrorPath)
			throws IOException {
		this.outInfos = logInfoPath;
		this.outWarning = logWarningPath;
		this.outError = logErrorPath;

	}

	public static Logger construct(String logInfoPath, String logWarningPath,
			String logErrorPath) throws IOException, FileNotFoundException {
		PrintStream outInfos;
		PrintStream outWarning;
		PrintStream outError;
		if (null == logInfoPath || logInfoPath.isEmpty()) {
			outInfos = System.out;
		} else {
			File file = new File(logInfoPath);
			if (!file.exists()) {
				file.getParentFile().mkdirs();
				if (!file.createNewFile()) {
					throw new IOException("Cannot create logInfo file");
				}
			}
			outInfos = new PrintStream(file);
		}

		if (null == logWarningPath || logWarningPath.isEmpty()) {
			outWarning = System.err;
		} else {
			File file = new File(logWarningPath);
			if (!file.exists()) {
				file.getParentFile().mkdirs();
				if (!file.createNewFile()) {
					throw new IOException("Cannot create logWarning file");
				}
			}
			outWarning = new PrintStream(file);
		}

		if (null == logErrorPath || logErrorPath.isEmpty()) {
			outError = System.err;
		} else {
			File file = new File(logErrorPath);
			if (!file.exists()) {
				file.getParentFile().mkdirs();
				if (!file.createNewFile()) {
					throw new IOException("Cannot create logError file");
				}
			}
			outError = new PrintStream(file);
		}
		return new Logger(outInfos, outWarning, outError);
	}
	
	public static Logger construct() throws IOException {
		return new Logger(System.out, System.err, System.err);
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
