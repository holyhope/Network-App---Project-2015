package upem.jarret.task;

import java.util.HashMap;
import java.util.Map;

public class Task {
	public String JobId;
	public String WorkerVersionNumber;
	public String WorkerURL;
	public String WorkerClassName;
	public String JobTaskNumber;

	public Task() {
	}

	public String getJobId() {
		return JobId;
	}

	public String getWorkerVersionNumber() {
		return WorkerVersionNumber;
	}

	public String getWorkerURL() {
		return WorkerURL;
	}

	public String getWorkerClassName() {
		return WorkerClassName;
	}

	public String getJobTaskNumber() {
		return JobTaskNumber;
	}

	public Map<String, Object> buildMap() {
		HashMap<String, Object> map = new HashMap<>();
		map.put("JobId", JobId);
		map.put("WorkerVersionNumber", getWorkerVersionNumber());
		map.put("WorkerURL", getWorkerURL());
		map.put("WorkerClassName", getWorkerClassName());
		map.put("JobTaskNumber", getJobTaskNumber());
		return map;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("TaskWorker [JobId=").append(getJobId())
				.append(", WorkerVersionNumber=")
				.append(getWorkerVersionNumber()).append(", WorkerURL=")
				.append(getWorkerURL()).append(", WorkerClassName=")
				.append(getWorkerClassName()).append(", Task=")
				.append(getJobTaskNumber()).append("]");
		return builder.toString();
	}
}