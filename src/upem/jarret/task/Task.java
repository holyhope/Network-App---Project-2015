package upem.jarret.task;

import java.util.HashMap;
import java.util.Map;

public class Task {
	private String JobId;
	private String WorkerVersionNumber;
	private String WorkerURL;
	private String WorkerClassName;
	private String JobTaskNumber;

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

	public boolean isValid() {
		return JobId != null && WorkerVersionNumber != null
				&& WorkerURL != null && WorkerClassName != null
				&& JobTaskNumber != null;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Task)) {
			return false;
		}
		Task task = (Task) obj;
		return task.JobId.equals(JobId)
				&& task.WorkerVersionNumber.equals(WorkerVersionNumber)
				&& task.WorkerURL.equals(WorkerURL)
				&& task.WorkerClassName.equals(WorkerClassName)
				&& task.JobTaskNumber.equals(JobTaskNumber);
	}
}