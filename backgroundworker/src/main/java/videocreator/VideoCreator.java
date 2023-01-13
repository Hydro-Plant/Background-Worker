package videocreator;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class VideoCreator {
    public static void createVideo(String imageFiles, double fps, int height, int width, String outputPath) throws Exception {
        // Create the FFmpeg command
        String command;        
        if (System.getProperty("os.name").startsWith("Windows")) {
        	command = "C:\\ffmpeg\\bin\\ffmpeg.exe -i .\\" + imageFiles + " -r " + fps + " -s " + width + "x" + height + " -vcodec libx264 -crf 25 -pix_fmt yuv420p .\\" + outputPath;
        	command = command.replace('/', '\\');
        } else {
        	command = "ffmpeg -i " + imageFiles + " -r " + fps + " -s " + width + "x" + height + " -vcodec libx264 -crf 25 -pix_fmt yuv420p " + outputPath;
        }
        
        // Execute the command
        ProcessBuilder builder = new ProcessBuilder(command);
        Process process = builder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
        process.waitFor();
    }
}