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
	private volatile boolean keepAlive;
	private volatile boolean stop=false;

	public Download(Pipe.SinkChannel channel, URL url, String requestType, Map<String, String> properties) {
		this.channel = channel;
		this.url = url;
		this.requestType = requestType;
		this.properties = properties;
		this.keepAlive = false;
	}
	
	public void interrupt() {
		stop=true;
	}
	
	public boolean getKeepAlive() {
		return this.keepAlive;
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
				if (key.equals("Proxy-Connection")) continue;
				System.out.println(key + ": " + properties.get(key));
				urlConnection.setRequestProperty(key, properties.get(key));
			}
			System.out.println("\r\n\r\n");
			
			/* Send HTTP response to the client */
			//TODO manage chunked data
			StringBuilder sb = new StringBuilder(urlConnection.getHeaderField(0)).append("\r\n");
			int nbFields = urlConnection.getHeaderFields().size();
			for(int i=1; i<nbFields; i++) {
				System.out.println(urlConnection.getHeaderFieldKey(i) + " " + urlConnection.getHeaderField(i));
				sb.append(urlConnection.getHeaderFieldKey(i)).append(": ");
				sb.append(urlConnection.getHeaderField(i)).append("\r\n");
			}
			sb.append("\r\n");
			channel.write(ByteBuffer.wrap(sb.toString().getBytes()));
		
			String keep = urlConnection.getHeaderField("Connection");
			if (keep != null && keep.compareTo("close") == 0)
				keepAlive = false;
			else
				keepAlive = true;
			
			/* Get informations */
			/*if (urlConnection.getResponseCode() != 200) {
				channel.close();
				return;
			}*/

			/* Format url informations and send it to the client */
			String file = "";
			if (urlConnection.getURL().getFile() == "" || urlConnection.getURL().getFile().compareTo("/") == 0)
				file = "/index.html";
			String urlPath = url.toString() + file;
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
					if (cache.freeSpace(urlConnection.getContentLength(), contentType)) {
						/* Space clear for caching */
						caching = true;
					}
					else {
						// TODO we must inform the Logger that there are not enough space for this file
						//System.out.println("No enough space for caching");
					}
				}
				else {
					/* We don't know the real size, downloading before caching */
					if (cache.freeSpace(100000, contentType)) {
						/* Predetermine space clear for caching */
						caching = true;
					}
					else {
						// TODO we must inform the Logger that there are not enough space for this file
						//System.out.println("No enough space for caching");
					}
				}
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
				
				if (stop)
					break;
				
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
