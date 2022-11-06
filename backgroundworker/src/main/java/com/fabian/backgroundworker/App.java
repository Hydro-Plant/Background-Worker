package com.fabian.backgroundworker;

import java.io.File;
import handlers.TimelapseHandler;

/**
 * Hello world!
 *
 */
public class App {
	static File save_dir = new File("saves/");
	static TimelapseHandler tlh;
	
	public static void main(String[] args) {
		System.out.println("Backgroundworker active");

		// ----------------------------------- Checking save directory

		if (!save_dir.exists()) {
			save_dir.mkdir();
		}
		
		// ----------------------------------- Adding Timelapse Handler
		
		tlh = new TimelapseHandler();
		tlh.setupMqtt();
		tlh.loadSave();

		while (true) {
			
		}
	}
}
