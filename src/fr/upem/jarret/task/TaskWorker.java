package fr.upem.jarret.task;

import java.net.MalformedURLException;

import fr.upem.jarret.worker.Worker;
import fr.upem.jarret.worker.WorkerFactory;

public class TaskWorker extends Task {
	public Worker getWorker() throws MalformedURLException,
			ClassNotFoundException, IllegalAccessException,
			InstantiationException {
		return WorkerFactory.getWorker(getWorkerURL(), getWorkerClassName());
	}
}
