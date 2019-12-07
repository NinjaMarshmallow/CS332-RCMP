package RCMP;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;



public class ReceiverThread extends Thread {
	
	private final int MTU = Main.MTU;
	private final int HEADER_SIZE = Main.HEADER_SIZE;
	
	private DatagramSocket socket;
	private FileOutputStream stream;
	public ReceiverThread(int port, String filename) throws SocketException, IOException {
		super();
		socket = new DatagramSocket(port);
		socket.setReceiveBufferSize(MTU);
		socket.setSendBufferSize(MTU);
		stream = new FileOutputStream(new File(filename));
		
	}
	// Logic center for Receiver
	public void run() {
		try {
			while(true) {
				DatagramPacket packet = receivePacket();
				writePacketToFile(packet);
				sendACK(packet.getAddress(), packet.getPort());
				if(packet.getLength() < MTU) {
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
		byte[] buffer = new byte[MTU];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		socket.receive(packet);
		System.out.println("Packet Received has size of " + packet.getLength());
		return packet;
	}
	
	private void writePacketToFile(DatagramPacket packet) throws IOException {
		stream.write(packet.getData());
	}
	
	private void sendACK(InetAddress address, int port) throws IOException {
		byte[] ackBuffer = new String("ACK").getBytes(); // ACK
		System.out.println("ACK Buffer is of length " + ackBuffer.length);
		DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length, address, port);
		socket.send(ackPacket);
		System.out.println("ACK Packet Sent");
	}
}