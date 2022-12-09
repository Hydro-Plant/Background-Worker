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
import com.google.gson.reflect.TypeToken;

public class PlantHandler {
	static MemoryPersistence pers;
	static MqttClient plant_client;

	double temp_value, light_value, ph_value, ec_value, flow_value, level_value;

	double temp_min, temp_opt, temp_tol, temp_max;
	double light_min, light_max;
	double ph_min, ph_opt, ph_tol, ph_max;
	double ec_min, ec_opt, ec_tol, ec_max;
	double max_level;
	double normal_flow;

	static Gson gson;

	public void setupGson() {
		gson = new GsonBuilder().setPrettyPrinting().create();
		System.out.println("Plant-Gson created");
	}

	public void setupMqtt() {
		try {
			pers = new MemoryPersistence();

			plant_client = new MqttClient("tcp://localhost:1883", "plant", pers);
			plant_client.connect();
			System.out.println("Plant-Client communication established");
			plant_client.subscribe(new String[] { "option/temperature", "option/maxLevel", "value/temperature",
					"value/light", "value/ph", "value/ec", "value/flow", "value/level" });

			System.out.println("Plant-Client subscriptions completed");
			plant_client.setCallback(new MqttCallback() {
				@Override
				public void messageArrived(String topic, MqttMessage message) throws Exception {
					switch (topic.toUpperCase()) {
					case "OPTION/TEMPERATURE":
						ArrayList<Double> temp_options = gson.fromJson(new String(message.getPayload()),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						temp_min = temp_options.get(0);
						temp_opt = temp_options.get(1);
						temp_tol = temp_options.get(2);
						temp_max = temp_options.get(3);
						break;
					case "OPTION/LIGHT":
						ArrayList<Double> light_options = gson.fromJson(new String(message.getPayload()),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						light_min = light_options.get(0);
						light_max = light_options.get(1);
						break;
					case "OPTION/PH":
						ArrayList<Double> ph_options = gson.fromJson(new String(message.getPayload()),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						ph_min = ph_options.get(0);
						ph_opt = ph_options.get(1);
						ph_tol = ph_options.get(2);
						ph_max = ph_options.get(3);
						break;
					case "OPTION/EC":
						ArrayList<Double> ec_options = gson.fromJson(new String(message.getPayload()),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						ec_min = ec_options.get(0);
						ec_opt = ec_options.get(1);
						ec_tol = ec_options.get(2);
						ec_max = ec_options.get(3);
						break;
					case "OPTION/MAXLEVEL":
						max_level = Double.parseDouble(new String(message.getPayload()));
						break;
					case "OPTION/FLOW":
						max_level = Double.parseDouble(new String(message.getPayload()));
						break;
					case "VALUE/TEMPERATURE":
						temp_value = Double.parseDouble(new String(message.getPayload()));
						break;
					case "VALUE/LEVEL":
						level_value = Double.parseDouble(new String(message.getPayload()));
						break;
					case "VALUE/FLOW":
						flow_value = Double.parseDouble(new String(message.getPayload()));
						break;
					}
					update();
				}

				@Override
				public void connectionLost(Throwable cause) {
					System.out.println("Plant Mqtt-Connection lost");
					System.out.println(cause.toString());
				}

				@Override
				public void deliveryComplete(IMqttDeliveryToken token) {

				}
			});

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void requestOptions() {
		try {
			plant_client.publish("option/get", new MqttMessage("maxLevel".getBytes()));
			plant_client.publish("option/get", new MqttMessage("temperature".getBytes()));
		} catch (MqttException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
		
	private void update() {
		// System.out.println("Min: " + temp_min);
		// System.out.println("Optimal: " + temp_opt);
		// System.out.println("Tol: " + temp_tol);
		// System.out.println("Max: " + temp_max);

		// ---------------------- Checking temperature
		if ((temp_value > temp_max || temp_value < temp_min) && level_value >= 3) {
			try {
				plant_client.publish("warning/temperature", new MqttMessage("true".getBytes()));
			} catch (MqttException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			String result = "";

			if (temp_value > temp_max) {
				result += "Die Wassertemperature ist zu warm. ";

				// T = (t1 * m1 + t2 * m2) / (m1 + m2)

				double t1 = 0;
				double t2 = temp_value;
				double T = temp_opt;
				double m2 = level_value;

				double m1 = m2 * (t2 - T) / (T - t1);
				if (m1 + level_value > max_level) {
					T = temp_opt + temp_tol;
					m1 = m2 * (t2 - T) / (T - t1);
					if (m1 + level_value > max_level) {
						double remove_water = max_level * (T - t2) / (t1 - t2);
						result += String.format(
								"Bitte entferne %.2fl aus dem Tank und füll den Tank mit 0° kaltem Wasser. ",
								level_value - (max_level - remove_water));
					} else {
						result += String.format(
								"Bitte füge %.2fl 0° kaltes Wasser hinzu, um innerhalb der Optimalgrenzen zu kommen. ",
								(max_level - level_value));
					}
				} else {
					result += String.format(
							"Bitte füge %.2fl 0° kaltes Wasser hinzu, um die optimale Wassertemperatur zu erreichen. ",
							m1);
				}
			} else {
				result += "Die Wassertemperature ist zu warm. ";

				// T = (t1 * m1 + t2 * m2) / (m1 + m2)

				double t1 = 100;
				double t2 = temp_value;
				double T = temp_opt;
				double m2 = level_value;

				double m1 = m2 * (t2 - T) / (T - t1);
				if (m1 + level_value > max_level) {
					T = temp_opt - temp_tol;
					m1 = m2 * (t2 - T) / (T - t1);
					if (m1 + level_value > max_level) {
						double remove_water = max_level * (T - t2) / (t1 - t2);
						result += String.format(
								"Bitte entferne %.2fl aus dem Tank und füll den Tank mit 100° heißem Wasser. ",
								level_value - (max_level - remove_water));
					} else {
						result += String.format(
								"Bitte füge %.2fl 100° heißes Wasser hinzu, um innerhalb der Optimalgrenzen zu kommen. ",
								(max_level - level_value));
					}
				} else {
					result += String.format(
							"Bitte füge %.2fl 100° heißes Wasser hinzu, um die optimale Wassertemperatur zu erreichen. ",
							m1);
				}
			}

			try {
				plant_client.publish("warningtext/temperature", new MqttMessage(result.getBytes()));
			} catch (MqttException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			try {
				plant_client.publish("warning/temperature", new MqttMessage("false".getBytes()));
			} catch (MqttException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// Checking 
	}
}
