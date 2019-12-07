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
	
	private final int MTU = Main.MTU;
	private final int HEADER_SIZE = Main.HEADER_SIZE;
	
	private DatagramSocket socket;
	private FileOutputStream stream;
	
	class PacketHeader {
		public int connectionID, fileSize, packetNumber; 
		PacketHeader(int connectionID, int fileSize, int packetNumber) {
			this.connectionID = connectionID;
			this.fileSize = fileSize;
			this.packetNumber = packetNumber;
		}
	}
	
	public ReceiverThread(int port, String filename) throws SocketException, IOException {
		super();
		socket = new DatagramSocket(port);
		socket.setReceiveBufferSize(MTU + HEADER_SIZE);
		socket.setSendBufferSize(MTU + HEADER_SIZE);
		stream = new FileOutputStream(new File(filename));
		
	}
	// Logic center for Receiver
	public void run() {
		try {
			while(true) {
				DatagramPacket packet = receivePacket();
				writePacketToFile(packet);
				sendACK(packet.getAddress(), packet.getPort());
				if(packet.getLength() < MTU + HEADER_SIZE) {
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
		PacketHeader header = extractHeaderInfo(packet);
		System.out.println("Packet #" + header.packetNumber + " Received from connection " + header.connectionID);
		System.out.println("The total file size is " + header.fileSize + " with packet size of " + packet.getLength());
		return packet;
	}
	
	private PacketHeader extractHeaderInfo(DatagramPacket packet) {
		byte[] wholeBuffer = packet.getData();
		byte[] bytesConnectionID = new byte[4];
		byte[] bytesFileSize = new byte[4];
		byte[] bytesPacketNumber = new byte[4];
		for(int i = 0; i < wholeBuffer.length; i++) {
			if(i < 4) {
				bytesConnectionID[i] = wholeBuffer[i];
			} else if(i < 8) {
				bytesFileSize[i - 4] = wholeBuffer[i];
			} else if(i < 12) {
				bytesPacketNumber[i - 8] = wholeBuffer[i];
			}
		}
		int id = ByteBuffer.wrap(bytesConnectionID).getInt();
		int fileSize = ByteBuffer.wrap(bytesFileSize).getInt();
		int packetNumber = ByteBuffer.wrap(bytesPacketNumber).getInt();
		return new PacketHeader(id, fileSize, packetNumber);
	}
	
	private void writePacketToFile(DatagramPacket packet) throws IOException {
		byte[] headerAndPayload = packet.getData();
		byte[] justPayload = Arrays.copyOfRange(headerAndPayload, 12, MTU + HEADER_SIZE);
		stream.write(justPayload);
	}
	
	private void sendACK(InetAddress address, int port) throws IOException {
		byte[] ackBuffer = new String("ACK").getBytes(); // ACK
		DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length, address, port);
		socket.send(ackPacket);
		System.out.println("ACK Packet Sent");
	}
}