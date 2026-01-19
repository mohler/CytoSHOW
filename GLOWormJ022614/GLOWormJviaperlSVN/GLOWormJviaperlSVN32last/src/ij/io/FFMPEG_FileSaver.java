package ij.io;

	import java.util.ArrayList;

	import java.util.List;

import ij.util.NativeTools;

import java.io.File;
import java.io.IOException;

			
				
	public class FFMPEG_FileSaver {

		static File ffmpegBin;

		public static void convertSequence(String tiffFolderString, String outputFileString, String fpsString) {

			File tiffFolder  = new File(tiffFolderString);
			File outputFile = new File(outputFileString);
			Double fps = Double.valueOf(fpsString);

			List<String> cmd = new ArrayList<>();
			try {
				ffmpegBin = NativeTools.getBundledBinary("ffmpeg");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// 1. The Binary

			cmd.add(ffmpegBin.getAbsolutePath()); 



			// 2. Global Flags

			cmd.add("-y"); // Overwrite output without asking



			// 3. Input Setup

			cmd.add("-f");

			cmd.add("image2");

			cmd.add("-framerate");
			cmd.add(String.valueOf(fps));




			// The Input Pattern: %05d matches frame_00000.tif

			// We construct the full path pattern for ffmpeg to interpret

			String inputPattern = tiffFolder.getAbsolutePath();

			cmd.add("-i");

			cmd.add(inputPattern+"/%d.tif");



			// 4. Output Logic (The Switch)

			String filename = outputFile.getName().toLowerCase();



			if (filename.endsWith(".mp4")) {

				// H.264 Standard

				cmd.add("-c:v");

				cmd.add("libx264");

				cmd.add("-pix_fmt");

				cmd.add("yuv420p"); 

				cmd.add("-crf");

				cmd.add("20");

				cmd.add("-preset");

				cmd.add("slow");

			} 

			else if (filename.endsWith(".webm")) {

				// VP9 Web

				cmd.add("-c:v");

				cmd.add("libvpx-vp9");

				cmd.add("-b:v");

				cmd.add("0");

				cmd.add("-crf");

				cmd.add("30");

			}

			else if (filename.endsWith(".mov")) {

				// ProRes HQ

				cmd.add("-c:v");

				cmd.add("prores_ks");

				cmd.add("-profile:v");

				cmd.add("3");

			}

			else {

				// Fallback

				System.out.println("Unknown extension, defaulting to libx264");

				// H.264 Standard

				cmd.add("-c:v");

				cmd.add("libx264");

				cmd.add("-pix_fmt");

				cmd.add("yuv420p"); 

				cmd.add("-crf");

				cmd.add("20");

				cmd.add("-preset");

				cmd.add("slow");

			}



			// 5. The Output File

			cmd.add("-vf"); 
			cmd.add("pad=ceil(iw/2)*2:ceil(ih/2)*2");
			cmd.add(outputFile.getAbsolutePath());



			// 6. Execute

			try {

				ProcessBuilder pb = new ProcessBuilder(cmd);

				pb.redirectErrorStream(true); 

				pb.inheritIO(); 



				System.out.println("Running FFmpeg: " + String.join(" ", cmd));



				Process p = pb.start();

				int exitCode = p.waitFor();



				if (exitCode == 0) {

					System.out.println("Conversion complete: " + outputFile.getAbsolutePath());

				} else {

					System.err.println("FFmpeg failed with error code: " + exitCode);

				}

			} catch (Exception e) {

				e.printStackTrace();

			}

		}
}
