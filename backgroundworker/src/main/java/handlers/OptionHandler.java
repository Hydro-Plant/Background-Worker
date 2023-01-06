package handlers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import option.Option;
import std.std;

public class OptionHandler {
	File saves = new File("saves/options.save");

	static MemoryPersistence pers;
	static MqttClient option_client;

	static Gson gson;

	static boolean save_file = false;
	static ArrayList<String> requests = new ArrayList<String>();
	static ArrayList<Option> options;

	public void setupGson() {
		gson = new GsonBuilder().setPrettyPrinting().create();
		std.INFO(this, "Gson created");
	}

	public void setupSave() throws IOException {
		if (!saves.exists()) {
			try {
				saves.createNewFile();
				FileWriter fw = new FileWriter(saves.getAbsolutePath());
				fw.write("[\n]");
				fw.close();
			} catch (IOException e) {
				std.INFO(this, "Option Save-File couldn't be built");
				e.printStackTrace();
			}
		}

		String option_string = "";
		try {
			FileReader fr = new FileReader(saves.getAbsolutePath());
			boolean end_reached = false;
			while (!end_reached) {
				char x = (char) fr.read();
				if (x == (char) -1)
					end_reached = true;
				else
					option_string += x;
			}
			fr.close();
		} catch (FileNotFoundException e) {
			std.INFO(this, "Option File not found");
			e.printStackTrace();
		} catch (IOException e) {
			std.INFO(this, "Option File failed");
			e.printStackTrace();
		}
		std.INFO(this, "Options-File read");
		options = new ArrayList<Option>();
		options = gson.fromJson(option_string, new TypeToken<ArrayList<Option>>() {
		}.getType());
	}
	

	public void setupMqtt() {
		try {
			pers = new MemoryPersistence();

			option_client = new MqttClient("tcp://localhost:1883", "option", pers);
			option_client.connect();
			std.INFO(this, "Mqtt-communication established");
			option_client.subscribe(new String[] { "option/get", "option/set" });
			std.INFO(this, "Subscriptions added");
			option_client.setCallback(new MqttCallback() {
				@Override
				public void messageArrived(String topic, MqttMessage message) throws Exception {
					switch (topic.toUpperCase()) {
					case "OPTION/GET":
						requests.add(new String(message.getPayload()));
						break;
					case "OPTION/SET":
						if (message != null) {
							Option data = gson.fromJson(new String(message.getPayload()), Option.class);
							boolean option_found = false;
							for(int x = 0; x < options.size(); x++) {
								if(data.equals(options.get(x))) {
									option_found = true;
									options.set(x, data);
									break;
								}
							}
							if(!option_found) {
								options.add(data);
							}
							requests.add(data.getName());
							save_file = true;
						}
						break;
					}
				}

				
				@Override
				public void connectionLost(Throwable cause) {
					std.INFO(this, "Mqtt-connection lost");
					std.INFO(this, cause.toString());
				}

				@Override
				public void deliveryComplete(IMqttDeliveryToken token) {

				}
			});

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void handle() {
		if (requests.size() > 0) {
			Option requested = null;
			for(int x = 0; x < options.size(); x++) {
				if(options.get(x).equals(requests.get(0))) {
					requested = options.get(x);
				}
			}
			if(requested != null) {
				try {
					option_client.publish("option/" + requested.getName(), new MqttMessage(requested.getString().getBytes()));
				} catch (MqttException e) {
					e.printStackTrace();
				}
			}
			if(requests.size() > 0) requests.remove(0);
		}
		if(save_file) {
			save_file = false;
			try {
				FileWriter fw = new FileWriter(saves.getAbsolutePath());
				fw.write(gson.toJson(options));
				fw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
