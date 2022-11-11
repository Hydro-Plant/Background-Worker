package handlers;

import java.io.FileWriter;
import java.lang.reflect.Type;
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

import timelapse.TimeLapseData;
import timelapse.TimeLapsePos;

public class SerialHandler {
	SerialPort port;

	static MemoryPersistence pers;
	static MqttClient serial_client;

	static Gson gson;

	public void setupGson() {
		gson = new GsonBuilder().setPrettyPrinting().create();
		System.out.println("Serial-Gson created");
	}

	public void setupMqtt() {
		try {

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
						int len = value.length();
						String msg = "VT" + len + value;
						port.writeBytes(msg.getBytes(), msg.length());
						break;

					case "SERIAL/CAMERA/SET":
						ArrayList<Double> cam_pos = gson.fromJson(new String(message.getPayload()),
								new TypeToken<ArrayList<Double>>() {
								}.getType());
						if (cam_pos.size() == 2) {
							String msg2 = "C" + cam_pos.get(0) + ";" + cam_pos.get(1);
							port.writeBytes(msg2.getBytes(), msg2.length());
						}
						break;

					case "SERIAL/CAMERA/RESET":
						port.writeBytes("CR".getBytes(), 2);
						break;
					}
				}

				@Override
				public void connectionLost(Throwable cause) {
					System.out.println("Connection lost");
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

	public void setupConnection() {
		SerialPort[] ports = SerialPort.getCommPorts();

		port_loop: for (int x = 0; x < ports.length; x++) {
			port = SerialPort.getCommPorts()[x];
			port.setBaudRate(115200);
			if (port.openPort()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println("Testing port " + port.getSystemPortName());
				port.writeBytes("?\n".getBytes(), 2);

				for (int y = 0; y < 5; y++) {
					long millis = System.currentTimeMillis();
					timer_loop: while (System.currentTimeMillis() < millis + 3000) {
						if (port.bytesAvailable() >= 2) {
							byte[] t = new byte[2];
							port.readBytes(t, 2);
							System.out.println("Answere: " + new String(t));
							if (new String(t).equals("OK")) {
								System.out.println("Communication port found");
								break port_loop;
							}
							byte[] mt = new byte[1];
							while (port.bytesAvailable() > 0)
								port.readBytes(mt, 1);
							break timer_loop;
						}
					}
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				System.out.println("Wrong port");
				port.closePort();
			} else {
				System.out.println("Could not open port " + port.getSystemPortName());
			}
		}
	}

	public void handle() {
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

			if (res.length() >= 5 && res.substring(0, 5).equals("VTEMP")) { // Getting values
				try {
					serial_client.publish("value/temperature",
							new MqttMessage(res.substring(5, res.length()).getBytes()));
				} catch (MqttException e) {
					e.printStackTrace();
				}
			} else if (res.length() >= 5 && res.substring(0, 5).equals("VLIGT")) {
				try {
					serial_client.publish("value/light", new MqttMessage(res.substring(5, res.length()).getBytes()));
				} catch (MqttException e) {
					e.printStackTrace();
				}
			} else if (res.length() >= 5 && res.substring(0, 5).equals("VLGST")) {
				try {
					if (res.substring(5, res.length()).equals("1")) {
						serial_client.publish("value/lightStatus", new MqttMessage("True".getBytes()));
					} else {
						serial_client.publish("value/lightStatus", new MqttMessage("False".getBytes()));
					}
				} catch (MqttException e) {
					e.printStackTrace();
				}
			} else if (res.length() >= 3 && res.substring(0, 3).equals("VPH")) {
				int len = Integer.parseInt(res.substring(3, 4));
				try {
					serial_client.publish("value/ph", new MqttMessage(res.substring(3, res.length()).getBytes()));
				} catch (MqttException e) {
					e.printStackTrace();
				}
			} else if (res.length() >= 3 && res.substring(0, 3).equals("VEC")) {
				int len = Integer.parseInt(res.substring(3, 4));
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
					serial_client.publish("value/level", new MqttMessage(res.substring(5, res.length()).getBytes()));
				} catch (MqttException e) {
					e.printStackTrace();
				}
			} else if (res.length() >= 3 && res.substring(0, 3).equals("COK")) {
				try {
					serial_client.publish("serial/camera/reached", new MqttMessage());
				} catch (MqttPersistenceException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (MqttException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (res.length() >= 4 && res.substring(0, 4).equals("DBUG")) {
				System.out.println("Arduino-Debug: " + res.substring(4, res.length()));
			}
		}
	}
}
