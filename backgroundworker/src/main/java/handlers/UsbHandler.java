/*
 * 
 * 		Made by OpenAI's ChatGPT
 * 
 */

package handlers;

import std.std;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.samuelcampos.usbdrivedetector.USBDeviceDetectorManager;
import net.samuelcampos.usbdrivedetector.USBStorageDevice;
import net.samuelcampos.usbdrivedetector.events.IUSBDriveListener;
import net.samuelcampos.usbdrivedetector.events.USBStorageEvent;

public class UsbHandler {
	private String optionPath;
	private String videoPath;

	final USBDeviceDetectorManager driveDetector = new USBDeviceDetectorManager();

	public UsbHandler() {
		// Set default paths
		optionPath = "/path/to/option";
		videoPath = "/path/to/video";
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
					std.INFO(this, "New Options File");
					
					// Copy the newest .save file to the specified path and rename it to "option"
					try {
						ProcessBuilder pb = new ProcessBuilder("sudo", "cp", newestSaveFile.getAbsolutePath(),
								optionPath + "/options.save");
						pb.inheritIO().start().waitFor();
					} catch (Exception e) {
						System.err.println("Error copying file: " + e.getMessage());
					}
				}

				// Find all .mp4 files in the specified video path
				List<File> videoFiles = findFilesByExtension(new File(videoPath), ".mp4");

				// Moving all .mp4 files to the USB stick
				for (File videoFile : videoFiles) {
					std.INFO(this, "Moving video file");
					try {
						ProcessBuilder pb = new ProcessBuilder("sudo", "mv", videoFile.getAbsolutePath(),
								device.getRootDirectory().getAbsolutePath() + "/" + videoFile.getName());
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
					// TODO Auto-generated catch block
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