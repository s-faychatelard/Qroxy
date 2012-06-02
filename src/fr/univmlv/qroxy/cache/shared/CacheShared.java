package fr.univmlv.qroxy.cache.shared;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.util.Calendar;

import fr.univmlv.qroxy.cache.Cache;

public class CacheShared{

	private MulticastSocket socket;
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
	
	public SocketChannel sendCacheRequest(String filename, long time){
		byte[] buffer = new byte[12+filename.length()];
		Short size = (short) filename.length();
		buffer[0] = byte1.byteValue();
		buffer[1] = byte2.byteValue();
		ByteBuffer bb = ByteBuffer.allocate(2);
		bb.putShort(size);
		bb.flip();
		buffer[2] = bb.get();
		buffer[3] = bb.get();
		byte[] filenameByte = new byte[filename.length()];
		filenameByte = filename.getBytes();
		byte[] timeBytes = new byte[8];
		bb = ByteBuffer.allocate(8);
		bb.putLong(time);
		bb.flip();
		bb.get(timeBytes);
		for (int i = 0, j = 0; i < filenameByte.length; i++) {
			filenameByte[i] = (byte)(filenameByte[i]^timeBytes[j]);
			j++;
			if(j == 8){
				j= 0;
			}
		}
		for (int i = 0; i < filenameByte.length; i++) {
			buffer[i+4] = filenameByte[i];
		}
		for (int i = 0; i < 8; i++) {
			buffer[i+4+filenameByte.length] = timeBytes[i];
		}
		DatagramPacket p = new DatagramPacket(buffer, 12+filename.length(), this.multicastGroup, this.port);
		try {
			this.socket.send(p);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public void multicastReceive(DatagramPacket dp) throws FileNotFoundException{
		try {
			this.socket.receive(dp);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		byte[] buffer = new byte[dp.getLength()];
		buffer = dp.getData();
		short size;
		byte[] timeBytes = new byte[8];
		if(buffer[0] == byte1.byteValue() && buffer[1] == byte2.byteValue()){
			ByteBuffer bb = ByteBuffer.allocate(2);
			bb.put(buffer, 2, 2);
			bb.flip();
			size = bb.getShort();
			byte[] filenameByte = new byte[size];
			for(int i = 0; i<size; i++){
				filenameByte[i] = buffer[i+4];
			}
			for (int i = 0; i < 8; i++) {
				 timeBytes[i] = buffer[i+4+filenameByte.length];
			}
			for(int i = 0, j = 0; i<size; i++){
				filenameByte[i] = (byte)(filenameByte[i]^timeBytes[j]);
				j++;
				if(j == 8){
					j= 0;
				}
			}
			String filename = new String(filenameByte);
			String contentType = filename.split(";")[0];
			filename = filename.split(";")[1];
			System.out.println(contentType);
			System.out.println(filename);
			ReadableByteChannel channel = Cache.getInstance().isInCache(filename, contentType, false);
			if(channel != null){
				haveFileInCache(filename, Calendar.getInstance().getTimeInMillis());
			}
		}
	}
	
	public void haveFileInCache(String filename, long time){
		byte[] buffer = new byte[12+filename.length()];
		Short size = (short) filename.length();
		buffer[0] = byte2.byteValue();
		buffer[1] = byte1.byteValue();
		ByteBuffer bb = ByteBuffer.allocate(2);
		bb.putShort(size);
		bb.flip();
		buffer[2] = bb.get();
		buffer[3] = bb.get();
		byte[] filenameByte = new byte[filename.length()];
		filenameByte = filename.getBytes();
		byte[] timeBytes = new byte[8];
		bb = ByteBuffer.allocate(8);
		bb.putLong(time);
		bb.flip();
		bb.get(timeBytes);
		for (int i = 0, j = 0; i < filenameByte.length; i++) {
			filenameByte[i] = (byte)(filenameByte[i]^timeBytes[j]);
			j++;
			if(j == 8){
				j= 0;
			}
		}
		for (int i = 0; i < filenameByte.length; i++) {
			buffer[i+4] = filenameByte[i];
		}
		for (int i = 0; i < 8; i++) {
			buffer[i+4+filenameByte.length] = timeBytes[i];
		}
		DatagramPacket p = new DatagramPacket(buffer, 12+filename.length(), this.multicastGroup, this.port);
		try {
			this.socket.send(p);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void fileRequest(){
		
	}
	
	public static void main(String[] args) {
		CacheShared cs = new CacheShared(1234);
		cs.sendCacheRequest("html/text;http://www.google.fr/index.html", Calendar.getInstance().getTimeInMillis());
		
	}

}
