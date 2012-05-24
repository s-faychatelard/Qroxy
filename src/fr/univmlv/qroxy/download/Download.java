package fr.univmlv.qroxy.download;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;

import fr.univmlv.qroxy.bandwidthservice.BandwidthService;
import fr.univmlv.qroxy.cache.Cache;

public class Download implements Runnable {

	private final static int BUFFER_SIZE = 1024;
	private final Pipe.SinkChannel channel;
	private final URL url;
	private final String requestMethod;

	public Download(Pipe.SinkChannel channel, URL url, String requestMethod) {
		this.channel = channel;
		this.url = url;
		this.requestMethod = requestMethod;
	}

	@Override
	public void run() {
		HttpURLConnection connection = null;
		try {
			/* Get informations */
			HttpURLConnection.setFollowRedirects(true);
			connection = (HttpURLConnection)url.openConnection();
			connection.setRequestMethod(this.requestMethod);

			// TODO check response code
			if (connection.getResponseCode() != 200) {
				StringBuilder sb = new StringBuilder("HTTP/1.0 ");
				sb.append(connection.getResponseCode()).append(" ");
				sb.append(connection.getResponseMessage()).append("\n");
				ByteBuffer bb = ByteBuffer.wrap(sb.toString().getBytes());
				channel.write(bb);
				System.out.println("HTTP error");
				return;
			}

			/* Format url informations and send it to the client */
			String file = connection.getURL().getFile();
			if (file == "" || file.compareTo("/") == 0)
				file = "/index.html";
			String urlPath = connection.getURL().getProtocol() + "://" + connection.getURL().getHost() + file;
			String contentType = connection.getContentType();

			/* Prepare cache */
			Cache cache = Cache.getInstance();

			/* Check if the content is not already in the cache */
			if (cache.isInCache(urlPath, connection.getContentType())) {
				// Get from cache
				//System.out.println("Content is in cache");
				//return;
			}

			boolean caching = false;
			String cacheControl = (connection.getHeaderField("Cache-Control") == null) ? "" : connection.getHeaderField("Cache-Control");
			/* Check if you need to cache the file */
			if (cacheControl.contains("private") == false) {
				if (connection.getContentLength() != -1) {
					if (cache.freeSpace(connection.getContentLength())) {
						//System.out.println("Space clear for caching");
						caching = true;
					}
					else {
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
				System.out.println("Cache-control is private");
			}

			/* Get content from url and send it to the cache and client */
			DataInputStream dis = new DataInputStream(connection.getInputStream());
			byte[] buffer = new byte[BUFFER_SIZE];
			int readbyte = 0;

			if (caching)
				cache.addContentToCache(ByteBuffer.wrap("".getBytes()), urlPath, contentType, false);

			/* Declare download to BandwidthService */
			//BandwidthService bandwidthService = BandwidthService.getInstance();
			//bandwidthService.addADownloadWithURLAndType(urlPath, connection.getContentType());

			/* Download the content */
			while((readbyte = dis.read(buffer, 0, BUFFER_SIZE)) != -1 && readbyte != 0) {
				ByteBuffer bb = ByteBuffer.wrap(buffer, 0, readbyte);
				while(bb.hasRemaining()) {								
					/* Send it to the client */
					channel.write(bb);

					/* Send it to the cache */
					if (caching)
						cache.addContentToCache(bb, urlPath, connection.getContentType(), true);

					bb.remaining();
				}
				bb.clear();
				buffer = new byte[BUFFER_SIZE];

				/* Wait define time to respect bandwidth define by the content type */
				/*try {
					Thread.sleep(bandwidthService.getTimeToWaitForURLAndType(urlPath, readbyte));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}*/
			}
			connection.disconnect();
			channel.close();

			/* Remove the download from the BandwidthService */
			//bandwidthService.deleteDownloadWithURLAndType(urlPath);
		} catch (IOException e) {
			System.out.println("la");
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
			new Thread(new Download(pipe.sink(), new URL("http://google.fr/search?q=Robert+Moog&oi=ddle&ct=moog12-hp"), "GET")).start();

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
