package fr.univmlv.qroxy.cache;

public class Cache {

	private final static Cache instance = new Cache();
	
	public static Cache getInstance() {
		return instance;
	}
	
	public void addContentToCache(Object content, String contentType) {
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
