package fr.univmlv.qroxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.UnknownHostException;
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

	private final ServerSocketChannel channel;

	private final static int BUFFER_SIZE = 1024;

	static class Buffers {
		static final ByteBuffer in = ByteBuffer.allocate(BUFFER_SIZE);
		static final ByteBuffer out = ByteBuffer.allocate(BUFFER_SIZE);
	}

	public Qroxy(int remotePort) throws UnknownHostException, IOException {
		channel = ServerSocketChannel.open();
		channel.configureBlocking(false);
		channel.bind(new InetSocketAddress(remotePort));
	}

	public void launch() throws IOException, InterruptedException {
		HashMap<SourceChannel, SocketChannel> map = new HashMap<SourceChannel, SocketChannel>();
		Selector selector = Selector.open();
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
						Buffers.in.clear();
						clientChannel.read(Buffers.in);

						String request = new String(Buffers.in.array());
						System.out.println(request);
						Scanner scanner = new Scanner(request);
						if (scanner.hasNext()) {
							if (scanner.next().compareToIgnoreCase("GET") == 0) {
								/* Create a pipe to communicate with the thread */
								Pipe pipe = Pipe.open();
								pipe.source().configureBlocking(false);
								pipe.source().register(selector, SelectionKey.OP_READ);

								/* Save the clientChannel for a specific pipe */
								map.put(pipe.source(), clientChannel);

								/* Start the download */
								URL url = new URL(scanner.next());
								new Thread(new Download(pipe.sink(), url)).start();
							}
						}
					}
					/* It's a pipe receive */
					else if (selKey.channel() instanceof SourceChannel) {
						SourceChannel pipeChannel = (SourceChannel)selKey.channel();

						/* Get the clientChannel from its pipe */
						SocketChannel clientChannel = map.get(pipeChannel);

						/* Get the data from the pipe */
						if (pipeChannel.read(Buffers.out) == -1) {
							pipeChannel.close();
						}

						/* Register has available for writing*/
						clientChannel.register(selector, SelectionKey.OP_WRITE);
					}
				}
				if (selKey.isValid() && selKey.isWritable()) {
					SocketChannel clientChannel = (SocketChannel)selKey.channel();
					while(Buffers.out.position() != 0) {
						Buffers.out.flip();
						ByteBuffer bb = ByteBuffer.allocate(4);
						bb.putInt(Buffers.out.limit());
						clientChannel.write(bb);
						clientChannel.write(Buffers.out);
						Buffers.out.compact();
					}
					Buffers.out.clear();
					selKey.interestOps(SelectionKey.OP_READ);
				}
			}
		}
	}

	public static void main(String[] args) {
		try {
			new Qroxy(8080).launch();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
