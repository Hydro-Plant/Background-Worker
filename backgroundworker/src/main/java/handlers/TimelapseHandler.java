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
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import timelapse.TimeLapseData;
import timelapse.TimeLapsePos;

public class TimelapseHandler {
	private final double plant_distance = 1; // in m
	private final double OtoZ = 1; // in m

	static MemoryPersistence pers;
	static MqttClient backg_client;

	static ArrayList<TimeLapseData> timelapse_data;
	static ArrayList<TimeLapsePos> timelapse_pos;

	static Gson gson;
	static File tl_save = new File("saves/timelapse.save");
	static File tlp_save = new File("saves/timelapse_pos.save");

	static boolean free = true;

	static ArrayList<Integer> info;
	static ArrayList<Double> video_info = null;

	public void setupMqtt() {
		try {

			pers = new MemoryPersistence();

			backg_client = new MqttClient("tcp://localhost:1883", "backgroundworker", pers);
			backg_client.connect();
			System.out.println("Background-Client communication established");
			backg_client.subscribe(new String[] { "timelapse/add", "timelapse/delete", "timelapse/get",
					"serial/camera/reached", "camera/taken" });

			System.out.println("Background-Client subscriptions completed");
			backg_client.setCallback(new MqttCallback() {
				@Override
				public void messageArrived(String topic, MqttMessage message) throws Exception {
					switch (topic.toUpperCase()) {
					case "SERIAL/CAMERA/REACHED":
						System.out.println("Camera reached pos");
						try {
							backg_client.publish("camera/picture",
									new MqttMessage(gson.toJson(info).toString().getBytes()));
						} catch (MqttException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
						break;
					case "CAMERA/TAKEN":
						System.out.println("Picture taken");
						if(video_info != null) {
							try {
								backg_client.publish("camera/video", new MqttMessage(gson.toJson(video_info).getBytes()));
							} catch (MqttException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
							video_info = null;
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
								for (int y = 0; y < timelapse_data.size(); y++) {
									if (x == timelapse_data.get(y).id)
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
							fw = new FileWriter(tlp_save.getAbsolutePath());
							fw.write(gson.toJson(timelapse_pos));
							fw.close();
						}
						break;

					case "TIMELAPSE/DELETE":
						ArrayList<Integer> data = new ArrayList<Integer>();
						data.add(Integer.parseInt(new String(message.getPayload())));

						for (int x = 0; x < timelapse_data.size(); x++) {
							if (timelapse_data.get(x).id == (int) Integer.parseInt(new String(message.getPayload()))) {
								System.out.println("Data removed");
								timelapse_data.remove(x);
								break;
							}
						}

						for (int x = 0; x < timelapse_pos.size(); x++) {
							if (timelapse_pos.get(x).id == (int) Integer.parseInt(new String(message.getPayload()))) {
								data.add(timelapse_pos.get(x).dates.size());
								System.out.println("Pos removed");
								timelapse_pos.remove(x);
								break;
							}
						}

						backg_client.publish("camera/delete", new MqttMessage(gson.toJson(data).getBytes()));

						FileWriter fw2 = new FileWriter(tl_save.getAbsolutePath());
						fw2.write(gson.toJson(timelapse_data));
						fw2.close();
						backg_client.publish("timelapse/data", new MqttMessage(gson.toJson(timelapse_data).getBytes()));

						fw2 = new FileWriter(tlp_save.getAbsolutePath());
						fw2.write(gson.toJson(timelapse_pos));
						fw2.close();

						break;

					case "TIMELAPSE/GET":
						backg_client.publish("timelapse/data", new MqttMessage(gson.toJson(timelapse_data).getBytes()));
						break;
					}
				}

				@Override
				public void connectionLost(Throwable cause) {
					System.out.println("Timelapse Mqtt-Connection lost");
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

		if (!tlp_save.exists()) {
			try {
				tlp_save.createNewFile();
				FileWriter fw = new FileWriter(tlp_save.getAbsolutePath());
				fw.write("[\n]");
				fw.close();
			} catch (IOException e) {
				System.out.println("TimeLapsePos Save-File couldn't be built");
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
		timelapse_data = new ArrayList<TimeLapseData>();
		timelapse_data = gson.fromJson(timeLapses.toString(), new TypeToken<ArrayList<TimeLapseData>>() {
		}.getType());

		System.out.println("Save file data extracted");

		String timeLapsePoses = "";
		try {
			FileReader fr = new FileReader(tlp_save.getAbsolutePath());
			boolean end_reached = false;
			while (!end_reached) {
				char x = (char) fr.read();
				if (x == (char) -1)
					end_reached = true;
				else
					timeLapsePoses += x;
			}
			fr.close();
		} catch (FileNotFoundException e) {
			System.out.println("TimeLapsePos File not found");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("Reading TimeLapsePos File failed");
			e.printStackTrace();
		}
		System.out.println("TimeLapsePosFile read");
		timelapse_pos = new ArrayList<TimeLapsePos>();
		timelapse_pos = gson.fromJson(timeLapsePoses.toString(), new TypeToken<ArrayList<TimeLapsePos>>() {
		}.getType());

		System.out.println("Pos Save file data extracted");
	}

	public TimeLapsePos calculateTimeLapseDates(LocalDate from_date, LocalTime from_time, LocalDate to_date,
			LocalTime to_time, long images, int mode) {
		long epoch_from = from_date.toEpochSecond(from_time, ZoneOffset.of("+1"));
		long epoch_to = to_date.toEpochSecond(to_time, ZoneOffset.of("+1"));

		double image_gap = (double) (epoch_to - epoch_from) / (images - 1);
		System.out.println(image_gap);

		ArrayList<LocalDate> dates = new ArrayList<LocalDate>();
		ArrayList<LocalTime> times = new ArrayList<LocalTime>();

		ArrayList<Double> pos = new ArrayList<Double>();
		ArrayList<Double> angle = new ArrayList<Double>();
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
					while (!done) {
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
						System.out.println(sel.at_image);
						free = false;
						info = new ArrayList<Integer>();
						info.add(sel.id);
						info.add(sel.at_image);

						ArrayList<Double> pos = new ArrayList<Double>();
						pos.add(sel.pos.get(sel.at_image));
						pos.add(sel.angle.get(sel.at_image));

						try {
							backg_client.publish("serial/camera/set",
									new MqttMessage(gson.toJson(pos).toString().getBytes()));
						} catch (MqttException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}

						sel.at_image++;
						FileWriter fw;
						try {
							fw = new FileWriter(tlp_save.getAbsolutePath());
							fw.write(gson.toJson(timelapse_pos));
							fw.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
						if (sel.at_image == sel.dates.size() - 1) {
							System.out.println("Found last image");
							
							ArrayList<Integer> data = new ArrayList<Integer>();
							data.add(sel.id);
							data.add(sel.dates.size());
							
							video_info = new ArrayList<Double>();
							video_info.add(info.get(0).doubleValue());
							video_info.add(info.get(1).doubleValue());
							video_info.add(timelapse_data.get(x).frameRate);
							
							timelapse_data.remove(x);
							timelapse_pos.remove(x);
							
							try {
								FileWriter fw2 = new FileWriter(tl_save.getAbsolutePath());
								fw2.write(gson.toJson(timelapse_data));
								fw2.close();
								backg_client.publish("timelapse/data",
										new MqttMessage(gson.toJson(timelapse_data).getBytes()));

								fw2 = new FileWriter(tlp_save.getAbsolutePath());
								fw2.write(gson.toJson(timelapse_pos));
								fw2.close();
							} catch (MqttException | IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							
						}
						
						break;
					}
				}
			}
		}
	}
}
