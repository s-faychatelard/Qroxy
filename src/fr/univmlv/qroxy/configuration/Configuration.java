package fr.univmlv.qroxy.configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Scanner;

public class Configuration {
	
	private final HashMap<String, ConfigurationType> confMap = new HashMap<String, ConfigurationType>();
	private final static Configuration instance = new Configuration();
	private final static String startbalise = "[TYPE]";
	private final static String stopbalise = "[/TYPE]";
	private final static char comment = '#';
	private static boolean isShared=false;
	
	public static Configuration getInstance() {
		return instance;
	}
	
	public boolean isShared() {
		return isShared;
	}
	
	public void prepareConfigurationWithPath(String pathToConf) throws IOException {
		//TODO ADD Maximum size of cache
		String path = pathToConf;
		String type = null;
		int max = -1;
		int min = -1;
		int weight = 1;
		long size = -1;
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
			if(confString.contains("shared")){
				String[] confLine = confString.split("=");
				isShared = Boolean.valueOf(confLine[1]);
			}
			if(confString.equals(startbalise)){
				while (sc.hasNextLine()) {
					confString = sc.nextLine();
					if(confString.equals(stopbalise)){
						if(type == null){
							System.err.println("Fichier de configuration non valide type manquant");
							return;
						}
						if(size == -1){
							System.err.println("Fichier de configuration non valide size manquant");
							return;
						}
						conftype = new ConfigurationType(max, min, weight, size);
						confMap.put(type, conftype);
						break;
					}
					String[] confLine = confString.split("=");
					if(confLine[0].equalsIgnoreCase("type")){
						type = confString.substring(confString.indexOf("=")+1, confString.length());
					}
					if(confLine[0].equalsIgnoreCase("size")){
						size =	Long.valueOf(confLine[1]);
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
	
	public ConfigurationType getConfForType(String contentType) {
		ConfigurationType type = Configuration.getInstance().confMap.get(contentType);
		if (type == null)
			type = new ConfigurationType(-1, -1, 1, 50000000);
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
