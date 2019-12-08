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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class SenderThread extends Thread {
	
	private final int MTU = Main.MTU;
	private final int HEADER_SIZE = Main.HEADER_SIZE;
	private final static int TIMEOUT = 4000;
	private DatagramSocket socket;
	private int destinationPort;
	private InetAddress destinationAddress;
	private List<byte[]> payloads;
	private int remainderBytesNumber = 0;
	private int fileSize;
	private int connectionID;
	private int ackGap;
	private int gapCounter;
	
	public SenderThread(String destinationAddress, int destinationPort, String filename) throws SocketException, FileNotFoundException, IOException {
		super();
		setDestinationAddress(destinationAddress);
		setDestinationPort(destinationPort);
		initializeSocket();
		payloads = new ArrayList<byte[]>();
		remainderBytesNumber = readAllBytesIntoPayloadQueue(filename);
		fileSize = (payloads.size() - 1) * MTU + remainderBytesNumber;
		connectionID = new Random().nextInt((int)Math.pow(2, 16));
		ackGap = 0;
		gapCounter = 0;
	}
	
	private void initializeSocket() throws SocketException {
		socket = new DatagramSocket();
		socket.setReceiveBufferSize(HEADER_SIZE + MTU);
		socket.setSendBufferSize(HEADER_SIZE + MTU);
		socket.setSoTimeout(TIMEOUT);
	}
	
	// Logic center for Sender
	public void run() {
		// Send Normal Length Packets
		for(int i = 0; i < payloads.size(); i++) {
			try {
				sendPacket(i);
				receiveACK();
			} catch(SocketTimeoutException e) {
				System.out.println("TImeout reached. Resending last packet...");
				i--; // Go back a step to resend dropped packet
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		socket.close();
		System.out.println("File transfer complete");
		
	}
	
	private void sendPacket(int packetNumber) throws IOException {
		byte[] buffer = payloads.get(packetNumber);
		byte[] header = createHeader(packetNumber);
		byte[] fullPacketBuffer = ByteBuffer.allocate(HEADER_SIZE + MTU).put(header).put(buffer).array();
		DatagramPacket packet = createPacket(fullPacketBuffer);
		if(packetNumber == payloads.size() - 1) {
			packet.setLength(this.remainderBytesNumber + HEADER_SIZE);
		}
		socket.send(packet);
		gapCounter++;
		System.out.println("Sending Packet #" + packetNumber + " of size " + packet.getLength());
		System.out.println("Packet contains: ");
		displayPacket(packet);
	}
	
	private void receiveACK() throws IOException {
		byte[] ackBuffer = new byte[8]; // One byte per character
		DatagramPacket ackPacket = createPacket(ackBuffer);
		socket.receive(ackPacket);
		int connectID = ByteBuffer.wrap(ackBuffer).getInt();
		int lastPacketReceived = ByteBuffer.wrap(ackBuffer).getInt();
		ackGap++;
		gapCounter = 0;
		System.out.println("Received ACK of Packet #" + lastPacketReceived + "on connection " + connectID);
	}
	
	private byte[] createHeader(int packetNumber) {
		byte[] bytesConnectionID = ByteBuffer.allocate(4).putInt(connectionID).array();
		byte[] bytesFileSize = ByteBuffer.allocate(4).putInt(fileSize).array();
		byte[] bytesPacketNumber = ByteBuffer.allocate(4).putInt(packetNumber).array();
		byte[] shouldBeAcked = ByteBuffer.allocate(1).putInt(ackGap == gapCounter ? 1 : 0).array();
		return ByteBuffer.allocate(HEADER_SIZE).put(bytesConnectionID).put(bytesFileSize).put(bytesPacketNumber).put(shouldBeAcked).array();
	}
	
	private void displayPacket(DatagramPacket packet) {
		String address = packet.getAddress().getCanonicalHostName();
		byte[] payload = packet.getData();
		String contents = new String(payload);
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
}
