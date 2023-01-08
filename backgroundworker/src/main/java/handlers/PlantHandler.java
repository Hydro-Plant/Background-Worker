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

import std.std;

public class PlantHandler {
	static MemoryPersistence pers;
	static MqttClient plant_client;

	double temp_value, light_value, ph_value, ec_value, tds_value, flow_value, level_value;

	double temp_min, temp_opt, temp_tol, temp_max;
	double light_min, light_max;
	double ph_min, ph_opt, ph_tol, ph_max;
	double ec_min, ec_opt, ec_tol, ec_max;
	double tds_min, tds_opt, tds_tol, tds_max;
	double max_level, min_measuring;
	double normal_flow, flow_tol;

	String ec_or_tds = "ec";

	static Gson gson;

	public void setupGson() {
		gson = new GsonBuilder().setPrettyPrinting().create();
		std.INFO(this, "Gson created");
	}

	public void setupMqtt() {
		try {
			pers = new MemoryPersistence();
			plant_client = new MqttClient("tcp://localhost:1883", "plant", pers);
			plant_client.connect();
			std.INFO(this, "Mqtt-communication established");
			plant_client.subscribe(new String[] { "option/temperature", "option/light", "option/ph", "option/ec",
					"option/tds", "option/level", "option/flow", "option/ec_or_tds", "value/temperature", "value/light",
					"value/ph", "value/ec", "value/tds", "value/flow", "value/level" }, new int[] { 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2 });
			std.INFO(this, "Subscriptions added");
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

						tds_min = ec_options.get(0) * 500;
						tds_opt = ec_options.get(1) * 500;
						tds_tol = ec_options.get(2) * 500;
						tds_max = ec_options.get(3) * 500;
						break;
					case "OPTION/TDS":
						ArrayList<Double> tds_options = gson.fromJson(new String(message.getPayload()),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						tds_min = tds_options.get(0);
						tds_opt = tds_options.get(1);
						tds_tol = tds_options.get(2);
						tds_max = tds_options.get(3);

						ec_min = tds_options.get(0) / 500;
						ec_opt = tds_options.get(1) / 500;
						ec_tol = tds_options.get(2) / 500;
						ec_max = tds_options.get(3) / 500;
						break;
					case "OPTION/LEVEL":
						ArrayList<Double> level_options = gson.fromJson(new String(message.getPayload()),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						max_level = level_options.get(0);
						min_measuring = level_options.get(1);
						break;
					case "OPTION/FLOW":
						ArrayList<Double> flow_options = gson.fromJson(new String(message.getPayload()),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						normal_flow = flow_options.get(0);
						flow_tol = flow_options.get(1);
						break;
					case "OPTION/EC_OR_TDS":
						ec_or_tds = message.toString();
						break;

					case "VALUE/TEMPERATURE":
						temp_value = Double.parseDouble(new String(message.getPayload()));
						break;
					case "VALUE/LIGHT":
						light_value = Double.parseDouble(new String(message.getPayload()));
						break;
					case "VALUE/PH":
						ph_value = Double.parseDouble(new String(message.getPayload()));
						break;
					case "VALUE/EC":
						ec_value = Double.parseDouble(new String(message.getPayload()));
						tds_value = ec_value * 500;
						break;
					case "VALUE/TDS":
						tds_value = Double.parseDouble(new String(message.getPayload()));
						ec_value = tds_value / 500;
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
					std.INFO(this, "Mqtt-connection lost");
					std.INFO(this, cause.toString());
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
			plant_client.publish("option/get", new MqttMessage("temperature".getBytes()));
			plant_client.publish("option/get", new MqttMessage("light".getBytes()));
			plant_client.publish("option/get", new MqttMessage("ph".getBytes()));
			plant_client.publish("option/get", new MqttMessage("ec".getBytes()));
			plant_client.publish("option/get", new MqttMessage("tds".getBytes()));
			plant_client.publish("option/get", new MqttMessage("level".getBytes()));
			plant_client.publish("option/get", new MqttMessage("flow".getBytes()));
			plant_client.publish("option/get", new MqttMessage("ec_or_tds".getBytes()));
		} catch (MqttException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void update() {
		// ------------------------------------------------------------------------------------------------------------------------------------ Checking temperature
		if ((temp_value > temp_max || temp_value < temp_min) && level_value >= min_measuring) {
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
				result += "Die Wassertemperature ist zu kalt. ";

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
		
		// ------------------------------------------------------------------------------------------------------------------------------------ Checking pH
		
		if ((ph_value > ph_max || ph_value < ph_min) && level_value >= min_measuring) {
			try {
				plant_client.publish("warning/ph", new MqttMessage("true".getBytes()));
			} catch (MqttException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			String result = "";

			if (ph_value > ph_max) {
				result += "Der pH-Wert des Wassers ist zu hoch. ";

				// PH = (ph1 * m1 + ph2 * m2) / (m1 + m2)

				double ph1 = 0;
				double ph2 = ph_value;
				double PH = ph_opt;
				double m2 = level_value;

				double m1 = m2 * (ph2 - PH) / (PH - ph1);
				if (m1 + level_value > max_level) {
					PH = ph_opt + ph_tol;
					m1 = m2 * (ph2 - PH) / (PH - ph1);
					if (m1 + level_value > max_level) {
						double remove_water = max_level * (PH - ph2) / (ph1 - ph2);
						result += String.format(
								"Bitte entferne %.2fl aus dem Tank und füll den Tank mit der pH-Down Lösung. ",
								level_value - (max_level - remove_water));
					} else {
						result += String.format(
								"Bitte füge %.2fl der pH-Down Lösung hinzu, um innerhalb der Optimalgrenzen zu kommen. ",
								(max_level - level_value));
					}
				} else {
					result += String.format(
							"Bitte füge %.2fl der pH-Down Lösung hinzu, um den optimale pH-Wert zu erreichen. ",
							m1);
				}
			} else {
				result += "Der pH-Wert des Wassers ist zu niedrig. ";

				// PH = (ph1 * m1 + ph2 * m2) / (m1 + m2)

				double ph1 = 100;
				double ph2 = ph_value;
				double PH = ph_opt;
				double m2 = level_value;

				double m1 = m2 * (ph2 - PH) / (PH - ph1);
				if (m1 + level_value > max_level) {
					PH = ph_opt - ph_tol;
					m1 = m2 * (ph2 - PH) / (PH - ph1);
					if (m1 + level_value > max_level) {
						double remove_water = max_level * (PH - ph2) / (ph1 - ph2);
						result += String.format(
								"Bitte entferne %.2fl aus dem Tank und füll den Tank mit der pH-Up Lösung. ",
								level_value - (max_level - remove_water));
					} else {
						result += String.format(
								"Bitte füge %.2fl der pH-Up Lösung hinzu, um innerhalb der Optimalgrenzen zu kommen. ",
								(max_level - level_value));
					}
				} else {
					result += String.format(
							"Bitte füge %.2fl der pH-Up Lösung hinzu, um den optimale pH-Wert zu erreichen. ",
							m1);
				}
			}

			try {
				plant_client.publish("warningtext/ph", new MqttMessage(result.getBytes()));
			} catch (MqttException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			try {
				plant_client.publish("warning/ph", new MqttMessage("false".getBytes()));
			} catch (MqttException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// ------------------------------------------------------------------------------------------------------------------------------------ Checking EC or TDS
		
		if ((tds_value > tds_max || tds_value < tds_min) && level_value >= min_measuring) {
			try {
				plant_client.publish("warning/" + ec_or_tds, new MqttMessage("true".getBytes()));
			} catch (MqttException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			String result = "";

			if (tds_value > tds_max) {
				result += "Der " + ec_or_tds.toUpperCase() + "-Wert des Wassers ist zu hoch. ";

				// TDS = (tds1 * m1 + tds2 * m2) / (m1 + m2)

				double tds1 = 0;
				double tds2 = tds_value;
				double TDS = tds_opt;
				double m2 = level_value;

				double m1 = m2 * (tds2 - TDS) / (TDS - tds1);
				if (m1 + level_value > max_level) {
					TDS = tds_opt + tds_tol;
					m1 = m2 * (tds2 - TDS) / (TDS - tds1);
					if (m1 + level_value > max_level) {
						double remove_water = max_level * (TDS - tds2) / (tds1 - tds2);
						result += String.format(
								"Bitte entferne %.2fl aus dem Tank und füll den Tank mit destilliertem Wasser. ",
								level_value - (max_level - remove_water));
					} else {
						result += String.format(
								"Bitte füge %.2fl an destilliertem Wasser hinzu, um innerhalb der Optimalgrenzen zu kommen. ",
								(max_level - level_value));
					}
				} else {
					result += String.format(
							"Bitte füge %.2fl an destilliertem Wasser hinzu, um den optimale " + ec_or_tds.toUpperCase() + "-Wert zu erreichen. ",
							m1);
				}
			} else {
				result += "Der " + ec_or_tds.toUpperCase() + "-Wert des Wassers ist zu niedrig. ";

				// TDS = (tds1 * m1 + tds2 * m2) / (m1 + m2)

				double tds2 = tds_value;
				double TDS = tds_opt;
				double m2 = level_value;

				double m1 = m2 * (TDS - tds2);
				
				result += "Füge " + m1 + "mg Salz hinzu, um den optimalen " + ec_or_tds.toUpperCase() + "-Wert zu erreichen.";
				
			}

			try {
				plant_client.publish("warningtext/" + ec_or_tds, new MqttMessage(result.getBytes()));
			} catch (MqttException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			try {
				plant_client.publish("warning/" + ec_or_tds, new MqttMessage("false".getBytes()));
			} catch (MqttException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
