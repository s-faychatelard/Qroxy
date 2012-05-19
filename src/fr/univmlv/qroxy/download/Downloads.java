package fr.univmlv.qroxy.download;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import fr.univmlv.qroxy.cache.Cache;

public class Downloads {
	
	private final static int BUFFER_SIZE = 1024;

	public static void downloadContentAtURL(URL url) {
		try {
			/* Get informations */
			URLConnection connection = url.openConnection();
			connection.connect();
			System.out.println("Filename : " + url.getFile());
			System.out.println("Content-Type : " + connection.getContentType());
			System.out.println("Content-Length : " + connection.getContentLength());
			System.out.println("Cache-Control : " + connection.getHeaderField("Cache-Control"));
			
			/* Prepare cache */
			// TODO can be equal to -1 do not cache during download
			Cache cache = Cache.getInstance();
			if (connection.getContentLength() != -1 && connection.getHeaderField("Cache-Control").compareToIgnoreCase("private") != 0) {
				if (cache.freeSpace(connection.getContentLength())) {
					System.out.println("Space clear for caching");
				}
				else {
					System.out.println("No enough space for caching");
				}
			}
			else {
				System.out.println("We don't know the real size, downloading before caching");
			}
			
			/* Get content */
			DataInputStream dis = new DataInputStream(connection.getInputStream());
			byte[] buffer = new byte[BUFFER_SIZE];
			int readbyte = 0;
			while((readbyte = dis.read(buffer, 0, BUFFER_SIZE)) != -1) {
				System.out.println("Read byte : " + readbyte);
				for(int i=0; i<readbyte; i++) {
					System.out.print((char)buffer[i]);
				}
			}
			System.out.println("");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		try {
			//Downloads.downloadContentAtURL(new URL("http://movies.apple.com/media/us/ipad/2012/80ba527a-1a34-4f70-aae8-14f87ab76eea/apple-ipad-tour-safari-us-20120306_600x671.mp4"));
			Downloads.downloadContentAtURL(new URL("http://www.apple.fr/"));			
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}
}
