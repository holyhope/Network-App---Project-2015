package upem.jarret.task;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;

public class TasksManager {
	private TaskServer[] tasks = new TaskServer[1];

	private TasksManager() {
	}

	public static TasksManager construct(String path)
			throws JsonParseException, JsonMappingException, IOException {
		TasksManager manager = new TasksManager();

		ObjectMapper mapper = new ObjectMapper();
		ArrayList<TaskServer> tasks = new ArrayList<>();

		// http://stackoverflow.com/questions/23469784/com-fasterxml-jackson-databind-exc-unrecognizedpropertyexception-unrecognized-f
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		mapper.setVisibilityChecker(VisibilityChecker.Std.defaultInstance()
				.withFieldVisibility(JsonAutoDetect.Visibility.ANY));

		try (Scanner scanner = new Scanner(new File("workerdescription.json"))) {
			StringBuilder stringBuilder = new StringBuilder();
			while (scanner.hasNextLine()) {
				String string = scanner.nextLine();
				stringBuilder.append(string);
				if (string.equals("")) {
					tasks.add(mapper.readValue(stringBuilder.toString(),
							TaskServer.class));
					stringBuilder = new StringBuilder();
				}
			}
			if (stringBuilder.length() != 0) {
				tasks.add(mapper.readValue(stringBuilder.toString(),
						TaskServer.class));
				stringBuilder = new StringBuilder();
			}
		}

		tasks.sort(null);
		manager.tasks = tasks.toArray(manager.tasks);

		return manager;
	}

	public TaskServer nextTask() throws NoTaskException {
		TaskServer task = tasks[0];
		try {
			task.decrementPriority();
		} catch (IllegalAccessException e) {
			throw new NoTaskException();
		}
		if (tasks.length == 1) {
			return task;
		}

		int priority = task.getJobPriority();
		int priorityNext = tasks[1].getJobPriority();
		if (priority >= priorityNext) {
			return task;
		}

		for (int i = 1; i < tasks.length; i++) {
			TaskServer taskTmp = tasks[i];
			if (taskTmp.getJobPriority() < priorityNext) {
				tasks[i] = task;
				tasks[0] = taskTmp;
			}
		}

		return task;
	}
}
