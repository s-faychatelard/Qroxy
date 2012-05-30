package fr.univmlv.qroxy.download;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.util.HashMap;
import java.util.Map;

import fr.univmlv.qroxy.bandwidthservice.BandwidthService;
import fr.univmlv.qroxy.cache.Cache;

public class Download implements Runnable {

	private final static int BUFFER_SIZE = 262144;
	private final Pipe.SinkChannel channel;
	private HttpURLConnection urlConnection;
	private final URL url;
	private final String requestType;
	private final Map<String, String> properties;

	public Download(Pipe.SinkChannel channel, URL url, String requestType, Map<String, String> properties) {
		this.channel = channel;
		this.url = url;
		this.requestType = requestType;
		this.properties = properties;
	}

	/**
	 * This method manage all content download from cache or server directly.
	 * It will get information from HttpURLConnection and ask to the cache if it has the content. 
	 * 
	 * If it is in the cache, we pass the pipe to communicate with the main thread and the cache will send the content directly.
	 * If it is NOT in the cache, we download the file from the server and send it to the cache (only if it is not private) and to the client. 
	 */
	@Override
	public void run() {
		try {
			
			/* Send the header response to the Client */
			HttpURLConnection.setFollowRedirects(true);
			urlConnection = (HttpURLConnection)url.openConnection();
			urlConnection.setRequestMethod(requestType);
			for (String key : properties.keySet()) {
				if(key.compareTo("Accept-Encoding") == 0)
					continue;
				urlConnection.setRequestProperty(key, properties.get(key));
				System.out.println(key + " " + properties.get(key));
			}
			
			StringBuilder sb = new StringBuilder(urlConnection.getHeaderField(0));
			int nbFields = urlConnection.getHeaderFields().size();
			for(int i=1; i<nbFields; i++) {
				sb.append(urlConnection.getHeaderFieldKey(i)).append("=");
				sb.append(urlConnection.getHeaderField(i)).append("\r\n");
			}
			sb.append("\r\n");
			channel.write(ByteBuffer.wrap(sb.toString().getBytes()));
			
			/* Get informations */
			// TODO treat response code
			if (urlConnection.getResponseCode() != 200) {
				sb = new StringBuilder("HTTP/1.1 ");
				sb.append(urlConnection.getResponseCode()).append(" ");
				sb.append(urlConnection.getResponseMessage()).append("\r\n");
				ByteBuffer bb = ByteBuffer.wrap(sb.toString().getBytes());
				channel.write(bb);
				System.out.println("HTTP error " + sb.toString());
				return;
			}

			/* Format url informations and send it to the client */
			String file = urlConnection.getURL().getFile();
			if (file == "" || file.compareTo("/") == 0)
				file = "/index.html";
			String urlPath = urlConnection.getURL().getProtocol() + "://" + urlConnection.getURL().getHost() + file;
			String contentType = urlConnection.getContentType();

			/* Prepare cache */
			Cache cache = Cache.getInstance();

			/* Check if the content is not already in the cache */
			if (cache.isInCache(urlPath, urlConnection.getContentType())) {
				// Get from cache
				cache.getFromCache(urlPath, urlConnection.getContentType(), this.channel);
				return;
			}

			/* Cache privacy information and ask to the cache to freeing space for the content */
			boolean caching = false;
			String cacheControl = (urlConnection.getHeaderField("Cache-Control") == null) ? "" : urlConnection.getHeaderField("Cache-Control");
			/* Check if you need to cache the file */
			if (cacheControl.contains("private") == false) {
				if (urlConnection.getContentLength() != -1) {
					if (cache.freeSpace(urlConnection.getContentLength())) {
						//System.out.println("Space clear for caching");
						caching = true;
					}
					else {
						// TODO we must inform the Logger that there are not enough space for this file
						//System.out.println("No enough space for caching");
					}
				}
				else {
					//System.out.println("We don't know the real size, downloading before caching");
					// TODO Content-Length equal to -1 need to found a predetermine size to free
					if (cache.freeSpace(100000)) {
						//System.out.println("Predetermine space clear for caching");
						caching = true;
					}
					else {
						//System.out.println("No enough space for caching");
					}
				}
			}
			else {
				// Do nothing
				//System.out.println("Cache-control is private");
			}

			/* Get content from url and send it to the cache and client */
			DataInputStream dis = new DataInputStream(urlConnection.getInputStream());
			byte[] buffer = new byte[BUFFER_SIZE];
			int readbyte = 0;

			if (caching)
				cache.addContentToCache(ByteBuffer.wrap("".getBytes()), urlPath, contentType, false);

			/* Declare download to BandwidthService */
			BandwidthService bandwidthService = BandwidthService.getInstance();
			bandwidthService.addADownloadWithURLAndType(urlPath, urlConnection.getContentType());

			/* Download the content */
			while((readbyte = dis.read(buffer, 0, BUFFER_SIZE)) != -1 && readbyte != 0) {
				ByteBuffer bb = ByteBuffer.wrap(buffer, 0, readbyte);
				
				/* Send it to the client */
				channel.write(bb);

				/* Send it to the cache */
				if (caching)
					cache.addContentToCache(bb, urlPath, urlConnection.getContentType(), true);
				
				bb.clear();

				/* Wait define time to respect bandwidth define by the content type */
				try {
					Thread.sleep(bandwidthService.getTimeToWaitForURLAndType(urlPath, readbyte));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			urlConnection.disconnect();
			channel.close();

			/* Remove the download from the BandwidthService */
			bandwidthService.deleteDownloadWithURLAndType(urlPath);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		try {
			//http://movies.apple.com/media/us/ipad/2012/80ba527a-1a34-4f70-aae8-14f87ab76eea/apple-ipad-tour-safari-us-20120306_600x671.mp4
			//http://www.apple.fr/
			//http://www.ubuntu.com/start-download?distro=desktop&bits=32&release=lts

			/* Create a pipe to communicate with the thread */
			Pipe pipe = Pipe.open();

			/* Start the download */
			URL url = new URL("http://www.google.fr");
			new Thread(new Download(pipe.sink(), url, "GET", new HashMap<String, String>())).start();

			/* On receiving data from the pipe, you can send directly to the client */
			ByteBuffer bb = ByteBuffer.allocateDirect(BUFFER_SIZE);
			byte[] buffer = new byte[BUFFER_SIZE];
			int readbyte = 0;
			while ((readbyte = pipe.source().read(bb)) != -1 || readbyte == 0) {
				bb.flip();
				bb.get(buffer, 0, readbyte);
				/*for(int i=0; i<readbyte; i++) {
					System.out.print((char)buffer[i]);
				}*/
				bb.clear();
			}
			System.out.println("");
			System.out.println("End of download");
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
