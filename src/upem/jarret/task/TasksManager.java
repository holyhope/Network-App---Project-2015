package upem.jarret.task;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Scanner;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;

public class TasksManager {
	private final ArrayList<TaskServer> tasks = new ArrayList<>();

	public TasksManager() {
	}

	public TaskServer nextTask() throws NoTaskException {
		tasks.sort(null);
		if (tasks.isEmpty()) {
			throw new NoTaskException();
		}
		TaskServer task = tasks.get(0);
		try {
			task.decrementPriority();
		} catch (IllegalAccessException e) {
			throw new NoTaskException();
		}
		return task;
	}

	public void info(PrintStream out) {
		tasks.stream().forEach(
				t -> out.println("JobId: " + t.getJobId() + "    "
						+ "JobTaskNumber: " + t.getJobTaskNumber() + "    "
						+ "JobPriority: " + t.getJobPriority()));
	}

	public boolean addTask(TaskServer task) {
		return tasks.add(task);
	}

	public void addTaskFromFile(String file) throws JsonParseException,
			JsonMappingException, IOException {
		ObjectMapper mapper = new ObjectMapper();

		// http://stackoverflow.com/questions/23469784/com-fasterxml-jackson-databind-exc-unrecognizedpropertyexception-unrecognized-f
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		mapper.setVisibilityChecker(VisibilityChecker.Std.defaultInstance()
				.withFieldVisibility(JsonAutoDetect.Visibility.ANY));

		try (Scanner scanner = new Scanner(new File(file))) {
			StringBuilder stringBuilder = new StringBuilder();
			while (scanner.hasNextLine()) {
				String string = scanner.nextLine();
				stringBuilder.append(string);
				if (string.equals("")) {
					addTask(mapper.readValue(stringBuilder.toString(),
							TaskServer.class));
					stringBuilder = new StringBuilder();
				}
			}
			if (stringBuilder.length() != 0) {
				addTask(mapper.readValue(stringBuilder.toString(),
						TaskServer.class));
				stringBuilder = new StringBuilder();
			}
		}

	}
}
