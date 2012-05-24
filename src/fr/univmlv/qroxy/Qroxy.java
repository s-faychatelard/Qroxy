package fr.univmlv.qroxy;

import java.io.IOException;
import java.net.HttpURLConnection;
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
import java.util.Scanner;

import fr.univmlv.qroxy.download.Download;

public class Qroxy {

	private ServerSocketChannel channel;

	private final static int BUFFER_SIZE = 2048;
	
	private static class Client {
		public ByteBuffer out = ByteBuffer.allocate(BUFFER_SIZE);
		public SocketChannel channel;
		
		public Client(SocketChannel channel) {
			this.channel = channel;
		}
	}

	public Qroxy(int remotePort)  {
		try {
			channel = ServerSocketChannel.open();
			channel.configureBlocking(false);
			channel.bind(new InetSocketAddress(remotePort));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void launch() {
		try {
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
					if (selKey.isValid() && selKey.isAcceptable()) {
						ServerSocketChannel sChannel = (ServerSocketChannel)selKey.channel();
						SocketChannel clientChannel = sChannel.accept();
						clientChannel.configureBlocking(false);
						clientChannel.register(selector, SelectionKey.OP_READ);
					}
					if (selKey.isValid() && selKey.isReadable()) {
						/* It's a client request */
						if (!(selKey.channel() instanceof SourceChannel)) {
							SocketChannel clientChannel = (SocketChannel)selKey.channel();
							Scanner scanner = new Scanner(clientChannel);

							if (scanner.hasNextLine()) {
								String line = scanner.nextLine();
								if (line.contains("GET") || line.contains("POST") || line.contains("HEADER")) {
									String[] request = line.split(" ");

									while (scanner.hasNextLine()) {
										String lin = scanner.nextLine();
										if (lin.length() == 0) {
											/* End of header request */
											break;
										}
									}
									
									/* Create a pipe to communicate with the thread */
									Pipe pipe = Pipe.open();
									pipe.source().configureBlocking(false);
									pipe.source().register(selector, SelectionKey.OP_READ);

									/* Save the clientChannel for a specific pipe */
									Client client = new Client(clientChannel);
									map.put(pipe.source(), client);
									mapClient.put(clientChannel, client);

									/* URL of the request */
									URL url = new URL(request[1]);
									
									/* Send the header response to the Client */
									HttpURLConnection.setFollowRedirects(true);
									HttpURLConnection connection = (HttpURLConnection)url.openConnection();
									StringBuilder sb = new StringBuilder();
									for(int i=0; i<connection.getHeaderFields().size(); i++) {
										String headerName = connection.getHeaderFieldKey(i);
										if (headerName != null) {
											if (headerName.compareToIgnoreCase("Content-Length") != 0 && 
													headerName.compareToIgnoreCase("Transfer-­Encoding") != 0) {
												sb.append(connection.getHeaderFieldKey(i));
												sb.append(":");
												sb.append(connection.getHeaderField(i));
											}
										}
										else {
											sb.append(connection.getHeaderField(i));
										}
										sb.append(" ");
									}
									sb.append("Transfer-Encoding:chunked\r\n");
									clientChannel.write(ByteBuffer.wrap(sb.toString().getBytes()));
									
									/* Start the download */
									new Thread(new Download(pipe.sink(), url, request[0])).start();
								}
								else {
									System.out.println(line);
								}
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
									ByteBuffer bb = ByteBuffer.allocate(20);
									bb.put(Integer.toBinaryString(client.out.limit()).getBytes()).put("\r\n".getBytes());
									bb.flip();
									client.channel.write(bb);
									client.out.put("\r\n".getBytes());
									client.channel.write(client.out);
								}
								pipeChannel.close();
								mapClient.remove(map.get(pipeChannel));
								map.remove(pipeChannel);
								ByteBuffer end = ByteBuffer.allocate(20);
								end.put("".getBytes()).put("\r\n".getBytes());
								end.flip();
								client.channel.write(end);
								client.channel.close();
								continue;
							}

							/* Register has available for writing*/
							client.channel.register(selector, SelectionKey.OP_WRITE);
						}
					}
					if (selKey.isValid() && selKey.isWritable()) {
						Client client = mapClient.get(selKey.channel());
						client.out.flip();
						if (client.out.limit() == 0) {
							selKey.interestOps(SelectionKey.OP_READ);
							continue;
						}
						ByteBuffer bb = ByteBuffer.allocate(20);
						bb.put(Integer.toBinaryString(client.out.limit()).getBytes()).put("\r\n".getBytes());
						bb.flip();
						client.channel.write(bb);
						client.out.put("\r\n".getBytes());
						client.channel.write(client.out);
						client.out.compact();
						selKey.interestOps(SelectionKey.OP_READ);
					}
				}
			}
		}catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		new Qroxy(8080).launch();
	}
}
