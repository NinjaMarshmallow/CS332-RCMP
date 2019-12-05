package RCMP;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class SenderThread extends Thread {
	private DatagramSocket socket;
	private final int MTU;
	private int destinationPort;
	private InetAddress destinationAddress;
	private BufferedReader reader;
	private byte[] payload;
	public SenderThread(String destinationAddress, int destinationPort, String filename, int MTU) throws SocketException, FileNotFoundException, IOException {
		super();
		setDestinationAddress(destinationAddress);
		setDestinationPort(destinationPort);
		this.MTU = MTU;
		socket = new DatagramSocket();
		socket.setReceiveBufferSize(MTU);
		socket.setSendBufferSize(MTU);
		
		reader = new BufferedReader(new FileReader(filename));
		payload = calculateBytes(reader);
		System.out.println("File size: " + payload.length);
	}
	
	// Logic center for Sender
	public void run() {
		DatagramPacket packet = new DatagramPacket(payload, payload.length, destinationAddress, destinationPort);
		try {
			socket.send(packet);
			System.out.println("Packet Sent.");
			//DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
			//socket.receive(receivePacket);
			//displayPacket(receivePacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void displayPacket(DatagramPacket packet) {
		String address = packet.getAddress().getCanonicalHostName();
		byte[] payload = packet.getData();
		String contents = new String(payload);
		System.out.println("Message back from Server @ " + address + ":\n");
		System.out.println(contents);
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
	
	private byte[] calculateBytes(BufferedReader reader) throws IOException {
		String total = "";
		String line;
		while((line = reader.readLine()) != null) {
			total += line + "\n";
		}
		return total.getBytes();
	}
}
