package RCMP;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;



public class ReceiverThread extends Thread {
	
	private final int MTU;
	
	private DatagramSocket socket;
	private FileOutputStream stream;
	public ReceiverThread(int port, String filename, int MTU) throws SocketException, IOException {
		super();
		this.MTU = MTU;
		socket = new DatagramSocket(port);
		socket.setReceiveBufferSize(MTU);
		socket.setSendBufferSize(MTU);
		stream = new FileOutputStream(new File(filename));
		
	}
	// Logic center for Receiver
	public void run() {
		try {
			while(true) {
				
			
				byte[] buffer = new byte[MTU];
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				
				
				socket.receive(packet);
				System.out.println("Packet Received");
				System.out.println("Packet has size of " + packet.getLength());
				byte[] saveBuffer = packet.getData();
				stream.write(saveBuffer);
				System.out.println("Contents written");
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
}