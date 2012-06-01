package fr.univmlv.qroxy.cache.shared;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Timestamp;

public class CacheShared{

	private MulticastSocket socket;
	private final static int BUFFER_SIZE = 96;
	private final static Integer byte1 = 0x53;
	private final static Integer byte2 = 0x4A;
	private InetAddress multicastGroup = null;
	private int port;
	
	public CacheShared(int port) {
		this.port = port;
		try {
			multicastGroup = InetAddress.getByName("242.42.42.42");
			socket = new MulticastSocket(port);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void sendCacheRequest(String filename, Timestamp time){
		byte[] buffer = new byte[BUFFER_SIZE];
		buffer[0] = byte1.byteValue();
		buffer[1] = byte2.byteValue();
		byte[] sha256 = new byte[32];
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		md.update(filename.getBytes(), 0, filename.length());
		sha256 = md.digest();
		byte[] timeByte = time.toString().getBytes();
		for (int i = 2; i < buffer.length; i++) {
			if(i<34){
				buffer[i] = sha256[i-2];
			}else{
				buffer[i] = timeByte[i-34];
			}
		}
		DatagramPacket p = new DatagramPacket(buffer, BUFFER_SIZE, this.multicastGroup, this.port);
		try {
			this.socket.send(p);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void multicastReceive(DatagramPacket dp){
		byte[] buffer = new byte[BUFFER_SIZE];
		dp.getData();
	}
	
	public static void main(String[] args) {
		
	}

}
