/** ReceiverThread.java is a receiver for the Reliable Calvin Messaging Protocol.
 *  It starts on a given port and waits for a SenderThread to connect, then reliably
 *  receives the message and writes it to a given filename
 *  
 *  @author Jon Ellis, Jason Klaassen
 *  For CS332 at Calvin University
 *  
 *  usage: java RCMP.Main -r receiverPort filename
 */
package RCMP;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;


public class ReceiverThread extends Thread {
	
	private final static int MTU = Main.MTU;
	private final static int HEADER_SIZE = Main.HEADER_SIZE;
	private final static int CONNECTION_ID_LENGTH = Main.CONNECTION_ID_LENGTH;
	private final static int PACKET_NUM_LENGTH = Main.PACKET_NUM_LENGTH;
	
	private DatagramSocket socket;
	private FileOutputStream stream;
	private int totalBytesReceived;
	
	// PacketHeader defines the header fields as specified by the RCMP protocol
	class PacketHeader {
		public int connectionID, fileSize, packetNumber, shouldBeAcked; 
		
		/** 
		 * The explicit constructor for PacketHeader
		 * @param connectionID the unique ID for the connection
		 * @param fileSize the total size of the file (in bytes)
		 * @param packetNumber the sequential number of the packet
		 * @param shouldBeAcked a boolean representing whether the packed should be ACK'd or not
		 */
		PacketHeader(int connectionID, int fileSize, int packetNumber, int shouldBeAcked) {
			this.connectionID = connectionID;
			this.fileSize = fileSize;
			this.packetNumber = packetNumber;
			this.shouldBeAcked = shouldBeAcked;
		}
	}
	
	/** 
	 * The explicit constructor for ReceiverThread
	 * @param port the port of the socket
	 * @param filename the name of the file to be written to
	 * @throws SocketException
	 * @throws IOException
	 */
	public ReceiverThread(int port, String filename) throws SocketException, IOException {
		super();
		socket = new DatagramSocket(port);
		socket.setReceiveBufferSize(MTU + HEADER_SIZE);
		socket.setSendBufferSize(MTU + HEADER_SIZE);
		stream = new FileOutputStream(new File(filename));
		totalBytesReceived = 0;
		
	}
	
	/**
	 * Main execution loop
	 * 
	 * Receives and parses a packet from the SenderThread. Then writes the packet
	 * payload to the file if the packet is the correct next one to be written 
	 * (i.e. it has the next packet number). Then sends an ACK to the SenderThread
	 * if indicated in the header that the packet should be ACK'd
	 */
	public void run() {
		try {
			int lastPacketReceived = -1;
			while(true) {
				DatagramPacket packet = receivePacket();
				PacketHeader header = extractHeaderInfo(packet);
				displayHeader(header, packet);
				//only write to the file if the next packet received has the next packet number
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
	
	/** 
	 * receivePacket() gets a DatagramPacket from the socket of size MTU + HEADER_SIZE
	 * @return a DatagramPacket from the SenderThread
	 * @throws IOException
	 */
	private DatagramPacket receivePacket() throws IOException {
		byte[] buffer = new byte[MTU + HEADER_SIZE];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		socket.receive(packet);
		return packet;
	}
	
	/** 
	 * extractHeaderInfo(packet) takes the header from the received packet 
	 * 	and puts it in a PacketHeader
	 * @param packet a DatagramPacket from which to extract the header
	 * @return a PacketHeader initialized with the header data from packet
	 */
	private PacketHeader extractHeaderInfo(DatagramPacket packet) {
		byte[] wholeBuffer = packet.getData();
		ByteBuffer byteBuf = ByteBuffer.wrap(wholeBuffer);
		int id = byteBuf.getInt();
		int fileSize = byteBuf.getInt();
		int packetNumber = byteBuf.getInt();
		int shouldBeAcked = byteBuf.get();
		return new PacketHeader(id, fileSize, packetNumber, shouldBeAcked);
	}
	
	/** 
	 * writePacketToFile(packet) writes the payload from packet to a file
	 * @param packet the DatagramPacket to extract the data from
	 * @throws IOException
	 */
	private void writePacketToFile(DatagramPacket packet) throws IOException {
		stream.write(packet.getData(), HEADER_SIZE, packet.getLength() - HEADER_SIZE);
		totalBytesReceived += packet.getLength() - HEADER_SIZE;
	}
	
	/** 
	 * sendACK(address, port, connectionID, lastPacketReceived) sends an ACK
	 *  to the address and port
	 * @param address the address to send the ACK to
	 * @param port the port to send the ACK to
	 * @param connectionID the unique connectionID of the received packet being ACK'd
	 * @param lastPacketReceived the packet number of the packet being ACK'd
	 * @throws IOException
	 */
	private void sendACK(InetAddress address, int port, int connectionID, int lastPacketReceived) throws IOException {
		byte[] bytesID = ByteBuffer.allocate(CONNECTION_ID_LENGTH).putInt(connectionID).array();
		byte[] bytesPacketNumber = ByteBuffer.allocate(PACKET_NUM_LENGTH).putInt(lastPacketReceived).array();
		byte[] ackBuffer = ByteBuffer.allocate(CONNECTION_ID_LENGTH + PACKET_NUM_LENGTH).put(bytesID).put(bytesPacketNumber).array();
		DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length, address, port);
		socket.send(ackPacket);
		System.out.println("ACK Packet Sent");
	}
	
	/** 
	 * displayHeader(header, packet) prints the data in the extracted header
	 * @param header the PacketHeader to be displayed
	 * @param packet the DatagramPacket that contains the header
	 */
	private void displayHeader(PacketHeader header, DatagramPacket packet) {
		System.out.println("Packet #" + header.packetNumber + " Received from connection " + header.connectionID);
		System.out.println("The total file size is " + header.fileSize + " with packet size of " + packet.getLength());
	}
}