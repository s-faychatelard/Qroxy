package fr.univmlv.qroxy.configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Scanner;

public class Configuration {
	
	private HashMap<String, ConfigurationType> confMap = new HashMap<String, ConfigurationType>();
	private final static Configuration instance = new Configuration();
	private String path;
	private final String startbalise = "[TYPE]";
	private final String stopbalise = "[/TYPE]";
	private final char comment = '#';
	
	private Configuration() {
	}
	
	public void prepareConfigurationWithPath(String path) throws IOException {
		this.path = path;
		String type = null;
		int max = -1;
		int min = -1;
		int weight = 1;
		ConfigurationType conftype;
		File conf = new File(path);
		FileInputStream fin= new FileInputStream(conf);
		Scanner sc = new Scanner(fin.getChannel());
		while(sc.hasNextLine()){
			String confString = sc.nextLine();
			if(confString.length() == 0){
				continue;
			}
			if(confString.charAt(0) == comment){
				continue;
			}
			if(confString.equals(startbalise)){
				while (sc.hasNextLine()) {
					confString = sc.nextLine();
					if(confString.equals(stopbalise)){
						if(type == null){
							System.err.println("Fichier de configuration non valide");
							return;
						}
						conftype = new ConfigurationType(max, min, weight);
						confMap.put(type, conftype);
						break;
					}
					String[] confLine = confString.split("=");
					if(confLine[0].equalsIgnoreCase("type")){
						type = confString.substring(confString.indexOf("=")+1, confString.length());
					}
					if(confLine[0].equalsIgnoreCase("max")){
						max = Integer.valueOf(confLine[1]);
					}
					if(confLine[0].equalsIgnoreCase("min")){
						min = Integer.valueOf(confLine[1]);
					}
					if(confLine[0].equalsIgnoreCase("weight")){
						weight = Integer.valueOf(confLine[1]);
					}
					
				}
			}
		}
		fin.close();
	}
	
	public static Configuration getInstance() {
		return instance;
	}
	
	public ConfigurationType getConfForType(String contentType) {
		ConfigurationType type = Configuration.getInstance().confMap.get(contentType);
		return type;
	}


	/**
	 * Must contain directory path for cache
	 * Information about priority of type file
	 * @throws IOException 
	 *  
	 */

	public static void main(String[] args) throws IOException {
		Configuration.getInstance().prepareConfigurationWithPath("/home/joachim/cache.conf");
		System.out.println(Configuration.getInstance().getConfForType("text/html"));
		
	}
}
