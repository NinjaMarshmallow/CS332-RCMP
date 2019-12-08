package RCMP;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;



public class ReceiverThread extends Thread {
	
	private final static int MTU = Main.MTU;
	private final static int HEADER_SIZE = Main.HEADER_SIZE;
	private final static int CONNECTION_ID_LENGTH = Main.CONNECTION_ID_LENGTH;
	private final static int FILE_SIZE_LENGTH = Main.FILE_SIZE_LENGTH;
	private final static int PACKET_NUM_LENGTH = Main.PACKET_NUM_LENGTH;
	private final static int SHOULD_ACK_LENGTH = Main.SHOULD_ACK_LENGTH;
	
	private DatagramSocket socket;
	private FileOutputStream stream;
	private int totalBytesReceived;
	
	class PacketHeader {
		public int connectionID, fileSize, packetNumber, shouldBeAcked; 
		PacketHeader(int connectionID, int fileSize, int packetNumber, int shouldBeAcked) {
			this.connectionID = connectionID;
			this.fileSize = fileSize;
			this.packetNumber = packetNumber;
			this.shouldBeAcked = shouldBeAcked;
		}
	}
	
	public ReceiverThread(int port, String filename) throws SocketException, IOException {
		super();
		socket = new DatagramSocket(port);
		socket.setReceiveBufferSize(MTU + HEADER_SIZE);
		socket.setSendBufferSize(MTU + HEADER_SIZE);
		stream = new FileOutputStream(new File(filename));
		totalBytesReceived = 0;
		
	}
	// Logic center for Receiver
	public void run() {
		try {
			int lastPacketReceived = -1;
			while(true) {
				DatagramPacket packet = receivePacket();
				PacketHeader header = extractHeaderInfo(packet);
				displayHeader(header, packet);
				if(lastPacketReceived + 1 == header.packetNumber) {
					lastPacketReceived++;
					writePacketToFile(packet);
				}
				System.out.println("Should be acked: " + header.shouldBeAcked);
				if (header.shouldBeAcked == 1) {
					sendACK(packet.getAddress(), packet.getPort(), header.connectionID, lastPacketReceived);
				}
				System.out.println("Bytes Received: " + totalBytesReceived + " Bytes to go: " + (header.fileSize - totalBytesReceived));
				if(totalBytesReceived == header.fileSize) {
					System.out.println("Packet has been received in Full.");
					break;
				}
			}
			stream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		socket.close();
	}
	
	private DatagramPacket receivePacket() throws IOException {
		byte[] buffer = new byte[MTU + HEADER_SIZE];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		socket.receive(packet);
		return packet;
	}
	
	private PacketHeader extractHeaderInfo(DatagramPacket packet) {
		byte[] wholeBuffer = packet.getData();
		ByteBuffer byteBuf = ByteBuffer.wrap(wholeBuffer);
		int id = byteBuf.getInt();
		int fileSize = byteBuf.getInt();
		int packetNumber = byteBuf.getInt();
		int shouldBeAcked = byteBuf.get();
		return new PacketHeader(id, fileSize, packetNumber, shouldBeAcked);
	}
	
	private void writePacketToFile(DatagramPacket packet) throws IOException {
		byte[] headerAndPayload = packet.getData();
		byte[] justPayload = Arrays.copyOfRange(headerAndPayload, HEADER_SIZE, MTU + HEADER_SIZE);
		stream.write(justPayload);
		totalBytesReceived += packet.getLength() - HEADER_SIZE;
	}
	
	private void sendACK(InetAddress address, int port, int connectionID, int lastPacketReceived) throws IOException {
		byte[] bytesID = ByteBuffer.allocate(CONNECTION_ID_LENGTH).putInt(connectionID).array();
		byte[] bytesPacketNumber = ByteBuffer.allocate(PACKET_NUM_LENGTH).putInt(lastPacketReceived).array();
		byte[] ackBuffer = ByteBuffer.allocate(CONNECTION_ID_LENGTH + PACKET_NUM_LENGTH).put(bytesID).put(bytesPacketNumber).array();
		DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length, address, port);
		socket.send(ackPacket);
		System.out.println("ACK Packet Sent");
	}
	
	private void displayHeader(PacketHeader header, DatagramPacket packet) {
		System.out.println("Packet #" + header.packetNumber + " Received from connection " + header.connectionID);
		System.out.println("The total file size is " + header.fileSize + " with packet size of " + packet.getLength());
	}
}