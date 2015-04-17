package upem.jarret.task;

import java.net.MalformedURLException;

import upem.jarret.worker.Worker;
import upem.jarret.worker.WorkerFactory;

public class Task {
	private String JobId;
	private String WorkerVersion;
	private String WorkerURL;
	private String WorkerClassName;
	private String Task;

	/**
	 * Get the JSon formated result of the task.
	 * 
	 * @return Result, ready to be sent to server.
	 */
	public byte[] getResult() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public String getJobId() {
		return JobId;
	}

	public Worker getWorker() throws MalformedURLException,
			ClassNotFoundException, IllegalAccessException,
			InstantiationException {
		return WorkerFactory.getWorker(WorkerURL, WorkerClassName);
	}
}
