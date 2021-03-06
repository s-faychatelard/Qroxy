package fr.univmlv.qroxy;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.Pipe.SourceChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;

import fr.univmlv.qroxy.cache.Cache;
import fr.univmlv.qroxy.cache.shared.CacheShared;
import fr.univmlv.qroxy.configuration.Configuration;
import fr.univmlv.qroxy.download.Download;

public class Qroxy implements Runnable {

	private ServerSocketChannel channel;
	private final static int BUFFER_SIZE = 262144;
	private final static String usage = "Usage : \r\n\tqroxy.jar -c cache.conf IP:PORT\r\n";

	//TODO add limit of number of download thread

	/**
	 * Private class for managing a buffer for each specific client
	 */
	private static class Client {
		public ByteBuffer out = ByteBuffer.allocate(BUFFER_SIZE);
		public SocketChannel channel;
		public Download download;

		public Client(SocketChannel channel, Download download) {
			this.channel = channel;
			this.download = download;
		}
	}

	/**
	 * Create the Qroxy server and launch it
	 * 
	 * @param remotePort the listening port of the Qroxy
	 */
	public Qroxy(String ip, int remotePort)  {
		try {
			channel = ServerSocketChannel.open();
			channel.configureBlocking(false);
			channel.bind(new InetSocketAddress(ip, remotePort));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Manage communication with clients
	 */
	@Override
	public void run() {
		try {
			/* We create correspondence between a pipe and a socket client in both way */
			HashMap<SourceChannel, Client> map = new HashMap<SourceChannel, Client>();
			HashMap<SocketChannel, Client> mapClient = new HashMap<SocketChannel, Client>();

			Selector selector;
			selector = Selector.open();
			channel.register(selector, SelectionKey.OP_ACCEPT);
			while (true) {
				selector.select();
				Iterator<SelectionKey> it = selector.selectedKeys().iterator();
				while (it.hasNext()) {
					SelectionKey selKey = (SelectionKey)it.next();
					it.remove();

					/* Client connection */
					if (selKey.isValid() && selKey.isAcceptable()) {
						ServerSocketChannel sChannel = (ServerSocketChannel)selKey.channel();
						SocketChannel clientChannel = sChannel.accept();
						clientChannel.configureBlocking(false);
						clientChannel.register(selector, SelectionKey.OP_READ);
					}

					/* Client receive */
					if (selKey.isValid() && selKey.isReadable()) {

						/* It's a client request */
						if (!(selKey.channel() instanceof SourceChannel)) {
							SocketChannel clientChannel = (SocketChannel)selKey.channel();							
							Scanner scanner = new Scanner(clientChannel);

							if (scanner.hasNextLine()) {
								String line = scanner.nextLine();
								String[] request = line.split(" ");
								
								/* Get request attributes */
								Map<String, String> properties = new HashMap<String, String>();
								while(scanner.hasNextLine()) {
									line = scanner.nextLine();
									if (line.length() == 0) {
										break;
									}
									int index = line.indexOf(':');
									String key = line.substring(0, index);
									if(line.charAt(index+1) == ' ')
										index++;
									String value = line.substring(index+1, line.length());
									properties.put(key, value);
								}
								// TODO get POST but there is no data...
								/*if (properties.get("Content-Length") != null) {
									ByteBuffer bb = ByteBuffer.allocate(Integer.valueOf(properties.get("Content-Length")));
									System.out.println(clientChannel.read(bb));
									bb.flip();
									System.out.println(bb.limit() + " " + bb.capacity());
								}*/
								
								/* URL of the request */
								URL url = new URL(request[1]);
								if (url.toString().contains("http://qroxy")) {
									this.sendCacheInformation(url.toString(), clientChannel);
								}
								
								/* Create a pipe to communicate with the thread */
								Pipe pipe = Pipe.open();
								pipe.source().configureBlocking(false);
								pipe.source().register(selector, SelectionKey.OP_READ);

								/* Save the clientChannel for a specific pipe */
								Client client = new Client(clientChannel, new Download(pipe.sink(), url, request[0], properties));
								map.put(pipe.source(), client);
								mapClient.put(clientChannel, client);

								/* Start the download */
								new Thread(client.download).start();
							}
						}

						/* It's a pipe receive */
						else if (selKey.channel() instanceof SourceChannel) {
							SourceChannel pipeChannel = (SourceChannel)selKey.channel();

							/* Get the clientChannel from its pipe */
							Client client = map.get(pipeChannel);

							/* Get the data from the pipe */
							if (pipeChannel.read(client.out) == -1) {
								client.out.flip();
								if (client.out.limit() != 0) {
									try {
										client.channel.write(client.out);
									} catch (IOException e) {
										client.channel.close();
									}
								}
								if (pipeChannel.isOpen())
									pipeChannel.close();
								selKey.cancel();
								client.download.interrupt();
								mapClient.remove(map.get(pipeChannel));
								map.remove(pipeChannel);
								if (!client.download.getKeepAlive()) {
									client.channel.close();
								}
								continue;
							}

							/* Register is available for writing*/
							if (client.channel.isConnected() && client.channel.isOpen())
								client.channel.register(selector, SelectionKey.OP_WRITE);
						}
					}

					/* Client send */
					if (selKey.isValid() && selKey.isWritable()) {
						/* We send data to the client */
						Client client = mapClient.get(selKey.channel());
						client.out.flip();
						if (!client.channel.isConnected() || !client.channel.isOpen()) {
							client.download.interrupt();
							client.channel.close();
							continue;
						}
						try {
							client.channel.write(client.out);
						} catch (IOException e) {
							client.channel.close();
							selKey.cancel();
							client.download.interrupt();
							map.remove(mapClient.get(client.channel));
							mapClient.remove(client.channel);
							continue;
						}
						client.out.compact();
						selKey.interestOps(SelectionKey.OP_READ);
					}
				}
			}
		}catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Qroxy cache browser request
	 * 
	 * @param url
	 * @param client
	 */
	public void sendCacheInformation(String url, SocketChannel client) {
		Cache cache = Cache.getInstance();
		/* Remove just a content type cache */
		if (url.contains("/remove/")) {
			int index = url.indexOf("/remove/") + "/remove/".length();
			String contentType = url.substring(index);
			cache.emptyCacheForContentType(contentType);
		}
		/* Remove all cache */
		if (url.contains("/empty/")) {
			cache.emptyCache();
		}
		StringBuilder sb = new StringBuilder("<html><head></head><body><div><h1>Cache information</h1><h2>Taille des caches en cours</h2><table><thead><tr><th>Cache type</th><th>Taille courante (octets)</th><th>Taille globale (octets)</th><th>Vider le cache</th></tr></thead><tbody>");
		for(String key : cache.getContentTypesInCache()) {
			sb.append("<tr><td>").append(key).append("</td><td>").append(cache.getSizeCacheOfContentType(key)).append("</td><td>").append(Configuration.getInstance().getConfForType(key).getSize()).append("</td><td><a href='http://qroxy/remove/").append(key).append("'>Vider le cache</a></td></tr>");
		}
		sb.append("</tbody></table><div><div><p><a href='http://qroxy/empty/'>Vider le cache</a></p></div></div></div></body></html>");
		ByteBuffer bb = ByteBuffer.allocate(1024+sb.length());
		
		bb.put("HTTP/1.1 200 OK\r\n".getBytes());
		bb.put("Content-Type: text/html; charset=UTF-8\r\n".getBytes());
		bb.put(("Content-Length: "+sb.length()+"\r\n\r\n").getBytes());
		bb.put(sb.toString().getBytes());
		bb.flip();
		try {
			client.write(bb);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		if (args.length != 3) {
			System.out.println(usage);
			return;
		}
		String pathToConf=null;
		String ip=null;
		int port=-1;
		if (args[0].equals("-c")) {
			pathToConf = args[1];
			if (!args[2].contains(":")) {
				System.out.println(usage);
				return;
			}
			ip = args[2].split(":")[0];
			port = Integer.valueOf(args[2].split(":")[1]);
		}
		else {
			if (!args[0].contains(":")) {
				System.out.println(usage);
				return;
			}
			ip = args[0].split(":")[0];
			port = Integer.valueOf(args[2].split(":")[1]);
			if (!args[1].equals("-c")) {
				System.out.println(usage);
				return;
			}
			pathToConf = args[2];
		}
		try {
			/* Redirect error to a file */
			System.setErr(new PrintStream(new FileOutputStream("qroxy.log")));
			System.err.println("Qroxy started");
			/* Load configuration from file */
			Configuration.getInstance().prepareConfigurationWithPath(pathToConf);
			System.err.println("Cache configuration loaded");
		} catch (IOException e) {
			e.printStackTrace();
		}
		/* Start qroxy */
		System.out.println("To see informations about your cache use http://qroxy in your browser");
		System.out.println("Type 'exit' to quit");
		CacheShared.startService();
		new Thread(new Qroxy(ip, port)).start();
		Scanner scanner = new Scanner(System.in);
		while (scanner.hasNext()) {
			if (scanner.next().equalsIgnoreCase("exit")) {
				Cache.getInstance().emptyCache();
				System.exit(0);
			}
		}
	}
}
