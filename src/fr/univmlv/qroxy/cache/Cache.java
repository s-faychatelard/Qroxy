package fr.univmlv.qroxy.cache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class Cache {
	private final static Cache instance = new Cache();
	
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
		StringBuilder arbo = new StringBuilder();
		contentTypeF.mkdirs();
		arbo.append(contentType).append("/");
		for (int i = 0; i < f.length-1 ; i++) {
			arbo.append(f[i]).append("/");
			File rep = new File(arbo.toString());
			rep.mkdir();
		}
		
		File file = new File(arbo.toString(), url);
		FileOutputStream output = new FileOutputStream(file, append);
		buffer.flip();
		output.getChannel().write(buffer);
		output.flush();
		output.close();
	}
	
	public boolean isInCache(String url, String contentType) {
		//TODO contentType can be null
		contentType = contentType.split(";")[0];
		url = url.replace("://", "_");
		StringBuilder filename = new StringBuilder(contentType).append("/").append(url);
		File file =  new File(filename.toString());
		return file.exists();
	}
	
	public boolean freeSpace(int neededSpace) {
		
		return true;
	}
	
	public static void main(String[] args) throws IOException {
		Cache cache = Cache.getInstance();
		ByteBuffer buffer = ByteBuffer.allocate(10);
		buffer.putInt(60);
		cache.addContentToCache(buffer, "http://www.facebook.com/joach//video/", "text/html", false);
		//cache.addContentToCache(buffer, "http://www.facebook.com/joach/index.html", "text/html", true);
		System.out.println(cache.isInCache("http://www.facebook.com/joach/index.html", "text/html"));
	}
}
