package handlers;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.github.sarxos.webcam.Webcam;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class CameraHandler {
	static MemoryPersistence pers;
	static MqttClient camera_client;

	Webcam webcam;

	static Gson gson;

	public void setupGson() {
		gson = new GsonBuilder().setPrettyPrinting().create();
		System.out.println("Serial-Gson created");
	}

	public void setupWebcam() {
		webcam = Webcam.getDefault();
		webcam.open();
	}

	public void setupMqtt() {
		try {

			pers = new MemoryPersistence();

			camera_client = new MqttClient("tcp://localhost:1883", "camera", pers);
			camera_client.connect();
			System.out.println("Serial-Client communication established");
			camera_client.subscribe(new String[] { "camera/picture", "camera/video", "camera/delete" });

			System.out.println("Serial-Client subscriptions completed");
			camera_client.setCallback(new MqttCallback() {
				@Override
				public void messageArrived(String topic, MqttMessage message) throws Exception {
					switch (topic.toUpperCase()) {
					case "CAMERA/PICTURE":
						BufferedImage img = webcam.getImage();
						ArrayList<Integer> val = gson.fromJson(new String(message.getPayload()),
								new TypeToken<ArrayList<Integer>>() {
								}.getType());
						ImageIO.write(img, "png", new File(new String("images/img_" + val.get(0) + "_" + val.get(1) + ".png")));
						break;
					case "CAMERA/VIDEO":

						break;
					case "CAMERA/DELETE":

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
}
