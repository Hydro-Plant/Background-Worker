package handlers;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.IplImage;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.ds.fswebcam.FsWebcamDriver;
import com.github.sarxos.webcam.ds.mjpeg.MjpegCaptureDriver;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import io.humble.video.Codec;
import io.humble.video.Encoder;
import io.humble.video.MediaPacket;
import io.humble.video.MediaPicture;
import io.humble.video.Muxer;
import io.humble.video.MuxerFormat;
import io.humble.video.PixelFormat;
import io.humble.video.Rational;
import io.humble.video.awt.MediaPictureConverter;
import io.humble.video.awt.MediaPictureConverterFactory;

public class CameraHandler {
	static MemoryPersistence pers;
	static MqttClient camera_client;

	Webcam webcam;
	FrameGrabber grabber;
	VideoCapture camera;

	static Gson gson;

	ArrayList<MqttMessage> com_order;
	ArrayList<String> topic_order;

	public void setupGson() {
		gson = new GsonBuilder().setPrettyPrinting().create();
		System.out.println("Serial-Gson created");
	}

	public void setupWebcam() throws org.bytedeco.javacv.FrameGrabber.Exception {
		startCamera();
		
		/**
		grabber = new OpenCVFrameGrabber(0);
		grabber.start();
		**/
		
		/**
		try {
			//Webcam.setDriver(new FsWebcamDriver());
			//Webcam.setDriver(new FFmpegCliDriver());
			//Webcam.setDriver(new MjpegCaptureDriver().withUri("tcp://127.0.0.1:5000") // this is your local host
			//		.withUri("tcp://192.168.1.12:5000")); // this is some remote host
			webcam = Webcam.getDefault();
			webcam.open();
		} catch (Exception e) {
			System.out.println("No webcam :(");
			e.printStackTrace();
		}
		**/

	}

	public void setupMqtt() {
		com_order = new ArrayList<MqttMessage>();
		topic_order = new ArrayList<String>();

		try {

			pers = new MemoryPersistence();

			camera_client = new MqttClient("tcp://localhost:1883", "camera", pers);
			camera_client.connect();
			System.out.println("Serial-Client communication established");
			camera_client.subscribe(new String[] { "camera/picture", "camera/video", "camera/delete" });

			System.out.println("Serial-Client subscriptions completed");
			camera_client.setCallback(new MqttCallback() {
				@Override
				public void messageArrived(String topic, MqttMessage message) throws Exception {
					com_order.add(message);
					topic_order.add(topic);
				}

				@Override
				public void connectionLost(Throwable cause) {
					System.out.println("Camera Mqtt-Connection lost");
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

	public void stop() {
		webcam.close();
	}

	public void handle() {
		if (com_order.size() > 0 && topic_order.size() > 0) {
			System.out.println("Camera input");
			switch (topic_order.get(0).toUpperCase()) {
			case "CAMERA/PICTURE":
				ArrayList<Integer> val = gson.fromJson(new String(com_order.get(0).getPayload()),
						new TypeToken<ArrayList<Integer>>() {
						}.getType());
				System.out.println("Webcam making image");
				//BufferedImage img = webcam.getImage();
				
				Mat frame = takePicture();
				
				Imgcodecs.imwrite(("images/img_" + val.get(0) + "_" + val.get(1) + ".png"), frame);
				
				/**
				Frame frame = null;
				try {
					frame = grabber.grabFrame();
				} catch (org.bytedeco.javacv.FrameGrabber.Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				
				
				OpenCVFrameConverter.ToIplImage converterer = new OpenCVFrameConverter.ToIplImage();
				IplImage img = converterer.convert(frame);
				opencv_imgcodecs.cvSaveImage("images/img_" + val.get(0) + "_" + val.get(1) + ".png", img);
				try {
					camera_client.publish("camera/taken", new MqttMessage());
				} catch (MqttException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				**/
				
				
				System.out.println("Webcam got image");
				/**
				try {
					ImageIO.write(img, "png",
							new File(new String("images/img_" + val.get(0) + "_" + val.get(1) + ".png")));
					camera_client.publish("camera/taken", new MqttMessage());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (MqttPersistenceException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (MqttException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				**/
				break;
			case "CAMERA/VIDEO":
				try {
					Thread.sleep(10);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				ArrayList<Double> val2 = gson.fromJson(new String(com_order.get(0).getPayload()),
						new TypeToken<ArrayList<Double>>() {
						}.getType());
				int id = (int) Math.floor(val2.get(0));
				int len = (int) Math.floor(val2.get(1));
				Rational frameRate = Rational.make(1.0 / val2.get(2));

				ArrayList<BufferedImage> imgs = new ArrayList<BufferedImage>();
				for (int x = 0; x < len; x++) {
					File cur_file = new File(new String("images/img_" + id + "_" + x + ".png"));
					if (cur_file.exists())
						try {
							imgs.add(ImageIO.read(cur_file));
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
				}

				String video_name = "";

				for (int x = 0; true; x++) {
					if (!new File(new String("videos/" + x + ".mp4")).exists()) {
						video_name = "videos/" + x + ".mp4";
						break;
					}
				}

				Muxer muxer = Muxer.make(video_name, null, "MP4");

				MuxerFormat format = muxer.getFormat();
				Codec codec = Codec.findEncodingCodec(format.getDefaultVideoCodecId());
				Encoder encoder = Encoder.make(codec);

				encoder.setHeight(imgs.get(0).getHeight());
				encoder.setWidth(imgs.get(0).getWidth());
				PixelFormat.Type pixelformat = PixelFormat.Type.PIX_FMT_YUV420P;
				encoder.setPixelFormat(pixelformat);
				encoder.setTimeBase(frameRate);

				if (format.getFlag(MuxerFormat.Flag.GLOBAL_HEADER))
					encoder.setFlag(Encoder.Flag.FLAG_GLOBAL_HEADER, true);
				encoder.open(null, null);
				muxer.addNewStream(encoder);
				try {
					muxer.open(null, null);
				} catch (InterruptedException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				MediaPictureConverter converter = null;
				final MediaPicture picture = MediaPicture.make(encoder.getWidth(), encoder.getHeight(), pixelformat);
				picture.setTimeBase(frameRate);
				MediaPacket packet = MediaPacket.make();

				for (int i = 0; i < imgs.size(); i++) {
					if (converter == null)
						converter = MediaPictureConverterFactory.createConverter(imgs.get(i), picture);
					converter.toPicture(picture, imgs.get(i), i);

					do {
						encoder.encode(packet, picture);
						if (packet.isComplete())
							muxer.write(packet, false);
					} while (packet.isComplete());
				}
				System.out.println("Video finished");
				do {
					encoder.encode(packet, null);
					if (packet.isComplete())
						muxer.write(packet, false);
				} while (packet.isComplete());
				muxer.close();
				System.out.println("Muxer closed");

				for (int x = 0; x < len; x++) {
					File cur_file = new File(new String("images/img_" + id + "_" + x + ".png"));
					if (cur_file.exists())
						cur_file.delete();
				}

				System.out.println("Images deleted");

				break;
			case "CAMERA/DELETE":			// ACHTUNG: DOPPELTES E UM AUSLÖSUNG ZU VERHINDERN
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
	
	// Generated by OpenAI's ChatGPT
	
	public void startCamera() {
		//nu.pattern.OpenCV.loadShared();
		nu.pattern.OpenCV.loadLocally();
		
	    // initialize the camera
	    camera = new VideoCapture(0);

	    // check if the camera import org.opencv.core.Mat;was successfully opened
	    if (!camera.isOpened()) {
	        System.out.println("Error: could not open camera!");
	        return;
	    }

	    // start the camera
	    camera.open(0);
	}

	public Mat takePicture() {
	    if (!camera.isOpened()) {
	        System.out.println("Error: Camera not open!");
	        return null;
	    }
	    // capture a frame from the camera
	    Mat frame = new Mat();
	    camera.read(frame);

	    // return the frame as a media file
	    return frame;
	}
}
