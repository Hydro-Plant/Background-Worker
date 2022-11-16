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
	static File vid_dir = new File("videos/");
	static TimelapseHandler tlh;
	static SerialHandler sh;
	static CameraHandler ch;
	
	static Thread camera_thread;
	static Thread serial_thread;
	static Thread timelapse_thread;
	static Thread plant_thread;
	
	public static void main(String[] args) {
		System.out.println("Backgroundworker active");
		
		Runtime.getRuntime().addShutdownHook(new Thread() {

		    @Override
		    public void run() {
		        camera_thread.stop();
		    }

		});

		// ----------------------------------- Checking save directory

		if (!save_dir.exists()) {
			save_dir.mkdir();
		}
		if(!img_dir.exists()) {
			img_dir.mkdir();
		}
		if(!vid_dir.exists()) {
			vid_dir.mkdir();
		}
		
		// ----------------------------------- Adding Handlers
		
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
		
		// ----------------------------------- Making threads
		
		camera_thread = new Thread(new Runnable() {
		    @Override
		    public void run() {
		    	while(true) ch.handle();
		    }
		});
		camera_thread.start();
		
		serial_thread = new Thread(new Runnable() {
		    @Override
		    public void run() {
		    	while(true) sh.handle();
		    }
		});
		serial_thread.start();
		
		timelapse_thread = new Thread(new Runnable() {
		    @Override
		    public void run() {
		    	while(true) tlh.handle();
		    }
		});
		timelapse_thread.start();
		
		plant_thread = new Thread(new Runnable() {
		    @Override
		    public void run() {
		    	
		    }
		});
		plant_thread.start();

		while (true) {
			
		}
	}
}
