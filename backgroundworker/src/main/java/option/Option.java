package option;

import java.lang.reflect.Type;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

public class Option {
	String name;
	JsonElement value;

	public Option() {
		name = "";
		value = null;
	}

	public Option(String name, String value) {
		this.name = name;
		this.value = new GsonBuilder().setPrettyPrinting().create().toJsonTree(value);
	}

	public void set(Object input) {
		value = new GsonBuilder().setPrettyPrinting().create().toJsonTree(input);
	}

	public String getName() {
		return name;
	}

	public <T> T get(Type typeOfT) throws JsonSyntaxException {
		if (value == null) {
			return null;
		}
		return new GsonBuilder().setPrettyPrinting().create().fromJson(value, typeOfT);
	}

	public boolean getBoolean() {
		try {
			return new GsonBuilder().setPrettyPrinting().create().fromJson(value, new TypeToken<Boolean>() {
			}.getType());
		} catch (Exception e) {
			return false;
		}
	}

	public int getInteger() {
		try {
			return new GsonBuilder().setPrettyPrinting().create().fromJson(value, new TypeToken<Integer>() {
			}.getType());
		} catch (Exception e) {
			return 0;
		}
	}

	public double getDouble() {
		try {
			return new GsonBuilder().setPrettyPrinting().create().fromJson(value, new TypeToken<Double>() {
			}.getType());
		} catch (Exception e) {
			return 0;
		}
	}

	public String getString() {
		try {
			return new GsonBuilder().setPrettyPrinting().create().fromJson(value, new TypeToken<String>() {
			}.getType());
		} catch (Exception e) {
			return "";
		}
	}

	public boolean equals(Option other) {
		return this.name.equals(other.name);
	}

	public boolean equals(String other) {
		return this.name.equals(other);
	}
}
