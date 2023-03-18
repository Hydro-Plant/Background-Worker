package handlers;

import java.util.ArrayList;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import std.std;

public class PlantHandler {
	static MemoryPersistence pers;
	static MqttClient plant_client;

	double temp_value, light_value, ph_value, ec_value, tds_value, flow_value, level_value;

	boolean pump_status;

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
			MqttConnectOptions mqtt_opt = new MqttConnectOptions();
			mqtt_opt.setMaxInflight(50);
			plant_client = new MqttClient("tcp://localhost:1883", "plant", pers);
			plant_client.connect(mqtt_opt);
			
			std.INFO(this, "Mqtt-communication established");
			plant_client.subscribe(new String[] { "option/temperature", "option/light", "option/ph", "option/ec", "option/tds",
								"option/level", "option/flow", "option/ec_or_tds", "value/pump", "value/temperature",
								"value/light", "value/ph", "value/ec", "value/tds", "value/flow", "value/level" });
			std.INFO(this, "Subscriptions added");
			plant_client.setCallback(new MqttCallback() {
				@Override
				public void messageArrived(String topic, MqttMessage message) throws Exception {
					switch (topic.toUpperCase()) {
					case "OPTION/TEMPERATURE":
						ArrayList<Double> temp_options = gson.fromJson(message.toString(),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						temp_min = temp_options.get(0);
						temp_opt = temp_options.get(1);
						temp_tol = temp_options.get(2);
						temp_max = temp_options.get(3);
						break;
					case "OPTION/LIGHT":
						ArrayList<Double> light_options = gson.fromJson(message.toString(),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						light_min = light_options.get(0);
						light_max = light_options.get(1);
						break;
					case "OPTION/PH":
						ArrayList<Double> ph_options = gson.fromJson(message.toString(),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						ph_min = ph_options.get(0);
						ph_opt = ph_options.get(1);
						ph_tol = ph_options.get(2);
						ph_max = ph_options.get(3);
						break;
					case "OPTION/EC":
						ArrayList<Double> ec_options = gson.fromJson(message.toString(),
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
						ArrayList<Double> tds_options = gson.fromJson(message.toString(),
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
						ArrayList<Double> level_options = gson.fromJson(message.toString(),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						max_level = level_options.get(0);
						min_measuring = level_options.get(1);
						break;
					case "OPTION/FLOW":
						ArrayList<Double> flow_options = gson.fromJson(message.toString(),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						normal_flow = flow_options.get(0);
						flow_tol = flow_options.get(1);
						break;
					case "OPTION/EC_OR_TDS":
						ec_or_tds = message.toString();
						break;

					case "VALUE/PUMP":
						pump_status = Boolean.parseBoolean(message.toString());
						break;

					case "VALUE/TEMPERATURE":
						temp_value = Double.parseDouble(message.toString());
						break;
					case "VALUE/LIGHT":
						light_value = Double.parseDouble(message.toString());
						break;
					case "VALUE/PH":
						ph_value = Double.parseDouble(message.toString());
						break;
					case "VALUE/EC":
						ec_value = Double.parseDouble(message.toString());
						tds_value = ec_value * 500;
						break;
					case "VALUE/TDS":
						tds_value = Double.parseDouble(message.toString());
						ec_value = tds_value / 500;
						break;
					case "VALUE/LEVEL":
						level_value = Double.parseDouble(message.toString());
						break;
					case "VALUE/FLOW":
						flow_value = Double.parseDouble(message.toString());
						break;
					}
					try {
						update();
					} catch(Exception e) {
						
					}
				}

				@Override
				public void connectionLost(Throwable cause) {
					std.INFO("PlantHandler", "Mqtt-connection lost");
					std.INFO("PlantHandler", cause.toString());
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

	private void update() throws MqttPersistenceException, MqttException {
		// ------------------------------------------------------------------------------------------------------------------------------------
		// Checking temperature
		if ((temp_value > temp_max || temp_value < temp_min) && level_value >= min_measuring) {
			plant_client.publish("warning/temperature", new MqttMessage("true".getBytes()));
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
			plant_client.publish("warningtext/temperature", new MqttMessage(result.getBytes()));
		} else {
			plant_client.publish("warning/temperature", new MqttMessage("false".getBytes()));
		}

		// ------------------------------------------------------------------------------------------------------------------------------------
		// Checking pH

		if ((ph_value > ph_max || ph_value < ph_min) && level_value >= min_measuring) {
			plant_client.publish("warning/ph", new MqttMessage("true".getBytes()));
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
							"Bitte füge %.2fl der pH-Down Lösung hinzu, um den optimale pH-Wert zu erreichen. ", m1);
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
							"Bitte füge %.2fl der pH-Up Lösung hinzu, um den optimale pH-Wert zu erreichen. ", m1);
				}
			}

			plant_client.publish("warningtext/ph", new MqttMessage(result.getBytes()));
		} else {
			plant_client.publish("warning/ph", new MqttMessage("false".getBytes()));
		}

		// ------------------------------------------------------------------------------------------------------------------------------------
		// Checking EC or TDS

		if ((tds_value > tds_max || tds_value < tds_min) && level_value >= min_measuring) {
			plant_client.publish("warning/" + ec_or_tds, new MqttMessage("true".getBytes()));
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
					result += String.format("Bitte füge %.2fl an destilliertem Wasser hinzu, um den optimale "
							+ ec_or_tds.toUpperCase() + "-Wert zu erreichen. ", m1);
				}
			} else {
				result += "Der " + ec_or_tds.toUpperCase() + "-Wert des Wassers ist zu niedrig. ";

				// TDS = (tds1 * m1 + tds2 * m2) / (m1 + m2)

				double tds2 = tds_value;
				double TDS = tds_opt;
				double m2 = level_value;

				double m1 = m2 * (TDS - tds2);

				result += "Füge " + String.format("%.2f", m1) + "mg Salz hinzu, um den optimalen " + ec_or_tds.toUpperCase()
						+ "-Wert zu erreichen.";

			}

			plant_client.publish("warningtext/" + ec_or_tds, new MqttMessage(result.getBytes()));
		} else {
			plant_client.publish("warning/" + ec_or_tds, new MqttMessage("false".getBytes()));
		}

		// ------------------------------------------------------------------------------------------------------------------------------------
		// Checking Flow

		if ((!this.pump_status && this.flow_value > this.flow_tol)
				|| (this.pump_status && (this.flow_value > this.normal_flow + this.flow_tol
						|| this.flow_value < this.normal_flow - this.flow_tol))) {
			String result = "";
			if (!this.pump_status && this.flow_value > this.flow_tol) {
				result = "Die Pumpe ist aus, jedoch ist der Durchfluss nicht 0. ";
			} else if (this.pump_status && this.flow_value > this.normal_flow + this.flow_tol) {
				result = "Die Pumpe ist an, jedoch ist der Durchfluss zu hoch. ";
			} else {
				result = "Die Pumpe ist an, jedoch ist der Durchfluss zu niedrig. ";
			}
			result += "Bitte überprüf die Pumpe und den Durchflussensor. ";

			plant_client.publish("warning/flow", new MqttMessage("true".getBytes()));
			plant_client.publish("warningtext/flow", new MqttMessage(result.getBytes()));
		} else {
			plant_client.publish("warning/flow", new MqttMessage("false".getBytes()));
		}

		// ------------------------------------------------------------------------------------------------------------------------------------
		// Checking Light
		
		if(this.light_value > this.light_max || this.light_value < this.light_min) {
			String result = "";
			if (this.light_value > this.light_max) {
				result = "Die Beleuchtungszeit ist zu hoch. Bitte überprüf die Lampen, den Helligkeitssensor oder stell das Gerät in einen weniger beleuchteten Platz. ";
			} else {
				result = "Die Beleuchtungszeit ist zu niedrig. Bitte überprüf die Lampen und den Helligkeitssensor. ";
			}
			
			plant_client.publish("warning/light", new MqttMessage("true".getBytes()));
			plant_client.publish("warningtext/light", new MqttMessage(result.getBytes()));
		} else {
			plant_client.publish("warning/light", new MqttMessage("false".getBytes()));
		}
		
		if(this.level_value < this.min_measuring) {
			String result = "Bitte füll " + (this.max_level * 0.9 - this.level_value) + "l in den Tank, um ihn bis zu 90% zu füllen. Dadurch bleibt Spielraum um mögliche benötigte Korrekturen zu ermöglichen.";
			
			plant_client.publish("warning/level", new MqttMessage("true".getBytes()));
			plant_client.publish("warningtext/level", new MqttMessage(result.getBytes()));
		} else {
			plant_client.publish("warning/level", new MqttMessage("false".getBytes()));
		}
	}
}
