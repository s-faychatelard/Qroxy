//TODO add path state change listener to upadte treeCache

package fr.univmlv.qroxy.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Set;

import fr.univmlv.qroxy.cache.shared.CacheShared;
import fr.univmlv.qroxy.cache.tree.TreeCache;
import fr.univmlv.qroxy.configuration.Configuration;

public class Cache {
	private final static Cache instance = new Cache();
	private static TreeCache tree = new TreeCache();
	private HashMap<String, Long> sizeMap = new HashMap<String, Long>();
	private HashMap<String, Integer> contentTypes = new HashMap<String, Integer>();

	public static Cache getInstance() {
		return instance;
	}

	public Set<String> getContentTypesInCache() {
		return contentTypes.keySet();
	}

	public Long getSizeCacheOfContentType(String contentType) {
		return sizeMap.get(contentType);
	}
	
	public void emptyCache() {
		for (String key : contentTypes.keySet()) {
			this.emptyCacheForContentType(key);
		}
	}

	public void emptyCacheForContentType(String contentType) {
		File directory = new File(contentType);
		String[] files = directory.list();
		if(files != null){
			for(int i = 0; i < files.length; i++){
				String fileName = files[i];
				try {
					Files.deleteIfExists(Paths.get(contentType+"/"+fileName));
				} catch (IOException e) {}
			}
		}
		sizeMap.put(contentType, (long)0);
	}

	public void addContentToCache(ByteBuffer buffer, String url, String contentType, boolean append) throws IOException {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			System.err.println("Cannot generate SHA-1 for url " + url);
		}
		if (contentType == null)
			contentType = "misc";
		else
			contentType = contentType.split(";")[0];
		contentTypes.put(contentType, 1);
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

	public boolean isUptodate(String url, String contentType){
		return true;
	}

	public ReadableByteChannel isInCache(String url, String contentType, boolean shared) throws FileNotFoundException {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			System.err.println("Cannot generate SHA-1 for url " + url);
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
		//TODO IsUpToDate
		if(file.exists() && !file.isDirectory()){
			FileInputStream input = new FileInputStream(file);
			url = url.replace("://", "_");
			tree.addPath(url);
			return input.getChannel();
		}
		if(shared == true){
			CacheShared cs = CacheShared.getInstance();
			return cs.sendCacheRequest(contentType+";"+url, Calendar.getInstance().getTimeInMillis());
		}
		return null;
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
		cache.addContentToCache(buffer, "http://free.fr/index.html", "text/html", false);
		cache.addContentToCache(buffer, "http://www.facebook.com/index.html", "text/html", false);
		cache.addContentToCache(buffer, "http://www.google.com/index.html", "text/html", true);
		ReadableByteChannel rb = cache.isInCache("http://www.google.com/index.html", "text/html", false);
		System.out.println(rb);
		rb.close();
	}
}
