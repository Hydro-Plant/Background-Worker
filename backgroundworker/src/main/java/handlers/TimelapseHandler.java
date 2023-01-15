package handlers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import std.std;
import timelapse.TimeLapseData;
import timelapse.TimeLapsePos;

public class TimelapseHandler {
	private final double plant_distance = 0.07; // in m
	private final double OtoZ = 0.045; // in m

	static MemoryPersistence pers;
	static MqttClient backg_client;

	static ArrayList<TimeLapseData> timelapse_data;
	static ArrayList<TimeLapsePos> timelapse_pos;

	static Gson gson;
	static File tl_save = new File("saves/timelapse.save");

	static boolean free = true;

	static ArrayList<Integer> info;
	static ArrayList<Double> video_info = null;

	public void setupMqtt() {
		try {

			pers = new MemoryPersistence();
			MqttConnectOptions mqtt_opt = new MqttConnectOptions();
			mqtt_opt.setMaxInflight(100);
			backg_client = new MqttClient("tcp://localhost:1883", "backgroundworker", pers);
			backg_client.connect(mqtt_opt);
			std.INFO(this, "Mqtt-communication established");
			try {
				backg_client.subscribe(new String[] { "timelapse/add", "timelapse/delete", "timelapse/get",
						"serial/camera/reached", "camera/taken" }, new int[]{ 2, 2, 2, 2, 2 });
			} catch (MqttException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			std.INFO(this, "Subscriptions added");
			backg_client.setCallback(new MqttCallback() {
				@Override
				public void messageArrived(String topic, MqttMessage message) throws Exception {
					switch (topic.toUpperCase()) {
					case "SERIAL/CAMERA/REACHED":
						boolean still_existing = false;
						for (TimeLapseData element : timelapse_data) {
							if (info.get(0) == element.id) {
								still_existing = true;
								break;
							}
						}
						if (still_existing) {
							try {
								backg_client.publish("camera/picture",
										new MqttMessage(gson.toJson(info).toString().getBytes()));
							} catch (MqttException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
						} else {
							try {
								backg_client.publish("serial/camera/reset", new MqttMessage());
							} catch (MqttException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
							free = true;
						}
						break;
					case "CAMERA/TAKEN":
						std.INFO(this, "Received camera-picture confirmation");
						if (video_info != null) {
							try {
								backg_client.publish("camera/video",
										new MqttMessage(gson.toJson(video_info).getBytes()));
							} catch (MqttException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
							video_info = null;

							for (int x = 0; x < timelapse_data.size(); x++) {
								if (info.get(0) == timelapse_data.get(x).id) {
									timelapse_data.remove(x);
									timelapse_pos.remove(x);

									try {
										FileWriter fw2 = new FileWriter(tl_save.getAbsolutePath());
										fw2.write(gson.toJson(timelapse_data));
										fw2.close();
										backg_client.publish("timelapse/data",
												new MqttMessage(gson.toJson(timelapse_data).getBytes()));
									} catch (MqttException | IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
									break;
								}
							}
						}

						try {
							backg_client.publish("serial/camera/reset", new MqttMessage());
						} catch (MqttException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}

						free = true;
						break;
					case "TIMELAPSE/ADD":
						TimeLapseData tld = gson.fromJson(message.toString(), TimeLapseData.class);
						if (LocalDate.parse(tld.date_from).toEpochSecond(LocalTime.parse(tld.time_from),
								ZoneOffset.of("+1")) != LocalDate.parse(tld.date_to)
										.toEpochSecond(LocalTime.parse(tld.time_to), ZoneOffset.of("+1"))) {

							TimeLapsePos tlp = calculateTimeLapseDates(tld.getDateFrom(), tld.getTimeFrom(),
									tld.getDateTo(), tld.getTimeTo(), tld.pictures, tld.mode);

							idloop: for (int x = 0; true; x++) {
								for (TimeLapseData element : timelapse_data) {
									if (x == element.id)
										continue idloop;
								}
								tld.id = x;
								tlp.id = x;
								break;
							}

							timelapse_data.add(tld);
							FileWriter fw = new FileWriter(tl_save.getAbsolutePath());
							fw.write(gson.toJson(timelapse_data));
							fw.close();
							backg_client.publish("timelapse/data",
									new MqttMessage(gson.toJson(timelapse_data).getBytes()));

							timelapse_pos.add(tlp);
							std.INFO(this, "Added new timelapse");
						}
						break;

					case "TIMELAPSE/DELETE":
						ArrayList<Integer> data = new ArrayList<>();
						data.add(Integer.parseInt(new String(message.getPayload())));

						for (int x = 0; x < timelapse_data.size(); x++) {
							if (timelapse_data.get(x).id == Integer.parseInt(new String(message.getPayload()))) {
								std.INFO(this, "Removed data");
								timelapse_data.remove(x);
								break;
							}
						}

						for (int x = 0; x < timelapse_pos.size(); x++) {
							if (timelapse_pos.get(x).id == Integer.parseInt(new String(message.getPayload()))) {
								data.add(timelapse_pos.get(x).dates.size());
								std.INFO(this, "Removed positions");
								timelapse_pos.remove(x);
								break;
							}
						}

						if (Integer.parseInt(new String(message.getPayload())) == info.get(0)) {
							free = true;
						}

						backg_client.publish("timelapse/data", new MqttMessage(gson.toJson(timelapse_data).getBytes()));
						backg_client.publish("camera/delete", new MqttMessage(gson.toJson(data).getBytes()));
						break;

					case "TIMELAPSE/GET":
						backg_client.publish("timelapse/data", new MqttMessage(gson.toJson(timelapse_data).getBytes()));
						break;
					}
				}

				@Override
				public void connectionLost(Throwable cause) {
					std.INFO("TimelapseHandler", "Mqtt-connection lost");
					std.INFO("TimelapseHandler", cause.toString());
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
				std.INFO(this, "TimeLapse Save-File couldn't be built");
				e.printStackTrace();
			}
		}

		// ----------------------------------- Creating Gson

		gson = new GsonBuilder().setPrettyPrinting().create();
		std.INFO(this, "Gson created");

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
			std.INFO(this, "TimeLapse File not found");
			e.printStackTrace();
		} catch (IOException e) {
			std.INFO(this, "Reading TimeLapse File failed");
			e.printStackTrace();
		}
		std.INFO(this, "Save-file read");
		timelapse_data = new ArrayList<>();
		timelapse_data = gson.fromJson(timeLapses.toString(), new TypeToken<ArrayList<TimeLapseData>>() {
		}.getType());

		timelapse_pos = new ArrayList<>();

		for (int x = 0; x < timelapse_data.size(); x++) {
			timelapse_pos.add(calculateTimeLapseDates(timelapse_data.get(x).getDateFrom(),
					timelapse_data.get(x).getTimeFrom(), timelapse_data.get(x).getDateTo(),
					timelapse_data.get(x).getTimeTo(), timelapse_data.get(x).pictures, timelapse_data.get(x).mode));
			timelapse_pos.get(x).id = timelapse_data.get(x).id;
		}
		std.INFO(this, "Save file data extracted");
	}

	public TimeLapsePos calculateTimeLapseDates(LocalDate from_date, LocalTime from_time, LocalDate to_date,
			LocalTime to_time, long images, int mode) {
		long epoch_from = from_date.toEpochSecond(from_time, ZoneOffset.of("+1"));
		long epoch_to = to_date.toEpochSecond(to_time, ZoneOffset.of("+1"));

		double image_gap = (double) (epoch_to - epoch_from) / (images - 1);

		ArrayList<LocalDate> dates = new ArrayList<>();
		ArrayList<LocalTime> times = new ArrayList<>();

		ArrayList<Double> pos = new ArrayList<>();
		ArrayList<Double> angle = new ArrayList<>();
		for (int x = 0; x < images; x++) {
			LocalDateTime ldt = LocalDateTime.ofEpochSecond((long) (epoch_from + x * image_gap), 0,
					ZoneOffset.of("+1"));
			dates.add(ldt.toLocalDate());
			times.add(ldt.toLocalTime());

			switch (mode) {
			case 0:
				pos.add(0.5);
				angle.add((double) 0);
				break;
			case 1:
				pos.add((image_gap * x) / (epoch_to - epoch_from));
				angle.add((double) 0);
				break;
			case 2:
				pos.add((image_gap * x) / (epoch_to - epoch_from));
				angle.add(Math.atan((0.5 - pos.get(x)) * OtoZ / plant_distance));
				break;
			}
		}

		TimeLapsePos res = new TimeLapsePos(dates, times, pos, angle, -1);
		return res;
	}

	public void handle() {
		if (free) {
			long epoch_now = LocalDate.now().toEpochSecond(LocalTime.now(), ZoneOffset.of("+1"));
			for (int x = 0; x < timelapse_pos.size(); x++) {
				TimeLapsePos sel = timelapse_pos.get(x);

				if (sel.at_image <= sel.dates.size() - 1) {
					boolean done = false;
					long sel_epoch;
					while (!done && sel.at_image != sel.dates.size() - 1) {
						if (sel.at_image + 1 < sel.dates.size()) {
							sel_epoch = LocalDate.parse(sel.dates.get(sel.at_image + 1)).toEpochSecond(
									LocalTime.parse(sel.times.get(sel.at_image + 1)), ZoneOffset.of("+1"));
							if (sel_epoch <= epoch_now) {
								sel.at_image++;
							} else {
								done = true;
							}
						} else {
							done = true;
						}
					}
					sel_epoch = LocalDate.parse(sel.dates.get(sel.at_image))
							.toEpochSecond(LocalTime.parse(sel.times.get(sel.at_image)), ZoneOffset.of("+1"));
					if (sel_epoch <= epoch_now) {
						std.INFO(this, "Making image " + sel.at_image);
						free = false;
						info = new ArrayList<>();
						info.add(sel.id);
						info.add(sel.at_image);

						ArrayList<Double> pos = new ArrayList<>();
						pos.add(sel.pos.get(sel.at_image));
						pos.add(sel.angle.get(sel.at_image) * 180 / Math.PI);

						try {
							backg_client.publish("serial/camera/set",
									new MqttMessage(gson.toJson(pos).toString().getBytes()));
						} catch (MqttException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}

						if (sel.at_image == sel.dates.size() - 1) {
							std.INFO(this, "Last image of video");

							ArrayList<Integer> data = new ArrayList<>();
							data.add(sel.id);
							data.add(sel.dates.size());

							video_info = new ArrayList<>();
							video_info.add(info.get(0).doubleValue());
							video_info.add((double) sel.dates.size());
							video_info.add(timelapse_data.get(x).frameRate);
						}

						sel.at_image++;

						break;
					}
				}
			}
		}
	}
}
