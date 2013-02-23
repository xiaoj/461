package edu.uw.cs.cse461.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.IPFinder;
import edu.uw.cs.cse461.util.Log;
import edu.uw.cs.cse461.net.base.NetLoadableInterface.NetLoadableServiceInterface;

/**
 * Transfers reasonably large amounts of data to client over raw TCP and UDP sockets.  In both cases,
 * the server simply sends as fast as it can.  The server does not implement any correctness mechanisms,
 * so, when using UDP, clients may not receive all the data sent.
 * <p>
 * Four consecutive ports are used to send fixed amounts of data of various sizes.
 * <p>
 * @author zahorjan
 *
 */

public class DataXferRawService extends DataXferServiceBase implements NetLoadableServiceInterface {
	private static final String TAG="DataXferRawService";
	private ServerSocket mServerSocket;
	private DatagramSocket mDatagramSocket;
	public static final int NPORTS = 4;
	public static final int[] XFERSIZE = {1000, 10000, 100000, 1000000};

	private int mBasePort;
	
	public DataXferRawService() throws Exception {
		super("dataxferraw");
		
		// Sanity check -- code below relies on this property
		if ( HEADER_STR.length() != RESPONSE_OKAY_STR.length() )
			throw new Exception("Header and response strings must be same length: '" + HEADER_STR + "' '" + RESPONSE_OKAY_STR + "'");	
		
		String serverIP = IPFinder.localIP();
		if ( serverIP == null ) throw new Exception("IPFinder isn't providing the local IP address.  Can't run.");
		
		// get the base port
		ConfigManager config = NetBase.theNetBase().config();
		mBasePort = config.getAsInt("dataxferraw.server.baseport", 0);
		if ( mBasePort == 0 ) throw new RuntimeException("dataxferraw service can't run -- no dataxferraw.server.baseport entry in config file");
		
		//for (int i = 0; i < NPORTS; i++){
			createUdpThread(mBasePort, serverIP);
			createTcpThread(mBasePort, serverIP);
		//}
	}
	
	public void createUdpThread(final int port, final String serverIP) throws Exception{
		final int numBytes = 1000 * (int) Math.pow(10, port - mBasePort);
		mDatagramSocket = new DatagramSocket(new InetSocketAddress(serverIP, port));
		mDatagramSocket.setSoTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 5000));

		Log.i(TAG,  "Datagram socket = " + mDatagramSocket.getLocalSocketAddress());
		Thread dgramThread = new Thread() {
			public void run() {
				// for debugging purpose
				System.out.println("UDP thread " + port);
				
				byte buf[] = new byte[1004];
				DatagramPacket packet = new DatagramPacket(buf, buf.length);

				//	Thread termination in this code is primitive.  When shutdown() is called (by the
				//	application's main thread, so asynchronously to the threads just mentioned) it
				//	closes the sockets.  This causes an exception on any thread trying to read from
				//	it, which is what provokes thread termination.
				try {
					
					while ( !mAmShutdown ) {
						try {
							mDatagramSocket.receive(packet);
							if ( packet.getLength() < HEADER_STR.length() )
								throw new Exception("Bad header: length = " + packet.getLength());
							String headerStr = new String( buf, 0, HEADER_STR.length() );
							if ( ! headerStr.equalsIgnoreCase(HEADER_STR) )
								throw new Exception("Bad header: got '" + headerStr + "', wanted '" + HEADER_STR + "'");
							System.out.println("in udp thread");
							System.arraycopy(RESPONSE_OKAY_STR.getBytes(), 0, buf, 0, HEADER_STR.length());
							String msg = "!";
							// copy the msg i times into buf until buf is full (1004 bytes)
							for (int i = 0; i < 1000; i++){
								System.arraycopy(msg.getBytes(), 0, buf, 4+i, msg.getBytes().length);
							}
							System.out.println("arraycopy done 1");
							
							if(numBytes >= buf.length){
								// send the packet i times until the total bytes sent is numBytes
								for (int i = 0; i < numBytes/1000; i++){
									mDatagramSocket.send( new DatagramPacket(buf, packet.getLength(), packet.getAddress(), packet.getPort()));
								}
							}
							System.out.println("data sent 1");
							if(numBytes % buf.length != 0){
								byte[] lastBuf = new byte[numBytes%buf.length + 4];
								DatagramPacket lastPacket = new DatagramPacket(lastBuf, lastBuf.length);
								System.arraycopy(RESPONSE_OKAY_STR.getBytes(), 0, lastBuf, 0, HEADER_STR.length());
								// copy the msg i times into lastBuf until lastBuf is full
								for (int i = 0; i < numBytes%buf.length; i++){
									System.arraycopy(msg.getBytes(), 0, lastBuf, 4+i, msg.getBytes().length);
								}
								System.out.println("arraycopy done 2");

								mDatagramSocket.send( new DatagramPacket(lastBuf, lastPacket.getLength(), lastPacket.getAddress(), lastPacket.getPort()));
							}
							System.out.println("end of udp");
						} catch (SocketTimeoutException e) {
							// socket timeout is normal
						} catch (Exception e) {
							Log.w(TAG,  "Dgram reading thread caught " + e.getClass().getName() + " exception: " + e.getMessage());
						}
					}
				} finally {
					if ( mDatagramSocket != null ) {
						System.out.println("udp socket closed2");
						mDatagramSocket.close(); 
					}
				}
			}
		};
		dgramThread.start();
	}
	
	public void createTcpThread(final int port, final String serverIP) throws Exception{
		final int numBytes = 1000 * (int) Math.pow(10, port - mBasePort);
		mServerSocket = new ServerSocket();
		mServerSocket.bind(new InetSocketAddress(serverIP, port));
		mServerSocket.setSoTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 5000));
		
		Log.i(TAG,  "Server socket = " + mServerSocket.getLocalSocketAddress());
		
		Thread tcpThread = new Thread() {

			public void run() {
				// for debugging purpose
				System.out.println("TCP thread " + port);
				
				byte[] header = new byte[4];
				byte[] buf = new byte[numBytes];
				int socketTimeout = NetBase.theNetBase().config().getAsInt("net.timeout.socket", 5000);
				try {
					
					while ( !isShutdown() ) {
						Socket sock = null;
						try {
							
							// accept() blocks until a client connects.  When it does, a new socket is created that communicates only
							// with that client.  That socket is returned.
							sock = mServerSocket.accept();
							System.out.println("tcp thread accpted");
							// We're going to read from sock, to get the message to echo, but we can't risk a client mistake
							// blocking us forever.  So, arrange for the socket to give up if no data arrives for a while.
							sock.setSoTimeout(socketTimeout);
							InputStream is = sock.getInputStream();
							OutputStream os = sock.getOutputStream();
							// Read the header.  Either it gets here in one chunk or we ignore it.  
							int len = is.read(header);
							
							if ( len != HEADER_STR.length() )
								throw new Exception("Bad header length: got " + len + " but wanted " + HEADER_STR.length());
							String headerStr = new String(header); 
							if ( !headerStr.equalsIgnoreCase(HEADER_STR) )
								throw new Exception("Bad header: got '" + headerStr + "' but wanted '" + HEADER_STR + "'");
							
							System.arraycopy(RESPONSE_OKAY_STR.getBytes(), 0, buf, 0, HEADER_STR.length());
							String msg = "!";
							/*
							// copy the msg i times into buf until buf is full
							for (int i = 0; i < numBytes-4; i++){
								System.arraycopy(msg.getBytes(), 0, buf, 4+i, msg.getBytes().length);
							}*/
							// send the packet
							os.write(buf, 0, 4);
							for(int i = 0; i < numBytes; i++){
								os.write(msg.getBytes());
							}
							System.out.println("end of tcp");
						} catch (SocketTimeoutException e) {
							// normal behavior, but we're done with the client we were talking with
						} catch (Exception e) {
							Log.i(TAG, "TCP thread caught " + e.getClass().getName() + " exception: " + e.getMessage());
						} finally {
							if ( sock != null ) {
								try { 
									sock.close(); 
									sock = null;
									System.out.println("tcp socket closed 1");
								} catch (Exception e) {
									
								}
							}
						}
					}
				} catch (Exception e) {
					Log.w(TAG, "TCP server thread exiting due to exception: " + e.getMessage());
				} finally {
					if ( mServerSocket != null ) try {
						mServerSocket.close();
						System.out.println("tcp socket closed 2");
						} catch (Exception e) {}
				}
				
			}
		};
		tcpThread.start();
	}
	
	/**
	 * Returns string summarizing the status of this server.  The string is printed by the dumpservicestate
	 * console application, and is also available by executing dumpservicestate through the web interface.
	 */
	@Override
	public String dumpState() {
		StringBuilder sb = new StringBuilder(super.dumpState());
		sb.append("\nListening on: ");
		if ( mServerSocket != null ) sb.append(mServerSocket.toString());
		if ( mDatagramSocket != null ) sb.append(mDatagramSocket.toString());
		sb.append("\n");
		return sb.toString();
	}
}
