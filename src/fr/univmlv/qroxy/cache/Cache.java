//TODO add path state change listener to upadte treeCache

package fr.univmlv.qroxy.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import fr.univmlv.qroxy.cache.tree.TreeCache;

public class Cache {
	private final static Cache instance = new Cache();
	private TreeCache tree = new TreeCache();

	public static Cache getInstance() {
		return instance;
	}

	// TODO name too long
	// TODO not a directory
	public void addContentToCache(ByteBuffer buffer, String url, String contentType, boolean append) throws IOException {
		contentType = contentType.split(";")[0];
		File contentTypeF = new File(contentType);
		url = url.replace("://", "_");
		String[] f = url.split("/");
		url = f[f.length -1];
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		byte[] sha1 = new byte[40];
		md.update(url.getBytes(), 0, url.length());
		sha1 = md.digest();
		StringBuilder arbo = new StringBuilder();
		contentTypeF.mkdirs();
		arbo.append(contentType).append("/");
		for (int i = 0; i < f.length-1 ; i++) {
			arbo.append(f[i]).append("/");
			File rep = new File(arbo.toString());
			rep.mkdir();
		}
		StringBuilder hexSha1 = new StringBuilder();
		for (int i=0;i<sha1.length;i++) {
			hexSha1.append(Integer.toHexString(0xFF & sha1[i]));
		}
		File file = new File(arbo.toString(), hexSha1.toString());
		FileOutputStream output = new FileOutputStream(file, append);
		buffer.flip();
		output.getChannel().write(buffer);
		output.flush();
		output.close();
		tree.addPath(arbo.toString()+url);
	}

	public void getFromCache(String url, String contentType, Pipe.SinkChannel channel) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(262144);
		contentType = contentType.split(";")[0];
		url = url.replace("://", "_");
		StringBuilder filename = new StringBuilder(contentType).append("/").append(url);
		File file =  new File(filename.toString());
		FileInputStream input = new FileInputStream(file);
		while(input.getChannel().read(buffer) != -1){
			buffer.flip();
			channel.write(buffer);
			buffer.compact();
		}
		channel.close();
		input.close();
	}

	public boolean isUptodate(String url, String contentType){
		return true;
	}

	public boolean isInCache(String url, String contentType) {
		//TODO contentType can be null
		contentType = contentType.split(";")[0];
		url = url.replace("://", "_");
		StringBuilder filename = new StringBuilder(contentType).append("/").append(url);
		
		File file =  new File(filename.toString());
		if(file.exists())
			return this.isUptodate(url, contentType);
		return false;
	}

	public boolean freeSpace(long neededSpace) {
		/*long size = 0;
		while(size < neededSpace){
			String filename = tree.getSmallerWeightPath();
			File fileToDelete = new File(filename);
			size = size + fileToDelete.length();
			if(fileToDelete.delete())
				tree.removePath(filename);
		}*/
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
		System.out.println(cache.freeSpace(100));
		System.out.println(cache.isInCache("http://www.google.com/index.html", "text/html"));
		System.out.println(cache.isInCache("http://www.google.com/", "text/html"));
	}
}
