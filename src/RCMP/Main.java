package RCMP;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;

//import java.io.File;
//import java.net.DatagramSocket;
//import java.net.DatagramPacket;
//import java.net.InetAddress;
//import java.net.SocketException;
//import java.net.UnknownHostException;
//import java.io.FileInputStream;
//import java.io.InputStream;
//import java.io.FileNotFoundException;
//import java.io.IOException;


public class Main {
	
	public final static int MTU = 1450;
	public final static int HEADER_SIZE = 13;
	public final static int CONNECTION_ID_LENGTH = 4;
	public final static int FILE_SIZE_LENGTH = 4;
	public final static int PACKET_NUM_LENGTH = 4;
	public final static int SHOULD_ACK_LENGTH = 1;
	
	private enum HostType {
		SENDER, RECEIVER, NONE
	}
	
	// Start Receiver in a new thread
	private void startReceiever(String receiverPort, String filename){
		try {
			new ReceiverThread(Integer.parseInt(receiverPort), filename).start();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// Start Sender in a new thread
	private void startSender(String receiverAddress, String receiverPort, String filename) {
		try {
			new SenderThread(receiverAddress, Integer.parseInt(receiverPort), filename).start();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static HostType getHostType(String[] args) {
		for(int i = 0; i < args.length; i++) {
			String arg = args[i];
			if(arg.equals("-r") && args.length == 3) {
				return HostType.RECEIVER;
			} else if(arg.equals("-s") && args.length == 4) {
				return HostType.SENDER;
			}
		}
		return HostType.NONE;
	}
	
	// MAIN METHOD ---------------------------
	public static void main(String[] args) {
		HostType hostType = getHostType(args);
		Main main = new Main();
		if(hostType == HostType.RECEIVER) {
			System.out.println("Starting Receiver...");
			main.startReceiever(args[1], args[2]);
		} else if(hostType == HostType.SENDER) {
			System.out.println("Starting Send...");
			main.startSender(args[1], args[2], args[3]);
		} else {
			System.out.println("Usage:");
			System.out.println("To make a Sender: java Main -s receiverIPAddress receiverPort filename");
			System.out.println("To make a Receiver: java Main -r receiverPort filename");
		}
	}

}
