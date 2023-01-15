package std;

public class std {
	public static void INFO(Object that, String msg) {
		System.out.println("INFO: " + that.getClass().getSimpleName() + ": " + msg);
	}
	public static void INFO(String that, String msg) {
		System.out.println("INFO: " + that + ": " + msg);
	}
}
