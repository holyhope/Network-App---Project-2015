package fr.upem.jarret.task;

import java.util.concurrent.atomic.AtomicInteger;

public class TaskServer extends TaskWorker implements Comparable<TaskServer> {
	private String JobDescription;
	private final AtomicInteger JobPriority = new AtomicInteger();

	public String getJobDescription() {
		return JobDescription;
	}

	public int getJobPriority() {
		return JobPriority.get();
	}

	public void decrementPriority() throws IllegalAccessException {
		if (JobPriority.getAndDecrement() == 0) {
			JobPriority.incrementAndGet();
			throw new IllegalAccessException(
					"Task priority cannot be negative.");
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
		return super.isValid() && JobPriority.get() >= 0;
	}

	public void incrementPriority() {
		JobPriority.incrementAndGet();
	}
}
