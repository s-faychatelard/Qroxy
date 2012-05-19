package fr.univmlv.qroxy.cache;

import java.nio.ByteBuffer;

public class Cache {

	private final static Cache instance = new Cache();
	
	public static Cache getInstance() {
		return instance;
	}
	
	public void addContentToCache(ByteBuffer buffer, String filename, String contentType, boolean append) {
		//Test de commit joachim
		//TODO create a reference to the file in cache
	}
	
	public boolean isInCache(String filename) {
		//TODO if Configuration.isCacheShared CacheShared.search(filename)
		return true;
	}
	
	public boolean freeSpace(int neededSpace) {
		//TODO try to remove older file or none used file
		return true;
	}
	
	public static void main(String[] args) {
		
	}
}
