/*
 *
 * 		Made with OpenAI's ChatGPT
 *
 */

package handlers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.samuelcampos.usbdrivedetector.USBDeviceDetectorManager;
import net.samuelcampos.usbdrivedetector.USBStorageDevice;
import net.samuelcampos.usbdrivedetector.events.IUSBDriveListener;
import net.samuelcampos.usbdrivedetector.events.USBStorageEvent;
import std.std;

public class UsbHandler {
	private String optionPath;
	private String videoPath;

	final USBDeviceDetectorManager driveDetector = new USBDeviceDetectorManager();

	static MemoryPersistence pers;
	static MqttClient usb_client;
	
	Gson gson;
	
	boolean videos = false;
	boolean options = false;
	
	public UsbHandler() {
		// Set default paths
		optionPath = "/path/to/option";
		videoPath = "/path/to/video";
	}
	
	public void setupMqtt() {
		try {
			pers = new MemoryPersistence();
			MqttConnectOptions mqtt_opt = new MqttConnectOptions();
			mqtt_opt.setMaxInflight(50);
			usb_client = new MqttClient("tcp://localhost:1883", "usb", pers);
			usb_client.connect(mqtt_opt);
			std.INFO(this, "Mqtt-communication established");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void setupGson() {
		gson = new GsonBuilder().setPrettyPrinting().create();
	}

	public void setOptionPath(String optionPath) {
		this.optionPath = optionPath;
	}

	public void setVideoPath(String videoPath) {
		this.videoPath = videoPath;
	}

	public void start() {
		// Register an event listener to be notified when an USB storage device is
		// connected
		driveDetector.addDriveListener(new IUSBDriveListener() {
			@Override
			public void usbDriveEvent(USBStorageEvent event) {
				videos = false;
				options = false;
				
				std.INFO(this, "New USB Device");
				USBStorageDevice device = event.getStorageDevice();
				// This is probably a USB stick
				File root = device.getRootDirectory();

				// Find the newest .save file on the USB stick
				File newestSaveFile = null;
				List<File> saveFiles = findFilesByExtension(root, ".save");
				if (saveFiles.size() > 0) {
					for (File saveFile : saveFiles) {
						if (newestSaveFile == null || saveFile.lastModified() > newestSaveFile.lastModified()) {
							newestSaveFile = saveFile;
						}
					}
				}

				if (newestSaveFile != null) {
					options = true;
					std.INFO(this, "New Options File");

					// Copy the newest .save file to the specified path and rename it to "option"
					try {
						String[] command;

						if (System.getProperty("os.name").startsWith("Windows")) {
						  // On Windows, use the "cmd /c" prefix to run the "copy" command
						  command = new String[] {"cmd", "/c", "copy", newestSaveFile.getAbsolutePath(), optionPath + "/options.save"};
						} else {
						  // On Linux, use the "sudo" command to run the "cp" command
						  command = new String[] {"sudo", "cp", newestSaveFile.getAbsolutePath(), optionPath + "/options.save"};
						}

						ProcessBuilder pb = new ProcessBuilder(command);
						pb.inheritIO().start().waitFor();
						
						usb_client.publish("option/reload", new MqttMessage("OK".getBytes()));

					} catch (Exception e) {
						System.err.println("Error copying file: " + e.getMessage());
					}
					
				}

				// Find all .mp4 files in the specified video path
				List<File> videoFiles = findFilesByExtension(new File(videoPath), ".mp4");

				// Moving all .mp4 files to the USB stick
				for (File videoFile : videoFiles) {
					videos = true;
					std.INFO(this, "Moving video file");
					try {
						String[] command;

						if (System.getProperty("os.name").startsWith("Windows")) {
						  // On Windows, use the "cmd /c" prefix to run the "move" command
						  command = new String[] {"cmd", "/c", "move", videoFile.getAbsolutePath(),
						                         device.getRootDirectory().getAbsolutePath() + "/" + videoFile.getName()};
						} else {
						  // On Linux, use the "sudo" command to run the "mv" command
						  command = new String[] {"sudo", "mv", videoFile.getAbsolutePath(),
						                         device.getRootDirectory().getAbsolutePath() + "/" + videoFile.getName()};
						}

						ProcessBuilder pb = new ProcessBuilder(command);
						pb.inheritIO().start().waitFor();
					} catch (Exception e) {
						System.err.println("Error moving file: " + e.getMessage());
					}
				}

				// Unmount the USB stick
				try {
					std.INFO(this, "Unmounting");
					driveDetector.unmountStorageDevice(device);
				} catch (IOException e) {
					e.printStackTrace();
				}
				String msg = "You can safly remove your storage-device now. ";
				if(videos && options) msg += "The options were updated and the videos were loaded onto your device.";
				else if(videos) msg += "The videos were loaded onto your device.";
				else if(options) msg += "The options were updated.";
				else msg += "Nothing changed.";
				try {
					usb_client.publish("gui/notification", new MqttMessage(gson.toJson(new String[]{"Remove storage-device", msg}).getBytes()));
				} catch (MqttException e) {
					e.printStackTrace();
				}
			}
		});
	}

	private List<File> findFilesByExtension(File root, String extension) {
		List<File> files = new ArrayList<>();
		if (root.isDirectory()) {
			File[] children = root.listFiles();
			if (children != null) {
				for (File child : children) {
					files.addAll(findFilesByExtension(child, extension));
				}
			}
		} else {
			if (root.getName().endsWith(extension)) {
				files.add(root);
			}
		}
		return files;
	}
}