package RCMP;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

public class SenderThread extends Thread {
	private final static String ACK = "ACK";
	private final static int TIMEOUT = 4000;
	private DatagramSocket socket;
	private final int MTU;
	private int destinationPort;
	private InetAddress destinationAddress;
	private BufferedReader reader;
	private byte[] payload;
	private List<byte[]> payloads;
	private int remainderBytesNumber = 0;
	private long time;
	public SenderThread(String destinationAddress, int destinationPort, String filename, int MTU) throws SocketException, FileNotFoundException, IOException {
		super();
		setDestinationAddress(destinationAddress);
		setDestinationPort(destinationPort);
		this.MTU = MTU;
		socket = new DatagramSocket();
		socket.setReceiveBufferSize(MTU);
		socket.setSendBufferSize(MTU);
		socket.setSoTimeout(TIMEOUT);
		payloads = new ArrayList<byte[]>();
		remainderBytesNumber = readAllBytesIntoPayloadQueue(filename);
		//stream.readNBytes(arg0)
//		if(payload.equals(payload2)) {
//			System.out.println("Same!");
//		}
		time = 0;
	}
	
	// Logic center for Sender
	public void run() {
		// Send Normal Length Packets
		for(int i = 0; i < payloads.size(); i++) {
			try {
	
				byte[] buffer = payloads.get(i);
				DatagramPacket packet = createPacket(buffer);
				if(i == payloads.size() - 1) {
					packet.setLength(this.remainderBytesNumber);
				}
				socket.send(packet);
				System.out.println("Sending Packet #" + i + " of size " + packet.getLength());
				byte[] ackBuffer = new byte[3]; // One byte per character
				DatagramPacket ackPacket = createPacket(ackBuffer);
				time = System.currentTimeMillis();
				socket.receive(ackPacket);
				String ack = new String(ackBuffer);
				if(!ack.equals(ACK)) {
					System.out.println("ACK Corrupted");
					System.out.println("Need 'ACK' not " + ack);
				} else {
					System.out.println("ACK Received!!!");
				}
					
				
			} catch(SocketTimeoutException e) {
				long waitTime = System.currentTimeMillis() - time;
				System.out.println("Waited: " + waitTime);
				System.out.println("TImeout reached. Resending last packet...");
				i--;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	private void displayPacket(DatagramPacket packet) {
		String address = packet.getAddress().getCanonicalHostName();
		byte[] payload = packet.getData();
		String contents = new String(payload);
		System.out.println("Message back from Server @ " + address + ":\n");
		System.out.println(contents);
	}
	
	private DatagramPacket createPacket(byte[] bytes) {
		return new DatagramPacket(bytes, bytes.length, destinationAddress, destinationPort);
	}
	
	public void setDestinationAddress(String address) {
		try {
			destinationAddress = InetAddress.getByName(address);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}
	
	public void setDestinationPort(int port) {
		this.destinationPort = port;
	}
	
	private int readAllBytesIntoPayloadQueue(String filename) throws IOException {
		FileInputStream stream = new FileInputStream(new File(filename));
		int remainder = MTU;
		int numberOfBytesRead = MTU;
		while(true) {
			byte[] buffer = new byte[MTU];
			numberOfBytesRead = stream.read(buffer);
			if(numberOfBytesRead == -1) {
				break;
			}
			if(numberOfBytesRead != MTU) {
				remainder = numberOfBytesRead;
			}
			payloads.add(buffer);
		}
		stream.close();
		return remainder;
	}
	
	private byte[] calculateBytes(BufferedReader reader) throws IOException {
		String total = "";
		String line = reader.readLine();
		while(true) {
			total += line;
			line = reader.readLine();
			if(line != null) {
				total += "\n";
			} else {
				break;
			}
		}
		return total.getBytes();
	}
}
