package handlers;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.fazecast.jSerialComm.SerialPort;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import std.std;

public class SerialHandler {
	SerialPort port = null;

	static MemoryPersistence pers;
	static MqttClient serial_client;

	static Gson gson;
	static ArrayList<String> port_list;

	HashMap<String, String> value_map = new HashMap<String, String>() {
		{
			put("TEMP", "temperature");
			put("LIGT", "light");
			put("PH", "ph");
			put("EC", "ec");
			put("TDS", "tds");
			put("FLOW", "flow");
			put("LEVL", "level");
			put("PUMP", "pump");
			put("BAT", "bat");
			put("LED", "led");
		}
	};
	ArrayList<String> serial_blacklist = new ArrayList<String>() {
		{
			add("COM6");
		}
	};

	public void setupGson() {
		gson = new GsonBuilder().setPrettyPrinting().create();
		std.INFO(this, "Gson created");
	}

	public void setupMqtt() {
		try {
			port_list = new ArrayList<>();

			pers = new MemoryPersistence();
			MqttConnectOptions mqtt_opt = new MqttConnectOptions();
			mqtt_opt.setMaxInflight(50);
			serial_client = new MqttClient("tcp://localhost:1883", "serial", pers);
			serial_client.connect(mqtt_opt);
			std.INFO(this, "Mqtt-communication established");
			serial_client.subscribe(new String[] { "option/interval", "option/light", "option/flow",
					"serial/camera/set", "serial/camera/reset" }, new int[] {2, 2, 2, 2, 2});

			std.INFO(this, "Subscriptions added");
			serial_client.setCallback(new MqttCallback() {
				@Override
				public void messageArrived(String topic, MqttMessage message) throws Exception {
					switch (topic.toUpperCase()) {
					case "OPTION/INTERVAL":
						String value = new String(message.getPayload());
						String msg = "!OINTV:" + value + "\n";
						port_list.add(msg);
						break;
					case "OPTION/LIGHT":
						ArrayList<Double> light_options = gson.fromJson(new String(message.getPayload()),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						String msg3 = "!OLIGT:" + ((light_options.get(0) + light_options.get(1)) / 2) + "\n";
						port_list.add(msg3);
						break;
					case "OPTION/FLOW":
						ArrayList<Double> flow_options = gson.fromJson(new String(message.getPayload()),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						String msg4 = "!OPUMP:" + flow_options.get(0) + "\n";
						port_list.add(msg4);
						break;
					case "SERIAL/CAMERA/SET":
						ArrayList<Double> cam_pos = gson.fromJson(new String(message.getPayload()),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						if (cam_pos.size() == 2) {
							String msg2 = "!CGO:" + cam_pos.get(0) + ";" + cam_pos.get(1) + "\n";
							port_list.add(msg2);
						}
						break;

					case "SERIAL/CAMERA/RESET":
						port_list.add("!CR\n");
						break;
					}
				}

				@Override
				public void connectionLost(Throwable cause) {
					std.INFO("SerialHandler", "Mqtt-connection lost");
					std.INFO("SerialHandler", cause.toString());
				}

				@Override
				public void deliveryComplete(IMqttDeliveryToken token) {

				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setupConnection() {
		SerialPort[] ports = SerialPort.getCommPorts();
		SerialPort short_port;

		port_loop: for (int x = 0; x < ports.length; x++) {
			short_port = SerialPort.getCommPorts()[x];
			short_port.setBaudRate(19200);
			if (short_port.openPort()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				std.INFO(this, "Testing port " + short_port.getSystemPortName());

				boolean connector_ok = true;
				for (int y = 0; y < this.serial_blacklist.size(); y++) {
					if (short_port.getSystemPortName().equals(this.serial_blacklist.get(y))) {
						connector_ok = false;
					}
				}
				if (connector_ok) {
					for (int y = 0; y < 5; y++) {
						short_port.writeBytes("?".getBytes(), 2);
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

						long millis = System.currentTimeMillis();
						boolean any_answere = false;
						timer_loop: while (System.currentTimeMillis() < millis + 3000) {
							if (short_port.bytesAvailable() >= 2) {
								any_answere = true;
								byte[] t = new byte[2];
								short_port.readBytes(t, 2);
								std.INFO(this, "Answere: " + new String(t));
								if (new String(t).equals("OK")) {
									std.INFO(this, "Communication port found");
									port = short_port;
									break port_loop;
								}
								// try {
								// Thread.sleep(100);
								// } catch (InterruptedException e) {
								// e.printStackTrace();
								// }
								byte[] mt = new byte[1];
								while (short_port.bytesAvailable() > 0)
									short_port.readBytes(mt, 1);
								break timer_loop;
							}
						}
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						if (!any_answere)
							break;
					}
				} else {
					std.INFO(this, "Port in blacklist");
				}
				std.INFO(this, "Wrong port");
				short_port.closePort();
			} else {
				std.INFO(this, "Could not open port " + short_port.getSystemPortName());
			}
		}
	}

	public void handle() throws MqttPersistenceException, MqttException {
		if (port != null) {
			if (port_list.size() > 0 && port.bytesAwaitingWrite() <= 0) {
				port.writeBytes(port_list.get(0).getBytes(), port_list.get(0).length());
				serial_client.publish("serial/monitorOut", new MqttMessage(port_list.get(0).getBytes()));
				port_list.remove(0);
			}

			BYTE_AVAILABLE: if (port.bytesAvailable() > 0) {
				boolean reading = true;
				boolean first = true;
				String res = "";
				while (reading) {
					if (port.bytesAvailable() > 0) {
						byte[] inp = new byte[1];
						port.readBytes(inp, 1);
						if (first && inp[0] != (byte) '!') {
							break BYTE_AVAILABLE;
						} else {
							first = false;
						}
						if (inp[0] == (byte) '\n')
							reading = false;
						else
							res += new String(inp);
					}
				}

				serial_client.publish("serial/monitor", new MqttMessage(res.getBytes()));

				String com = "";
				String sub_com = "";
				String data = "";
				if (res.length() >= 2) {
					com = res.substring(1, 2);
				} else {
					break BYTE_AVAILABLE;
				}
				int pos = res.indexOf(':');
				if (pos != -1) {
					sub_com = res.substring(2, pos);
					data = res.substring(pos + 1, res.length());
				} else {
					sub_com = res.substring(2, res.length());
				}

				switch (com) {
				case "V":
					MqttMessage msg = new MqttMessage(data.getBytes());
					msg.setRetained(true);
					serial_client.publish("value/" + value_map.get(sub_com), msg);
					break;
				case "S":
					MqttMessage msg2 = null;
					if (data.trim().strip().equals("1")) {
						msg2 = new MqttMessage("True".getBytes());
					} else {
						msg2 = new MqttMessage("False".getBytes());
					}
					msg2.setRetained(true);
					serial_client.publish("value/" + value_map.get(sub_com), msg2);
					break;
				case "C":
					switch (sub_com) {
					case "OK":
						std.INFO(this, "COK Input");
						serial_client.publish("serial/camera/reached", new MqttMessage("OK".getBytes()));
						break;
					}
					break;
				case "D":
					switch (sub_com) {
					case "BUG":
						std.INFO(this, "Arduino-Debug: " + data);
						break;
					}
					break;
				}
			}
		}
	}
}
