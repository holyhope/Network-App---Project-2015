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
}
