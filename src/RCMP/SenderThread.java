package RCMP;

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
import java.util.List;
import java.util.Random;

public class SenderThread extends Thread {
	
	// Shared constants between Sender and Receiver
	private final static int MTU = Main.MTU;
	private final static int HEADER_SIZE = Main.HEADER_SIZE;
	private final static int CONNECTION_ID_LENGTH = Main.CONNECTION_ID_LENGTH;
	private final static int FILE_SIZE_LENGTH = Main.FILE_SIZE_LENGTH;
	private final static int PACKET_NUM_LENGTH = Main.PACKET_NUM_LENGTH;
	private final static int SHOULD_ACK_LENGTH = Main.SHOULD_ACK_LENGTH;
	
	private final static int TIMEOUT = 1000;
	private DatagramSocket socket;
	private int destinationPort;
	private InetAddress destinationAddress;
	private List<byte[]> payloads;
	private int remainderBytesNumber = 0;
	private int fileSize;
	private int connectionID;
	private int ackGap;
	private int gapCounter;
	private int timeouts = 0;
	
	/**
	 * 
	 * @param destinationAddress
	 * @param destinationPort
	 * @param filename
	 * @throws SocketException
	 * @throws FileNotFoundException
	 * @throws IOException
	 * 
	 * The SenderThread is the main logic center of the RCMP Sender Socket. 
	 * It accepts a destination address of a server as well as its port. Then 
	 * using a given filename, it loads the file and transfers the file over
	 * the network to the the destination address using a reliable protocol
	 * that expects packets to be dropped occasionally and accounts for it 
	 * with acknowledgements.
	 */
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
	
	/**
	 * 
	 * @throws SocketException
	 * Sets up the socket object
	 */
	private void initializeSocket() throws SocketException {
		socket = new DatagramSocket();
		socket.setReceiveBufferSize(HEADER_SIZE + MTU);
		socket.setSendBufferSize(HEADER_SIZE + MTU);
		socket.setSoTimeout(TIMEOUT);
	}
	
	/**
	 * Main execution loop
	 * 
	 * Loops through the List of payloads (byte arrays) and sends a packet
	 * containing each byte array. Rewinds to send already sent packets
	 * if the receiver failed to receive some packets, through receiving 
	 * ACK packets from the receiver. 
	 * Checks for the absence of a receiver using a timeout.
	 */
	public void run() {
		int lastPacket = 0;
		for(int i = 0; i < payloads.size(); i++) {
			try {
				sendPacket(i);
				boolean isLastPacket = i == payloads.size() - 1;
				boolean shouldACK = ackGap == gapCounter;
				if(shouldACK || isLastPacket) {
					i = receiveACK();
					timeouts = 0;
					System.out.println("Last Packet: " + i);
				}
				gapCounter++;
			} catch(SocketTimeoutException e) {
				System.out.println("Timeout reached. Resending last packet...");
				timeouts++;
				if(timeouts >= 5) {
					lastPacket = i; // Save last packet number to decide if the transfer failed or is unknown status
					break;
				}
				i--; // Go back a step to resend dropped packet
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		socket.close();
		boolean lastACKDropped = timeouts >= 5 && lastPacket == this.payloads.size() - 1;
		boolean noReponse = timeouts >= 5;
		if(lastACKDropped) {
			
			System.out.println("File transfer success unknown.");
		} else if(noReponse) {
			
			System.out.println("File Transfer Failure.");
		} else {
			System.out.println("File transfer complete");
		}
	}
	
	/**
	 * 
	 * @param packetNumber
	 * @throws IOException
	 * 
	 * Builds a packet by using a index to access the correct byte[] in the 
	 * list of byte chunk payloads. Then sends the packet using the socket. 
	 */
	private void sendPacket(int packetNumber) throws IOException {
		byte[] buffer = payloads.get(packetNumber);
		byte[] header = createHeader(packetNumber);
		byte[] fullPacketBuffer = ByteBuffer.allocate(HEADER_SIZE + MTU).put(header).put(buffer).array();
		DatagramPacket packet = createPacket(fullPacketBuffer);
		if(packetNumber == payloads.size() - 1) {
			packet.setLength(this.remainderBytesNumber + HEADER_SIZE);
		}
		socket.send(packet);
		
		System.out.println("Sending Packet #" + packetNumber + " of size " + packet.getLength());
		System.out.println("Packet contains: ");
		displayPacket(packet);
	}
	
	/**
	 * 
	 * @return The sequence number of the last packet received by the server.
	 * @throws IOException
	 * 
	 * Initializes an empty buffer to read in the acknowledgement from the server.
	 * Deciphers the packet and returns the sequence number so that the sender
	 * knows which packet the server has received up to. 
	 * Ex. If the last packet number received was 8, then the server has received
	 * all packets #0 through #8. So then the sender should send #9
	 */
	private int receiveACK() throws IOException {
		byte[] ackBuffer = new byte[CONNECTION_ID_LENGTH + PACKET_NUM_LENGTH];
		DatagramPacket ackPacket = createPacket(ackBuffer);
		socket.receive(ackPacket);
		ByteBuffer byteBuffer = ByteBuffer.wrap(ackBuffer);
		int connectID = byteBuffer.getInt();
		int lastPacketReceived = byteBuffer.getInt();
		ackGap++;
		gapCounter = 0;
		System.out.println("Received ACK of Packet #" + lastPacketReceived + " on connection " + connectID);
		return lastPacketReceived;
	}
	
	/**
	 * 
	 * @param packetNumber
	 * @return returns the byte[] of size HEADER_SIZE holding all header information
	 * 
	 * Creates a byte array that holds all header information.  
	 */
	private byte[] createHeader(int packetNumber) {
		byte[] bytesConnectionID = ByteBuffer.allocate(CONNECTION_ID_LENGTH).putInt(connectionID).array();
		byte[] bytesFileSize = ByteBuffer.allocate(FILE_SIZE_LENGTH).putInt(fileSize).array();
		byte[] bytesPacketNumber = ByteBuffer.allocate(PACKET_NUM_LENGTH).putInt(packetNumber).array();
		boolean shouldAck = (ackGap == gapCounter || packetNumber == payloads.size() - 1);
		byte ackByte = (byte) (shouldAck ? 1 : 0);
		byte[] shouldBeAcked = ByteBuffer.allocate(SHOULD_ACK_LENGTH).put(ackByte).array();
		System.out.println("AckByte: " + ackByte);
		return ByteBuffer.allocate(HEADER_SIZE).put(bytesConnectionID).put(bytesFileSize).put(bytesPacketNumber).put(shouldBeAcked).array();
	}
	
	/**
	 * 
	 * @param packet
	 * 
	 * Assumes the packet is sending ascii characters in the file.
	 * Interprets the bytes in the packet as text and prints
	 * them to the console.
	 */
	private void displayPacket(DatagramPacket packet) {
		byte[] payload = packet.getData();
		String contents = new String(payload);
		System.out.println(contents);
	}
	
	
	/**
	 * 
	 * @param bytes
	 * @return - A DatagramPacket filled with the given byte array
	 * 
	 * Simplification of the DatagramPacket constructor since
	 * the destinationAddress and destinationPort are constants
	 * per file transfer.
	 */
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
	
	/**
	 * 
	 * @param filename
	 * @return The number of bytes of the last chunk of the file data. 
	 * @throws IOException
	 * 
	 * Reads in the file using a filename String. Splits up the file into chunks
	 * of size MTU (Maximum Transmission Unit) to be stuffed into DatagramPackets.
	 * The number of packets will be Ceiling( File Size / MTU )
	 * Once the file is sliced up and each chunk is saved into the payloads array,
	 * the number of bytes of the last chunk which is provably less than or equal
	 * to the MTU, is returned.
	 */
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
