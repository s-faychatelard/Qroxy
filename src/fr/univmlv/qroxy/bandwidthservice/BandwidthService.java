package fr.univmlv.qroxy.bandwidthservice;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

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
		public double minBandwidth;
		public long startTime;
		public DownloadBandwidth(String contentType, int minBandwidth, int maxBandwidth){
			this.currentBandwidth = minBandwidth;
			this.maxBandwidth = maxBandwidth;
			this.minBandwidth = minBandwidth;
			this.startTime = new Date().getTime();
			this.contentType = contentType;
		}
	}
	
	/**
	 * This attribute represent the theorical bandwidth
	 */
	private double theoricalBandwidth=0;
	/**
	 * Represent the instance of BandwidthService
	 */
	private final static BandwidthService instance = new BandwidthService();
	/**
	 * Contain all current downloads represent by a DownloadBandwidth
	 */
	private final static ConcurrentHashMap<String, DownloadBandwidth> downloads = new ConcurrentHashMap<String, DownloadBandwidth>();
	
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
		downloads.put(url, new DownloadBandwidth(contentType, Configuration.getInstance().getConfForType(contentType).getDebitmin(), Configuration.getInstance().getConfForType(contentType).getDebitmax()));
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
		//System.out.println("PB : " + previousBandwidth + " NB : " + downloads.get(url).currentBandwidth);
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
		double diffBandwidth = ((double)contentSize/(double)duration) - theoricalBandwidth;

		/* Do not recalculate if it is the first loop, all operations will be false */
		if (theoricalBandwidth == 0) {
			theoricalBandwidth = ((double)contentSize/(double)duration);
			return;
		}
		
		for (DownloadBandwidth db : downloads.values()) {
			if (db.minBandwidth != -1 && db.currentBandwidth < db.minBandwidth)
				db.currentBandwidth = db.minBandwidth;
			diffBandwidth = diffBandwidth - (db.minBandwidth - db.currentBandwidth);
		}

		/* Calculate global weight of current downloads */
		int globalWeight=0;
		for (DownloadBandwidth db : downloads.values()) {
			if (db.currentBandwidth >= db.maxBandwidth) continue;
			globalWeight += Configuration.getInstance().getConfForType(db.contentType).getWeight();
		}

		/* Adjust bandwidth for specific download */
		for (DownloadBandwidth db : downloads.values()) {
			if (db.currentBandwidth >= db.maxBandwidth) continue;
			db.currentBandwidth += diffBandwidth * Configuration.getInstance().getConfForType(db.contentType).getWeight()/globalWeight;
		}

		/* Save the theorical bandwidth for further use */
		theoricalBandwidth = ((double)contentSize/(double)duration);
		//System.out.println("Theorical bandwith : " + theoricalBandwidth + " ko/s");
		//System.out.println("Theorical diff of bandwith : " + diffBandwidth + " ko/s");
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
		System.out.println("Need wait " + bandwidthService.getTimeToWaitForURLAndType("http://www.google.fr", 1000) + "ms");
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("Need wait " + bandwidthService.getTimeToWaitForURLAndType("http://www.google.fr", 1000) + "ms");
		bandwidthService.deleteDownloadWithURLAndType("http://www.google.fr");
	}
}
