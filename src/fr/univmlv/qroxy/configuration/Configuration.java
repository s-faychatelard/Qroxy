package fr.univmlv.qroxy.configuration;

public class Configuration {
	
	private final static Configuration instance = new Configuration();
	private String path;
	
	private Configuration() {
		//Read conf from file
	}
	
	public void prepareConfigurationWithPath(String path) {
		this.path = path;
	}
	
	public static Configuration getInstance() {
		return instance;
	}
	
	public int getWeightForType(String contentType) {
		return 1;
	}
	
	/**
	 * Must contain directory path for cache
	 * Information about priority of type file
	 *  
	 */

	public static void main(String[] args) {

	}
}
