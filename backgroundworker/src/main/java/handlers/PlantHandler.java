package handlers;

import java.util.ArrayList;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class PlantHandler {
	static MemoryPersistence pers;
	static MqttClient plant_client;
	
	double temp_value, light_value, ph_value, ec_value, flow_value, level_value;
	
	double temp_min, temp_opt, temp_tol, temp_max;
	
	public void setupMqtt() {
		try {
			pers = new MemoryPersistence();

			plant_client = new MqttClient("tcp://localhost:1883", "plant", pers);
			plant_client.connect();
			System.out.println("Plant-Client communication established");
			plant_client.subscribe(new String[] { "value/temperature", "value/light", "value/ph", "value/ec", "value/flow", "value/level" });

			System.out.println("Plant-Client subscriptions completed");
			plant_client.setCallback(new MqttCallback() {
				@Override
				public void messageArrived(String topic, MqttMessage message) throws Exception {
					switch(topic.toUpperCase()) {
					case "VALUE/TEMPERATURE":
							temp_value = Double.parseDouble(new String(message.getPayload()));
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
	
	private void update() {
		
	}
}
