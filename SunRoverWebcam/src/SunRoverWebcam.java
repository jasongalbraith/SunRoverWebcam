import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.swing.ImageIcon;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

public class SunRoverWebcam implements Runnable {

	double scale = 0.25;
	final int FIRST_MICROPHONE = 0;
	final int SECOND_MICROPHONE = 1;
	final int AUDIO_OUTPUT = 2;
	final int FIRST_WEBCAM = 3;
	final int SECOND_WEBCAM = 4;
	final int INPUT = 5;
	int threadToStart = FIRST_MICROPHONE;
	ServerSocket server;
	Socket client;
	Socket client2;
	Socket client3;
	ObjectOutputStream stream1;
	ObjectOutputStream stream2;
	InputStream instream;
	DataInputStream dis;
	FrameGrabber grabber1 = new OpenCVFrameGrabber(0);
	FrameGrabber grabber2 = new OpenCVFrameGrabber(1);
	Socket socket;
	int bufferSize;
	byte[] buffer1;
	byte[] buffer2;
	int count1;
	int count2;
	boolean running;
	TargetDataLine line;
	final AudioFormat format = new AudioFormat(8000.0f, 16, 1, true, true);
	//final AudioFormat format = new AudioFormat(1000.0f, 8, 1, true, true);
	OutputStream outputStream;
	//BufferedOutputStream objectOutputStream;
	boolean bufferFull = false;

	public SunRoverWebcam() {
		try {
			server = new ServerSocket(1234);
			socketSetup();
			bufferSize = (int) format.getSampleRate() * format.getFrameSize();
			DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
			line = (TargetDataLine) AudioSystem.getLine(info);
			line.open(format, bufferSize);
			line.start();
			buffer1 = new byte[bufferSize];
			buffer2 = new byte[bufferSize];
			running = true;
			Thread t = new Thread(this);
			t.start();
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (LineUnavailableException ex) {
			ex.printStackTrace();
		}
	}
	
	public void socketSetup() throws IOException {
		System.out.println("Waiting...");
		client = server.accept();
		System.out.println("Got Audio Socket");
		outputStream = new BufferedOutputStream(client.getOutputStream());
		instream = client.getInputStream();
		client = server.accept();
		System.out.println("Got Socket 1");
		dis = new DataInputStream(client.getInputStream());
		stream1 = new ObjectOutputStream(client.getOutputStream());
		client = server.accept();
		System.out.println("Got Socket 2");
		stream2 = new ObjectOutputStream(client.getOutputStream());
	}

	public static void main(String[] args) {
		new SunRoverWebcam();
	}

	public void run() {
		if (threadToStart == FIRST_MICROPHONE) {
			threadToStart = SECOND_MICROPHONE;
			Thread t = new Thread(this);
			t.start();
			while (running) {
				if (bufferFull == false) {
					count1 = line.read(buffer1, 0, buffer1.length);
					//System.out.println(count1);
					buffer2 = buffer1;
					count2 = count1;
					buffer1 = new byte[buffer2.length];
					count1 = 0;
					bufferFull = true;
				}
			}
		}
		else if (threadToStart == SECOND_MICROPHONE) {
			threadToStart = AUDIO_OUTPUT;
			Thread t = new Thread(this);
			t.start();
			try {
				while (running) {
					try {		
						if (bufferFull == true) {
							outputStream.write(buffer2, 0, count2);
							bufferFull = false;
						}
					} catch (IOException ex) {
						if (running == true) {
							socketSetup();
						}
					}
				}
				outputStream.close();
				socket.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		else if (threadToStart == AUDIO_OUTPUT) {
			threadToStart = FIRST_WEBCAM;
			Thread t = new Thread(this);
			t.start();
			try {
				while (running) {
					try {
						AudioFormat format = new AudioFormat(8000.0f, 16, 1, true, true);
						AudioInputStream ais = new AudioInputStream(instream, format, buffer1.length / format.getFrameSize());
						DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
						SourceDataLine sline = (SourceDataLine)AudioSystem.getLine(info);
						sline.open(format);
						sline.start();
						int nBytesRead = 0;
						while (running == true) {
							while (nBytesRead != -1) {
								System.out.println("Inside Loop " + nBytesRead);
								nBytesRead = ais.read(buffer1,0,buffer1.length);
								System.out.println("After Read " + nBytesRead);
								if (nBytesRead >= 0) {
									sline.write(buffer1, 0, nBytesRead);
								}
							}
							nBytesRead = 0;
							ais = new AudioInputStream(instream, format, buffer1.length / format.getFrameSize());
						}
						client.close();
					} catch (Exception ex) {
						ex.printStackTrace();
						System.exit(-1);
					}
				}
				outputStream.close();
				socket.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		else if (threadToStart == FIRST_WEBCAM) {
			threadToStart = SECOND_WEBCAM;
			Thread t = new Thread(this);
			t.start();
			try {
				// Start grabber to capture video
				grabber1.start();
				Frame img1;
				BufferedImage bimg1;
				Image bsend1;
				ImageIcon ii;
				Java2DFrameConverter convert1 = new Java2DFrameConverter();
				while (running) {
					try {
						img1 = grabber1.grab();
						if (img1 != null) {
							// Show video frame in canvas
							bimg1 = convert1.getBufferedImage(img1);
							bsend1 = bimg1.getScaledInstance((int)(bimg1.getWidth()*scale), (int)(bimg1.getHeight()*scale), Image.SCALE_FAST);
							ii = new ImageIcon(bsend1);
							stream1.writeObject(ii);
							stream1.reset();
							stream1.flush();
							//System.out.println("Sent 1");
						}
					} catch (IOException ex) {
						//ex.printStackTrace();
					}
				}
			} catch (org.bytedeco.javacv.FrameGrabber.Exception ex) {
				ex.printStackTrace();
			}
		} else if (threadToStart == SECOND_WEBCAM) {
			threadToStart = INPUT;
			Thread t = new Thread(this);
			t.start();
			// Start grabber to capture video
			try {
				grabber2.start();
				Frame img2;
				BufferedImage bimg2;
				Image bsend2;
				ImageIcon ii;
				Java2DFrameConverter convert2 = new Java2DFrameConverter();
				while (running) {
					try {
						img2 = grabber2.grab();
						if (img2 != null) {		
							// Show video frame in canvas
							bimg2 = convert2.getBufferedImage(img2);
							bsend2 = bimg2.getScaledInstance((int)(bimg2.getWidth()*scale), (int)(bimg2.getHeight()*scale), Image.SCALE_FAST);
							ii = new ImageIcon(bsend2);
							stream2.writeObject(ii);
							stream2.reset();
							stream2.flush();
						}
					} catch (IOException ex) {
						//ex.printStackTrace();
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		else if (threadToStart == INPUT) {
			try {
				while (running) {
					try {
						scale = dis.readDouble();
						if (scale == -1) {
							running = false;
						}
					} catch (IOException ex) {
						//ex.printStackTrace();
					}
				}
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
		
}