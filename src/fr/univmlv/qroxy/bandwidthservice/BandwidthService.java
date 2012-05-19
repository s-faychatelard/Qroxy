package fr.univmlv.qroxy.bandwidthservice;

import java.util.Date;
import java.util.HashMap;

import fr.univmlv.qroxy.configuration.Configuration;

public class BandwidthService {
	
	/**
	 * Download object
	 * Represent a download in the BandwidthService
	 */
	private class DownloadBandwidth {
		public String contentType;
		public double currentBandwidth;
		public double maxBandwidth;
		public long startTime;
		public DownloadBandwidth(String contentType, int maxBandwidth){
			this.currentBandwidth = maxBandwidth;
			this.maxBandwidth = maxBandwidth;
			this.startTime = new Date().getTime();
			this.contentType = contentType;
		}
	}
	
	/**
	 * This attribute represent the theorical bandwidth
	 */
	private double theoricalBandwith=0;
	/**
	 * Represent the instance of BandwidthService
	 */
	private final static BandwidthService instance = new BandwidthService();
	/**
	 * Contain all current downloads represent by a DownloadBandwidth
	 */
	private final static HashMap<String, DownloadBandwidth> downloads = new HashMap<String, DownloadBandwidth>();
	
	/**
	 * Get the instance of the BandwidthService
	 */
	public static BandwidthService getInstance() {
		return instance;
	}
	
	/**
	 * Add a new download to the BandwidthService
	 * 
	 * @param url of download
	 * @param contentType of the download
	 */
	public void addADownloadWithURLAndType(String url, String contentType) {
		downloads.put(url, new DownloadBandwidth(contentType, 2));
	}
	
	/**
	 * Remove a download from the BandwidthService
	 *
	 * @param url of download
	 */
	public void deleteDownloadWithURLAndType(String url) {
		downloads.remove(url);
	}
	
	/**
	 * Return the time in milliseconds that the current Download must wait to respect its bandwidth
	 * 
	 * @param url of download
	 * @param contentSize contentSize is the number of Byte read since the last call
	 * @return the time that the Download must wait, in millisecond
	 */
	public int getTimeToWaitForURLAndType(String url, int contentSize) {
		double previousBandwidth = downloads.get(url).currentBandwidth;
		long endTime = new Date().getTime();
		long duration = endTime - downloads.get(url).startTime;
		this.recalculateBandwidth(url, contentSize);
		System.out.println("PB : " + previousBandwidth + " NB : " + downloads.get(url).currentBandwidth);
		int timeToWait = (int)(((double)contentSize/(double)previousBandwidth) - duration);
		return (timeToWait < 0) ? 0 :  timeToWait;
	}
	
	/**
	 * Recalculate the bandwidth for all download
	 * Calibrate the difference of bandwidth
	 * 
	 * @param url of download
	 * @param contentSize is the number of Byte read since the last call
	 */
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
			if (db.currentBandwidth >= db.maxBandwidth) continue;
			globalWeight += Configuration.getInstance().getWeightForType(db.contentType);
		}

		/* Adjust bandwidth for specific download */
		for (DownloadBandwidth db : downloads.values()) {
			if (db.currentBandwidth >= db.maxBandwidth) continue;
			db.currentBandwidth += diffBandwidth * Configuration.getInstance().getWeightForType(db.contentType)/globalWeight;
		}

		/* Save the theorical bandwidth for further use */
		theoricalBandwith = ((double)contentSize/(double)duration);
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
