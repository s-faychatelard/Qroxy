package fr.univmlv.qroxy.bandwidthservice;

import java.util.Date;
import java.util.HashMap;

import fr.univmlv.qroxy.configuration.Configuration;

public class BandwidthService {
	
	private class DownloadBandwidth {
		public String contentType;
		public double bandwidth;
		public long startTime;
		public DownloadBandwidth(String contentType){
			this.bandwidth = 1;
			this.startTime = new Date().getTime();
			this.contentType = contentType;
		}
	}
	
	private double theoricalBandwith=0;
	private final static BandwidthService instance = new BandwidthService();
	private final static HashMap<String, DownloadBandwidth> downloads = new HashMap<String, DownloadBandwidth>();
	
	public static BandwidthService getInstance() {
		return instance;
	}
	
	public void addADownloadWithURLAndType(String url, String contentType) {
		downloads.put(url, new DownloadBandwidth(contentType));
	}
	
	public void deleteDownloadWithURLAndType(String url) {
		downloads.remove(url);
	}
	
	public int getTimeToWaitForURLAndType(String url, int contentSize) {
		double previousBandwidth = downloads.get(url).bandwidth;
		long endTime = new Date().getTime();
		long duration = endTime - downloads.get(url).startTime;
		
		System.out.println("Duration : " + duration);
		System.out.println("Content size : " + contentSize);
		System.out.println("Bandwidth : " + previousBandwidth);
		
		this.recalculateBandwidth(url, contentSize);
		System.out.println("PB : " + previousBandwidth + " NB : " + downloads.get(url).bandwidth);
		int timeToWait = (int)(((double)contentSize/(double)previousBandwidth) - duration);
		return (timeToWait < 0) ? 0 :  timeToWait;
	}
	
	private void recalculateBandwidth(String url, int contentSize) {
		/* Calculate theorical bandwidth */
		long endTime = new Date().getTime();
		long duration = endTime - downloads.get(url).startTime;
		downloads.get(url).startTime = endTime;
		double diffBandwidth = ((double)contentSize/(double)duration) - theoricalBandwith;
		
		/* Do not recalculate if it is the first loop, all calcule will be false */
		if (theoricalBandwith == 0) {
			theoricalBandwith = ((double)contentSize/(double)duration);
			return;
		}
		
		/* Caculate global weight of current downloads */
		int globalWeight=0;
		for (DownloadBandwidth db : downloads.values()) {
			globalWeight += Configuration.getInstance().getWeightForType(db.contentType);
		}
		
		/* Adjust bandwidth for specific download */
		for (DownloadBandwidth db : downloads.values()) {
			db.bandwidth += diffBandwidth * Configuration.getInstance().getWeightForType(db.contentType)/globalWeight;
		}
		
		/* Save the theorical bandwidth for further use */
		theoricalBandwith = ((double)contentSize/(double)duration);
		System.out.println("Download time : " + duration + "ms");
		System.out.println("Theorical bandwith : " + theoricalBandwith + " ko/s");
		System.out.println("Theorical diff of bandwith : " + diffBandwidth + " ko/s");
	}
	
	public static void main(String[] args) {
		BandwidthService bandwidthService = BandwidthService.getInstance();
		bandwidthService.addADownloadWithURLAndType("http://www.google.fr", "text/html");
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("Need wait " + bandwidthService.getTimeToWaitForURLAndType("http://www.google.fr", 1000) + "ms");
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("Need wait " + bandwidthService.getTimeToWaitForURLAndType("http://www.google.fr", 2000) + "ms");
		bandwidthService.deleteDownloadWithURLAndType("http://www.google.fr");
	}
}
