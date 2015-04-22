package upem.jarret.worker;

public enum YesNo {
	YES, NO;

	public String toString() {
		switch (this) {
		case YES:
			return "Yes";
		case NO:
			return "No";
		default:
			throw new IllegalStateException("Not a yes/no");
		}
	}

	public static YesNo fromString(String string) {
		switch (string) {
		case "Yes":
			return YES;
		case "No":
			return NO;
		default:
			throw new IllegalArgumentException(string + " is not a valid YesNo");
		}
	}
};