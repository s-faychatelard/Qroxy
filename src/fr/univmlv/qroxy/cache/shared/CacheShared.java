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
		byte[] filenameByte = new byte[filename.length()];
		
		byte[] timeByte = time.toString().getBytes();
		for (int i = 2; i < buffer.length; i++) {
			if(i<34){
				buffer[i] = filenameByte[i-4];
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
		String filename;
		byte[] timeByte = new byte[4];
		byte[] sha256 = new byte[32];
		byte[] buffer = new byte[BUFFER_SIZE];
		buffer = dp.getData();
		if(buffer[0] == byte1.byteValue() && buffer[1] == byte2.byteValue()){
			for (int i = 2; i < buffer.length; i++) {
				if(i<34){
					sha256[i-2] = buffer[i];
					
				}else{
					timeByte[i-34] = buffer[i];
				}
			}
		}
	}
	
	public static void main(String[] args) {
		
	}

}
