package com.fabian.backgroundworker;

import java.io.File;

import handlers.CameraHandler;
import handlers.SerialHandler;
import handlers.TimelapseHandler;

/**
 * Hello world!
 *
 */
public class App {
	static File save_dir = new File("saves/");
	static File img_dir = new File("images/");
	static TimelapseHandler tlh;
	static SerialHandler sh;
	static CameraHandler ch;
	
	public static void main(String[] args) {
		System.out.println("Backgroundworker active");

		// ----------------------------------- Checking save directory

		if (!save_dir.exists()) {
			save_dir.mkdir();
		}
		if(!img_dir.exists()) {
			img_dir.mkdir();
		}
		
		// ----------------------------------- Adding Timelapse Handler
		
		tlh = new TimelapseHandler();
		tlh.setupMqtt();
		tlh.loadSave();
		
		sh = new SerialHandler();
		sh.setupConnection();
		sh.setupMqtt();
		sh.setupGson();
		
		ch = new CameraHandler();
		ch.setupGson();
		ch.setupMqtt();
		ch.setupWebcam();

		while (true) {
			tlh.handle();
			sh.handle();
		}
	}
}
