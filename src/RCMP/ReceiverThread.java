package RCMP;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;



public class ReceiverThread extends Thread {
	
	private final int MTU;
	
	private DatagramSocket socket;
	private BufferedWriter writer;
	public ReceiverThread(int port, String filename, int MTU) throws SocketException, IOException {
		super();
		this.MTU = MTU;
		socket = new DatagramSocket(port);
		socket.setReceiveBufferSize(MTU);
		socket.setSendBufferSize(MTU);
		writer = new BufferedWriter(new FileWriter(filename));
		
	}
	// Logic center for Receiver
	public void run() {
		byte[] buf = new byte[MTU];
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		
		try {
			socket.receive(packet);
			System.out.println("Packet Received");
			String contents = getPayload(packet);
			System.out.println("Getting Contents: " + contents);
			writer.append(contents);
			System.out.println("Contents written");
			writer.close();
			//DatagramPacket sendPacket = new DatagramPacket(payload, payload.length, packet.getAddress(), packet.getPort());
			//socket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
		socket.close();
	}
	
	private String getPayload(DatagramPacket packet) {
		return new String(packet.getData());
	}
}