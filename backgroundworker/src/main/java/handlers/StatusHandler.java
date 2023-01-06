package handlers;

import java.util.ArrayList;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import status.Status;
import std.std;

public class StatusHandler {
	static MemoryPersistence pers;
	static MqttClient status_client;

	static Gson gson;

	static boolean save_file = false;
	static ArrayList<String> requests = new ArrayList<String>();
	static ArrayList<Status> statuseses = new ArrayList<Status>();

	public void setupGson() {
		gson = new GsonBuilder().setPrettyPrinting().create();
		std.INFO(this, "Gson created");
	}

	public void setupMqtt() {
		try {
			pers = new MemoryPersistence();

			status_client = new MqttClient("tcp://localhost:1883", "status", pers);
			status_client.connect();
			std.INFO(this, "Mqtt-communication established");
			status_client.subscribe(new String[] { "status/get", "status/set" });
			std.INFO(this, "Subscriptions added");
			status_client.setCallback(new MqttCallback() {
				@Override
				public void messageArrived(String topic, MqttMessage message) throws Exception {
					switch (topic.toUpperCase()) {
					case "STATUS/GET":
						requests.add(new String(message.getPayload()));
						break;
					case "STATUS/SET":
						if (message != null) {
							Status data = gson.fromJson(new String(message.getPayload()), Status.class);
							boolean status_found = false;
							for (int x = 0; x < statuseses.size(); x++) {
								if (data.equals(statuseses.get(x))) {
									status_found = true;
									statuseses.set(x, data);
									break;
								}
							}
							if (!status_found) {
								statuseses.add(data);
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
			Status requested = null;
			for (int x = 0; x < statuseses.size(); x++) {
				if (statuseses.get(x).equals(requests.get(0))) {
					requested = statuseses.get(x);
				}
			}
			if (requested != null) {
				try {
					status_client.publish("status/" + requested.getName(),
							new MqttMessage(requested.getString().getBytes()));
				} catch (MqttException e) {
					e.printStackTrace();
				}
			}
			if (requests.size() > 0)
				requests.remove(0);
		}
	}
}
