package com.fabian.backgroundworker;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.MqttException;

import handlers.CameraHandler;
import handlers.OptionHandler;
import handlers.PlantHandler;
import handlers.SerialHandler;
import handlers.TimelapseHandler;
import handlers.UsbHandler;

public class App {
	static int handle_interval = 10;

	static File save_dir = new File("saves/");
	static File img_dir = new File("images/");
	static File vid_dir = new File("videos/");
	static TimelapseHandler tlh;
	static SerialHandler sh;
	static CameraHandler ch;
	static PlantHandler ph;
	static OptionHandler oh;
	static UsbHandler uh;

	static ScheduledExecutorService camera_thread;
	static ScheduledExecutorService serial_thread;
	static ScheduledExecutorService timelapse_thread;
	static ScheduledExecutorService option_thread;
	static ScheduledExecutorService usb_thread;

	public static void main(String[] args) {
		System.out.println("Backgroundworker active");

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				camera_thread.shutdown();
				serial_thread.shutdown();
				timelapse_thread.shutdown();
				option_thread.shutdown();
				usb_thread.shutdown();
			}
		});
		
		// ----------------------------------- Checking save directory

		if (!save_dir.exists()) {
			save_dir.mkdir();
		}
		if (!img_dir.exists()) {
			img_dir.mkdir();
		}
		if (!vid_dir.exists()) {
			vid_dir.mkdir();
		}

		// ----------------------------------- Adding Handlers

		sh = new SerialHandler();
		sh.setupConnection();
		sh.setupGson();
		sh.setupMqtt();

		tlh = new TimelapseHandler();
		tlh.setupMqtt();
		tlh.loadSave();

		ch = new CameraHandler();
		ch.setupGson();
		ch.setupMqtt();
		ch.setupWebcam();

		ph = new PlantHandler();
		ph.setupGson();
		ph.setupMqtt();

		uh = new UsbHandler();
		uh.setupGson();
		uh.setupMqtt();
		uh.setOptionPath(save_dir.getAbsolutePath());
		uh.setVideoPath(vid_dir.getAbsolutePath());
		uh.start();
		
		oh = new OptionHandler();
		oh.setupGson();
		try {
			oh.setupSave();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		oh.setupMqtt();

		// ----------------------------------- Making threads

		option_thread = Executors.newScheduledThreadPool(1);
		option_thread.scheduleWithFixedDelay(new Runnable() {
			  @Override
			public void run() {
			    oh.handle();
			  }
			}, 0, handle_interval, TimeUnit.MILLISECONDS);

		camera_thread = Executors.newScheduledThreadPool(1);
		camera_thread.scheduleWithFixedDelay(new Runnable() {
			  @Override
			public void run() {
			    try {
					ch.handle();
				} catch (MqttException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			  }
			}, 0, handle_interval, TimeUnit.MILLISECONDS);

		serial_thread = Executors.newScheduledThreadPool(1);
		serial_thread.scheduleWithFixedDelay(new Runnable() {
			  @Override
			public void run() {
			    try {
					sh.handle();
				} catch (MqttException e) {
					e.printStackTrace();
				}
			  }
			}, 0, handle_interval, TimeUnit.MILLISECONDS);

		timelapse_thread = Executors.newScheduledThreadPool(1);
		timelapse_thread.scheduleWithFixedDelay(new Runnable() {
			  @Override
			public void run() {
			    tlh.handle();
			  }
			}, 0, handle_interval, TimeUnit.MILLISECONDS);

		while (true) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}
	}
}
