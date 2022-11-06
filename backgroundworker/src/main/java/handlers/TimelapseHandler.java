package handlers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import timelapse.TimeLapseData;

public class TimelapseHandler {

	static MemoryPersistence pers;
	static MqttClient backg_client;

	static ArrayList<TimeLapseData> timelapse_data;

	static Gson gson;
	static File tl_save = new File("saves/timelapse.save");

	public void setupMqtt() {
		try {

			pers = new MemoryPersistence();

			backg_client = new MqttClient("tcp://localhost:1883", "backgroundworker", pers);
			backg_client.connect();
			System.out.println("Background-Client communication established");
			backg_client.subscribe(new String[] { "timelapse/add", "timelapse/delete", "timelapse/get" });

			System.out.println("Background-Client subscriptions completed");
			backg_client.setCallback(new MqttCallback() {
				@Override
				public void messageArrived(String topic, MqttMessage message) throws Exception {
					switch (topic.toUpperCase()) {
					case "TIMELAPSE/ADD":
						TimeLapseData tld = gson.fromJson(message.toString(), TimeLapseData.class);

						idloop: for (int x = 0; true; x++) {
							for (int y = 0; y < timelapse_data.size(); y++) {
								if (x == timelapse_data.get(y).id)
									continue idloop;
							}
							tld.id = x;
							break;
						}

						System.out.println("New: " + tld.id);

						timelapse_data.add(tld);
						FileWriter fw = new FileWriter(tl_save.getAbsolutePath());
						fw.write(gson.toJson(timelapse_data));
						fw.close();
						backg_client.publish("timelapse/data", new MqttMessage(gson.toJson(timelapse_data).getBytes()));
						break;

					case "TIMELAPSE/DELETE":
						System.out.println("In");
						for (int x = 0; x < timelapse_data.size(); x++) {
							if (timelapse_data.get(x).id == (int) Integer.parseInt(new String(message.getPayload()))) {
								System.out.println("Delete: " + message.getPayload().toString());
								timelapse_data.remove(x);
								break;
							}
						}
						System.out.println("Out");

						FileWriter fw2 = new FileWriter(tl_save.getAbsolutePath());
						fw2.write(gson.toJson(timelapse_data));
						fw2.close();
						backg_client.publish("timelapse/data", new MqttMessage(gson.toJson(timelapse_data).getBytes()));
						break;

					case "TIMELAPSE/GET":
						backg_client.publish("timelapse/data", new MqttMessage(gson.toJson(timelapse_data).getBytes()));
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

	public void loadSave() {
		// ----------------------------------- Checking save file

		if (!tl_save.exists()) {
			try {
				tl_save.createNewFile();
				FileWriter fw = new FileWriter(tl_save.getAbsolutePath());
				fw.write("[\n]");
				fw.close();
			} catch (IOException e) {
				System.out.println("TimeLapse Save-File couldn't be built");
				e.printStackTrace();
			}
		}

		// ----------------------------------- Creating Gson

		gson = new GsonBuilder().setPrettyPrinting().create();
		System.out.println("TimeLapse-Gson created");

		// ----------------------------------- Importing TimeLapses

		String timeLapses = "";
		try {
			FileReader fr = new FileReader(tl_save.getAbsolutePath());
			boolean end_reached = false;
			while (!end_reached) {
				char x = (char) fr.read();
				if (x == (char) -1)
					end_reached = true;
				else
					timeLapses += x;
			}
			fr.close();
		} catch (FileNotFoundException e) {
			System.out.println("TimeLapse File not found");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("Reading TimeLapse File failed");
			e.printStackTrace();
		}
		System.out.println("TimeLapseFile read");

		timelapse_data = gson.fromJson(timeLapses.toString(), new TypeToken<ArrayList<TimeLapseData>>() {
		}.getType());

		System.out.println("Save file data extracted");
	}
}
