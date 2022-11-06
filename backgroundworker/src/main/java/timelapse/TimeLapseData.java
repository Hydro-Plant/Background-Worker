package timelapse;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

public class TimeLapseData {
	public String date_from;
	public String time_from;
	public String date_to;
	public String time_to;

	public double speed;
	public String duration;
	public double frameRate;
	public int pictures;
	public int mode;

	public int id = -1;

	public TimeLapseData() {

	}

	public TimeLapseData(LocalDate date_from, LocalTime time_from, LocalDate date_to, LocalTime time_to, double speed, Duration duration, double frameRate, int pictures, int id, int mode) {
		this.date_from = date_from.toString();
		this.time_from = time_from.toString();
		this.date_to = date_to.toString();
		this.time_to = time_to.toString();

		this.speed = speed;
		this.duration = duration.toString();
		this.frameRate = frameRate;
		this.pictures = pictures;
		this.mode = mode;

		this.id = id;
	}

	public TimeLapseData(LocalDate date_from, LocalTime time_from, LocalDate date_to, LocalTime time_to, double speed, Duration duration, double frameRate, int pictures, int mode) {
		this.date_from = date_from.toString();
		this.time_from = time_from.toString();
		this.date_to = date_to.toString();
		this.time_to = time_to.toString();

		this.speed = speed;
		this.duration = duration.toString();
		this.frameRate = frameRate;
		this.pictures = pictures;
		this.mode = mode;

		this.id = -1;
	}
}
