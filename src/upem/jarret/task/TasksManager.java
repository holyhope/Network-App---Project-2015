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
	private final ArrayList<TaskServer> tasks = new ArrayList<>();

	private TasksManager() {
	}

	public static TasksManager construct(String path)
			throws JsonParseException, JsonMappingException, IOException {
		TasksManager manager = new TasksManager();

		ObjectMapper mapper = new ObjectMapper();

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
					manager.tasks.add(mapper.readValue(
							stringBuilder.toString(), TaskServer.class));
					stringBuilder = new StringBuilder();
				}
			}
			if (stringBuilder.length() != 0) {
				manager.tasks.add(mapper.readValue(stringBuilder.toString(),
						TaskServer.class));
				stringBuilder = new StringBuilder();
			}
		}

		manager.tasks.sort(null);
		System.out.println("Tasks " + manager.tasks);

		return manager;
	}

	public TaskServer nextTask() throws NoTaskException {
		TaskServer task = tasks.get(0);
		try {
			task.decrementPriority();
		} catch (IllegalAccessException e) {
			throw new NoTaskException();
		}
		tasks.sort(null);
		return task;
	}
}
