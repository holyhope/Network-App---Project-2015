package upem.jarret.task;

public class TaskServer extends TaskWorker implements Comparable<TaskServer> {
	private String JobDescription;
	private int JobPriority;

	public String getJobDescription() {
		return JobDescription;
	}

	public int getJobPriority() {
		return JobPriority;
	}

	public void decrementPriority() throws IllegalAccessException {
		if (JobPriority == 0) {
			throw new IllegalAccessException(
					"Task priority cannot be negative.");
		}
		JobPriority--;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("TaskWorker [jobId=").append(getJobId())
				.append(", workerVersion=").append(getWorkerVersionNumber())
				.append(", workerURL=").append(getWorkerURL())
				.append(", workerClassName=").append(getWorkerClassName())
				.append(", task=").append(getJobTaskNumber()).append("]")
				.append(", description=").append(getJobDescription())
				.append(", jobPriority=").append(getJobPriority()).append("]");
		return builder.toString();
	}

	@Override
	public int compareTo(TaskServer o) {
		return o.getJobPriority() - this.getJobPriority();
	}

	public boolean isValid() {
		return super.isValid() && JobPriority >= 0;
	}

	public void incrementPriority() {
		JobPriority++;
	}
}
