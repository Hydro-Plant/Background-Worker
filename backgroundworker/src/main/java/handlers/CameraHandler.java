package handlers;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import com.github.sarxos.webcam.ds.fswebcam.FsWebcamDriver;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import std.std;
import videocreator.VideoCreator;

public class CameraHandler {
	static MemoryPersistence pers;
	static MqttClient camera_client;

	Webcam webcam;

	static Gson gson;

	ArrayList<MqttMessage> com_order;
	ArrayList<String> topic_order;

	
	

	public void setupGson() {
		gson = new GsonBuilder().setPrettyPrinting().create();
		std.INFO(this, "Gson created");
	}

	public void setupWebcam() {
		try {
			String os = System.getProperty("os.name").toLowerCase();
			if (os.contains("linux")) {
				Webcam.setDriver(new FsWebcamDriver());
			}
			webcam = Webcam.getDefault();
			webcam.setCustomViewSizes(new Dimension[] { WebcamResolution.FHD.getSize() }); // register custom size
			webcam.setViewSize(WebcamResolution.FHD.getSize()); // set custom size
			webcam.open();
			std.INFO(this, "Webcam opened");
		} catch (Exception e) {
			std.INFO(this, "No webcam available");
			e.printStackTrace();
		}
	}

	public void setupMqtt() {
		com_order = new ArrayList<>();
		topic_order = new ArrayList<>();

		try {

			pers = new MemoryPersistence();
			MqttConnectOptions mqtt_opt = new MqttConnectOptions();
			mqtt_opt.setMaxInflight(100);
			camera_client = new MqttClient("tcp://localhost:1883", "camera", pers);
			camera_client.connect(mqtt_opt);
			std.INFO(this, "Mqtt-communication established");
			try {
				camera_client.subscribe(new String[] { "camera/picture", "camera/video", "camera/delete" });
			} catch (MqttException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			std.INFO(this, "Subscriptions added");
			camera_client.setCallback(new MqttCallback() {
				@Override
				public void messageArrived(String topic, MqttMessage message) throws Exception {
					com_order.add(message);
					topic_order.add(topic);
				}

				@Override
				public void connectionLost(Throwable cause) {
					std.INFO("CameraHandler", "Mqtt-connection lost");
					std.INFO("CameraHandler", cause.toString());
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

	public void stop() {
		webcam.close();
	}

	public void handle() {
		if (com_order.size() > 0 && topic_order.size() > 0) {
			switch (topic_order.get(0).toUpperCase()) {
			case "CAMERA/PICTURE":
				ArrayList<Integer> val = gson.fromJson(new String(com_order.get(0).getPayload()),
						new TypeToken<ArrayList<Integer>>() {
						}.getType());
				BufferedImage img = webcam.getImage();
				try {
					camera_client.publish("camera/taken", new MqttMessage());
				} catch (MqttException e3) {
					// TODO Auto-generated catch block
					e3.printStackTrace();
				}
				std.INFO(this, "Picture " + val.get(0) + " " + val.get(1) + " made");

				try {
					ImageIO.write(img, "png",
							new File(new String("images/img_" + val.get(0) + "_" + val.get(1) + ".png")));
					std.INFO(this, "Picture saved");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				break;
			case "CAMERA/VIDEO":
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				ArrayList<Double> val2 = gson.fromJson(new String(com_order.get(0).getPayload()),
						new TypeToken<ArrayList<Double>>() {
						}.getType());
				int id = (int) Math.floor(val2.get(0));
				int len = (int) Math.floor(val2.get(1));
				double frameRate = val2.get(2);

				ArrayList<File> imgs = new ArrayList<>();
				for (int x = 0; x < len; x++) {
					File cur_file = new File(new String("images/img_" + id + "_" + x + ".png"));
					if (cur_file.exists()) {
						imgs.add(cur_file);
					}
				}

				std.INFO(this, "Making new video " + id + " " + len + " " + frameRate);

				if (imgs.size() > 0) {
					int sizex, sizey;
					BufferedImage first = null;
					try {
						first = ImageIO.read(imgs.get(0));
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					sizex = first.getWidth();
					sizey = first.getHeight();

					int num_length = 0;
					while ((imgs.size() - 1) / Math.pow(10, num_length) >= 1)
						num_length++;

					String imgs_string = "images/img_buffer_%0" + num_length + "d.png";
					for(int x = 0; x < imgs.size(); x++) {
						String new_name = String.format(imgs_string, x);
						imgs.get(x).renameTo(new File(new_name));
					}

					String video_name = "";
					for (int x = 0; true; x++) {
						if (!new File(new String("videos/" + x + ".mp4")).exists()) {
							video_name = "videos/" + x + ".mp4";
							break;
						}
					}

					try {
						VideoCreator.createVideo(imgs_string, (int) Math.floor(frameRate), sizey, sizex, video_name);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					std.INFO(this, "Video saved");

					for (int x = 0; x < imgs.size(); x++) {
						File cur_file = new File(String.format(imgs_string, x));
						if (cur_file.exists())
							cur_file.delete();
					}

					std.INFO(this, "Images " + id + " deleted");
				}

				break;
			case "CAMERA/DELETE": // ACHTUNG: DOPPELTES E UM AUSLÖSUNG ZU VERHINDERN
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				ArrayList<Integer> val3 = gson.fromJson(new String(com_order.get(0).getPayload()),
						new TypeToken<ArrayList<Integer>>() {
						}.getType());
				int id2 = val3.get(0);
				int len2 = val3.get(1);

				for (int x = 0; x < len2; x++) {
					File cur_file = new File(new String("images/img_" + id2 + "_" + x + ".png"));
					if (cur_file.exists())
						cur_file.delete();
				}

				break;
			}

			com_order.remove(0);
			topic_order.remove(0);
		}
	}
}


