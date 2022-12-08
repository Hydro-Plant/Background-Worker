package handlers;

import java.util.ArrayList;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.fazecast.jSerialComm.SerialPort;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class SerialHandler {
	SerialPort port = null;

	static MemoryPersistence pers;
	static MqttClient serial_client;

	static Gson gson;

	static ArrayList<String> port_list;

	public void setupGson() {
		gson = new GsonBuilder().setPrettyPrinting().create();
		System.out.println("Serial-Gson created");
	}

	public void setupMqtt() {
		try {
			port_list = new ArrayList<String>();

			pers = new MemoryPersistence();

			serial_client = new MqttClient("tcp://localhost:1883", "serial", pers);
			serial_client.connect();
			System.out.println("Serial-Client communication established");
			serial_client.subscribe(new String[] { "option/interval", "serial/camera/set", "serial/camera/reset" });
			System.out.println("Serial-Client subscriptions completed");
			serial_client.setCallback(new MqttCallback() {
				@Override
				public void messageArrived(String topic, MqttMessage message) throws Exception {
					switch (topic.toUpperCase()) {
					case "OPTION/INTERVAL":
						String value = new String(message.getPayload());
						String msg = "OINTV" + value + "\n";
						port_list.add(msg);
						break;
					case "SERIAL/CAMERA/SET":
						ArrayList<Double> cam_pos = gson.fromJson(new String(message.getPayload()),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						if (cam_pos.size() == 2) {
							String msg2 = "C" + cam_pos.get(0) + ";" + cam_pos.get(1) + "\n";
							System.out.println("Port Size: " + port_list.size());
							port_list.add(msg2);
							System.out.println("I'm sending the camera somewhere: "+ msg2);
							System.out.println("Port Size: " + port_list.size());
						}
						break;

					case "SERIAL/CAMERA/RESET":
						port_list.add("CR\n");
						break;
					}
				}

				@Override
				public void connectionLost(Throwable cause) {
					System.out.println("Serial Mqtt-Connection lost");
					System.out.println(cause.toString());
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
			short_port.setBaudRate(115200);
			if (short_port.openPort()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println("Testing port " + short_port.getSystemPortName());
				short_port.writeBytes("?\n".getBytes(), 2);

				for (int y = 0; y < 5; y++) {
					long millis = System.currentTimeMillis();
					boolean any_answere = false;
					timer_loop: while (System.currentTimeMillis() < millis + 3000) {
						if (short_port.bytesAvailable() >= 2) {
							any_answere = true;
							byte[] t = new byte[2];
							short_port.readBytes(t, 2);
							System.out.println("Answere: " + new String(t));
							if (new String(t).equals("OK")) {
								System.out.println("Communication port found");
								port = short_port;
								break port_loop;
							}
							//try {
								//Thread.sleep(100);
							//} catch (InterruptedException e) {
							//	e.printStackTrace();
							//}
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
				System.out.println("Wrong port");
				short_port.closePort();
			} else {
				System.out.println("Could not open port " + short_port.getSystemPortName());
			}
		}
	}
	
	public void requestOptions() {
		try {
			serial_client.publish("option/get", new MqttMessage("interval".getBytes()));
		} catch (MqttException e) {
			e.printStackTrace();
		}
	}

	public void handle() {
		if (port != null) {
			//System.out.println(port.bytesAwaitingWrite() + " | " + port_list.size());
			if (port_list.size() > 0 && port.bytesAwaitingWrite() <= 0) {
				System.out.println("Writing: " + port_list.get(0) + " | " + port_list.get(0).length());
				port.writeBytes(port_list.get(0).getBytes(), port_list.get(0).length());
				port_list.remove(0);
			}

			if (port.bytesAvailable() > 0) {
				boolean reading = true;
				String res = "";
				while (reading) {
					if (port.bytesAvailable() > 0) {
						byte[] inp = new byte[1];
						port.readBytes(inp, 1);
						if (inp[0] == (byte) '\n')
							reading = false;
						else
							res += new String(inp);
					}
				}

				try {
					serial_client.publish("serial/monitor", new MqttMessage(res.getBytes()));
				} catch (MqttPersistenceException e1) {
					e1.printStackTrace();
				} catch (MqttException e1) {
					e1.printStackTrace();
				}

				if (res.length() >= 5 && res.substring(0, 5).equals("VTEMP")) { // Getting values
					try {
						serial_client.publish("value/temperature",
								new MqttMessage(res.substring(5, res.length()).getBytes()));
					} catch (MqttException e) {
						e.printStackTrace();
					}
				} else if (res.length() >= 5 && res.substring(0, 5).equals("VLIGT")) {
					try {
						serial_client.publish("value/light",
								new MqttMessage(res.substring(5, res.length()).getBytes()));
					} catch (MqttException e) {
						e.printStackTrace();
					}
				} else if (res.length() >= 5 && res.substring(0, 5).equals("SLIGT")) {
					try {
						if (res.substring(5, res.length()).equals("1")) {
							serial_client.publish("status/set", new MqttMessage("{\"name\":\"light\",\"value\":\"True\"}".getBytes()));
						} else {
							serial_client.publish("status/set", new MqttMessage("{\"name\":\"light\",\"value\":\"False\"}".getBytes()));
						}
					} catch (MqttException e) {
						e.printStackTrace();
					}
				} else if (res.length() >= 3 && res.substring(0, 3).equals("VPH")) {
					try {
						serial_client.publish("value/ph", new MqttMessage(res.substring(3, res.length()).getBytes()));
					} catch (MqttException e) {
						e.printStackTrace();
					}
				} else if (res.length() >= 3 && res.substring(0, 3).equals("VEC")) {
					try {
						serial_client.publish("value/ec", new MqttMessage(res.substring(3, res.length()).getBytes()));
					} catch (MqttException e) {
						e.printStackTrace();
					}
				} else if (res.length() >= 5 && res.substring(0, 5).equals("VFLOW")) {
					try {
						serial_client.publish("value/flow", new MqttMessage(res.substring(5, res.length()).getBytes()));
					} catch (MqttException e) {
						e.printStackTrace();
					}
				} else if (res.length() >= 5 && res.substring(0, 5).equals("VLEVL")) {
					try {
						serial_client.publish("value/level",
								new MqttMessage(res.substring(5, res.length()).getBytes()));
					} catch (MqttException e) {
						e.printStackTrace();
					}
				} else if (res.length() >= 3 && res.substring(0, 3).equals("COK")) {
					try {
						serial_client.publish("serial/camera/reached", new MqttMessage());
					} catch (MqttPersistenceException e) {
						e.printStackTrace();
					} catch (MqttException e) {
						e.printStackTrace();
					}
				} else if (res.length() >= 4 && res.substring(0, 4).equals("DBUG")) {
					System.out.println("Arduino-Debug: " + res.substring(4, res.length()));
				}
			}
		}
	}
}
