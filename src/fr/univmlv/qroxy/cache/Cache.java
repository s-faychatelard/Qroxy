//TODO add path state change listener to upadte treeCache

package fr.univmlv.qroxy.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import fr.univmlv.qroxy.cache.tree.TreeCache;
import fr.univmlv.qroxy.configuration.Configuration;

public class Cache {
	private final static Cache instance = new Cache();
	private static TreeCache tree = new TreeCache();
	private HashMap<String, Long> sizeMap = new HashMap<String, Long>();
	public static Cache getInstance() {
		return instance;
	}
	public void addContentToCache(ByteBuffer buffer, String url, String contentType, boolean append) throws IOException {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (contentType == null)
			contentType = "misc";
		else
			contentType = contentType.split(";")[0];
		File contentTypeF = new File(contentType);
		contentTypeF.mkdirs();
		byte[] sha1 = new byte[40];
		md.update(url.getBytes(), 0, url.length());
		sha1 = md.digest();
		StringBuilder hexSha1 = new StringBuilder();
		for (int i=0;i<sha1.length;i++) {
			hexSha1.append(Integer.toHexString(0xFF & sha1[i]));
		}
		File file = new File(contentType, hexSha1.toString());
		FileOutputStream output = new FileOutputStream(file, append);
		buffer.flip();
		output.getChannel().write(buffer);
		output.flush();
		output.close();
		long size = 0;
		if (sizeMap.containsKey(contentType))
			size = sizeMap.get(contentType);
		long newSize = file.length()+size;
		sizeMap.put(contentType, newSize);
		
		url = url.replace("://", "_");
		tree.addPath(url);
	}

	public void getFromCache(String url, String contentType, Pipe.SinkChannel channel) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(262144);
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (contentType == null)
			contentType = "misc";
		else
			contentType = contentType.split(";")[0];
		byte[] sha1 = new byte[40];
		md.update(url.getBytes(), 0, url.length());
		sha1 = md.digest();
		StringBuilder hexSha1 = new StringBuilder();
		for (int i=0;i<sha1.length;i++) {
			hexSha1.append(Integer.toHexString(0xFF & sha1[i]));
		}
		File file =  new File(contentType, hexSha1.toString());
		FileInputStream input = new FileInputStream(file);
		while(input.getChannel().read(buffer) != -1){
			buffer.flip();
			channel.write(buffer);
			buffer.compact();
		}
		channel.close();
		input.close();
		
		url = url.replace("://", "_");
		tree.addPath(url);
	}

	public boolean isUptodate(String url, String contentType){
		return true;
	}

	public boolean isInCache(String url, String contentType) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (contentType == null)
			contentType = "misc";
		else
			contentType = contentType.split(";")[0];
		byte[] sha1 = new byte[40];
		md.update(url.getBytes(), 0, url.length());
		sha1 = md.digest();
		StringBuilder hexSha1 = new StringBuilder();
		for (int i=0;i<sha1.length;i++) {
			hexSha1.append(Integer.toHexString(0xFF & sha1[i]));
		}
		File file =  new File(contentType, hexSha1.toString());
		if(file.exists() && !file.isDirectory())
			return this.isUptodate(url, contentType);
		return false;
	}

	public boolean freeSpace(long neededSpace, String contentType) {
		long size = 0;
		if (contentType == null)
			contentType = "misc";
		else
			contentType = contentType.split(";")[0];
		if (sizeMap.containsKey(contentType))
			size = sizeMap.get(contentType);
		if((Configuration.getInstance().getConfForType(contentType).getSize() - size) > neededSpace)
			return true;
		long deletedSize = 0;
		while(deletedSize < neededSpace){
			String filename = tree.getSmallerWeightPath();
			File fileToDelete = new File(filename);
			deletedSize = deletedSize + fileToDelete.length();
			if(fileToDelete.delete())
				tree.removePath(filename);
		}
		long newSize = sizeMap.get(contentType) - deletedSize;
		sizeMap.put(contentType, newSize);
		return true;
	}

	public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
		Cache cache = Cache.getInstance();
		ByteBuffer buffer = ByteBuffer.allocate(200);
		buffer.put("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".getBytes());
		cache.addContentToCache(buffer, "http://www.facebook.com/joach/index.html", "text/html", false);
		cache.addContentToCache(buffer, "http://www.facebook.com/index.html", "text/html", false);
		cache.addContentToCache(buffer, "http://www.google.com/index.html", "text/html", true);
		System.out.println(cache.isInCache("http://www.facebook.com/joach/index.html", "text/html"));
		System.out.println(cache.isInCache("http://www.google.com/index.html", "text/html"));
		System.out.println(cache.isInCache("http://www.google.com/toto.html", "text/html"));
		System.out.println(tree);
	}
}
