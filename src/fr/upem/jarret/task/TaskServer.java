package fr.upem.jarret.task;

import java.util.concurrent.atomic.AtomicInteger;

public class TaskServer extends TaskWorker implements Comparable<TaskServer> {
	private String JobDescription;
	private int JobPriority;
	private final Object lock = new Object();

	public String getJobDescription() {
		return JobDescription;
	}

	public int getJobPriority() {
		return JobPriority;
	}

	public void decrementPriority() throws IllegalAccessException {
		synchronized (lock) {
			if (JobPriority == 0) {
				throw new IllegalAccessException(
						"Task priority cannot be negative.");
			}
			JobPriority--;
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("TaskWorker [jobId=").append(getJobId())
				.append(", WorkerVersionNumber=")
				.append(getWorkerVersionNumber()).append(", WorkerURL=")
				.append(getWorkerURL()).append(", WorkerClassName=")
				.append(getWorkerClassName()).append(", JobTaskNumber=")
				.append(getJobTaskNumber()).append("]")
				.append(", description=").append(getJobDescription())
				.append(", jobPriority=").append(getJobPriority()).append("]");
		return builder.toString();
	}

	@Override
	public int compareTo(TaskServer o) {
		return o.getJobPriority() - this.getJobPriority();
	}

	@Override
	public boolean isValid() {
		return super.isValid() && JobPriority >= 0;
	}

	public void incrementPriority() {
		synchronized (lock) {
			JobPriority++;
		}
	}
}
