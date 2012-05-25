//TODO add path state change listener to upadte treeCache

package fr.univmlv.qroxy.cache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import fr.univmlv.qroxy.cache.tree.TreeCache;

public class Cache {
	private final static Cache instance = new Cache();
	private TreeCache tree = new TreeCache();
	
	public static Cache getInstance() {
		return instance;
	}
	
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
		tree.addPath(arbo.toString()+url);
	}
	
	public boolean isUptodate(String url, String contentType){
		return true;
	}
	
	public boolean isInCache(String url, String contentType) {
		contentType = contentType.split(";")[0];
		url = url.replace("://", "_");
		StringBuilder filename = new StringBuilder(contentType).append("/").append(url);
		File file =  new File(filename.toString());
		if(file.exists())
			return this.isUptodate(url, contentType);
		return false;
	}
	
	public boolean freeSpace(int neededSpace) {
		long size = 0;
		while(size < (long)neededSpace){
			String filename = tree.getSmallerWeightPath();
			File fileToDelete = new File(filename);
			size = size + fileToDelete.length();
			tree.removePath(filename);
			fileToDelete.delete();
		}
		return true;
	}
	
	public static void main(String[] args) throws IOException {
		Cache cache = Cache.getInstance();
		ByteBuffer buffer = ByteBuffer.allocate(10);
		buffer.putInt(60);
		cache.addContentToCache(buffer, "http://www.facebook.com/joach/index.html", "text/html", false);
		cache.addContentToCache(buffer, "http://www.facebook.com/index.html", "text/html", false);
		//cache.addContentToCache(buffer, "http://www.facebook.com/joach/index.html", "text/html", true);
		System.out.println(cache.isInCache("http://www.facebook.com/joach/index.html", "text/html"));
	}
}
