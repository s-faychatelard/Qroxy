package fr.univmlv.qroxy.cache.shared;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;

import fr.univmlv.qroxy.cache.Cache;

public class CacheShared{

	private MulticastSocket socket;
	private final static Integer byte1 = 0x53;
	private final static Integer byte2 = 0x4A;
	private final static Integer TIMEOUT = 50;
	private InetAddress multicastGroup = null;
	private int port;
	private InetAddress response=null;
	private String responseFilename=null;
	private final static int BUFFER_SIZE = 262144;
	
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
	
	public static void startService() {
		final CacheShared cs = new CacheShared(4242);
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				while (true) {
					try {
						cs.multicastReceive();
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
				}
			}
		});
	}
	
	public SocketChannel sendCacheRequest(String filename, long time){
		byte[] buffer = encode(filename, time);
		DatagramPacket p = new DatagramPacket(buffer, buffer.length, this.multicastGroup, this.port);
		try {
			this.socket.send(p);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return this.waitResponse(filename);
	}
	
	public SocketChannel waitResponse(String filename) {
		int counter=TIMEOUT;
		while(response == null) {
			if (counter==0) return null;
			try {
				Thread.sleep(1);
			} catch (InterruptedException e1) { return null; }
			counter--;
		}
		while(counter>-1) {
			if (responseFilename != null && responseFilename.equals(filename)) break;
			response=null;
			responseFilename=null;
			try {
				Thread.sleep(1);
			} catch (InterruptedException e1) { return null; }
			counter--;
			if (counter==0) return null;
		}
		SocketChannel s=null;
		try {
			s = SocketChannel.open();
			s.connect(new InetSocketAddress(response.getHostAddress(), 6060));
			response=null;
			responseFilename=null;
		} catch (IOException e) { return null; }
		return s;
	}
	
	public void multicastReceive() throws FileNotFoundException{
		byte[] buffer = new byte[1024];
		final DatagramPacket dp = new DatagramPacket(buffer, buffer.length, this.multicastGroup, this.port);
		try {
			this.socket.setSoTimeout(TIMEOUT);
			this.socket.receive(dp);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		buffer = dp.getData();
		if(buffer[0] == byte1.byteValue() && buffer[1] == byte2.byteValue()){
			String filename = decode(buffer);
			String contentType = filename.split(";")[0];
			filename = filename.split(";")[1];
			ReadableByteChannel channel = Cache.getInstance().isInCache(filename, contentType, false);
			if(channel != null){
				haveFileInCache(filename, Calendar.getInstance().getTimeInMillis());
				try {
					ServerSocket server = new ServerSocket(6060);
					server.getChannel().configureBlocking(false);
					SocketChannel sChannel = server.getChannel().accept();
					
					if (sChannel == null) return;
					MessageDigest md = null;
					try {
						md = MessageDigest.getInstance("SHA-1");
					} catch (NoSuchAlgorithmException e) {
						System.err.println("Cannot generate SHA-1 for url " + filename);
					}
					if (contentType == null)
						contentType = "misc";
					else
						contentType = contentType.split(";")[0];
					byte[] sha1 = new byte[40];
					md.update(filename.getBytes(), 0, filename.length());
					sha1 = md.digest();
					StringBuilder hexSha1 = new StringBuilder();
					for (int i=0;i<sha1.length;i++) {
						hexSha1.append(Integer.toHexString(0xFF & sha1[i]));
					}
					
					File file =  new File(contentType, hexSha1.toString());
					FileInputStream input = new FileInputStream(file);
					ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
					while(input.getChannel().read(bb) != -1) {
						bb.flip();
						sChannel.write(bb);	
						bb.compact();
					}
					sChannel.close();
					channel.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
			}
		}
		if(buffer[0] == byte2.byteValue() && buffer[1] == byte1.byteValue()){
			response = dp.getAddress();
			responseFilename = decode(buffer);
		}
	}
	
	public void haveFileInCache(String filename, long time){
		byte[] buffer = encode(filename, time);
		buffer[0] = byte2.byteValue();
		buffer[1] = byte1.byteValue();
		DatagramPacket p = new DatagramPacket(buffer, buffer.length, this.multicastGroup, this.port);
		try {
			this.socket.send(p);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public byte[] encode(String filename, long time) {
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
		return buffer;
	}
	
	public String decode(byte[] buffer) {
		ByteBuffer bb = ByteBuffer.allocate(2);
		bb.put(buffer, 2, 2);
		bb.flip();
		short size = bb.getShort();
		byte[] timeBytes = new byte[8];
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
		return filename;
	}
	
	public void fileRequest(){
		
	}
	
	public static void main(String[] args) {
		CacheShared cs = new CacheShared(1234);
		cs.sendCacheRequest("html/text;http://www.google.fr/index.html", Calendar.getInstance().getTimeInMillis());
		
	}

}
