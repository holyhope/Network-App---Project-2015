package upem.jarret.task;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import upem.jarret.worker.Worker;
import upem.jarret.worker.WorkerFactory;

public class Task {
	private String JobId;
	private String WorkerVersion;
	private String WorkerURL;
	private String WorkerClassName;
	private String Task;
	
	
	
	public String getJobId() {
		return JobId;
	}
	
	public String getWorkerVersion() {
		return WorkerVersion;
	}

	public String getWorkerURL() {
		return WorkerURL;
	}

	public String getWorkerClassName() {
		return WorkerClassName;
	}

	public String getTask() {
		return Task;
	}

	public Worker getWorker() throws MalformedURLException,
			ClassNotFoundException, IllegalAccessException,
			InstantiationException {
		return WorkerFactory.getWorker(WorkerURL, WorkerClassName);
	}
	
	public Map<String,String> buildMap() {
		HashMap<String, String> map = new HashMap<>();
		map.put("JobId", JobId);
		map.put("WorkerVersion", WorkerVersion);
		map.put("WorkerURL", WorkerURL);
		map.put("WorkerClassName", WorkerClassName);
		map.put("Task", Task);
		return map;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Task [jobId=");
		builder.append(JobId);
		builder.append(", workerVersion=");
		builder.append(WorkerVersion);
		builder.append(", workerURL=");
		builder.append(WorkerURL);
		builder.append(", workerClassName=");
		builder.append(WorkerClassName);
		builder.append(", task=");
		builder.append(Task);
		builder.append("]");
		return builder.toString();
	}
	
	
}
