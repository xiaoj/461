package edu.uw.cs.cse461.service;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.IPFinder;
import edu.uw.cs.cse461.util.Log;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRateInterval;

public class DataXferTCPMessageHandlerService extends DataXferServiceBase {
	private static final String TAG="DataXferTCPMessageHandlerService";
	
	private ServerSocket mServerSocket;
	private int mBasePort;
	public static final int NPORTS = 4;
	
	public DataXferTCPMessageHandlerService() throws Exception{
		super("dataxfertcpmessagehandler");
		// Sanity check -- code below relies on this property
		if ( HEADER_STR.length() != RESPONSE_OKAY_STR.length() )
			throw new Exception("Header and response strings must be same length: '" + HEADER_STR + "' '" + RESPONSE_OKAY_STR + "'");	
		
		String serverIP = IPFinder.localIP();
		if ( serverIP == null ) throw new Exception("IPFinder isn't providing the local IP address.  Can't run.");
		
		// get the base port
		ConfigManager config = NetBase.theNetBase().config();
		mBasePort = config.getAsInt("dataxferraw.server.baseport", 0);
		if ( mBasePort == 0 ) throw new RuntimeException("dataxferraw service can't run -- no dataxferraw.server.baseport entry in config file");

		createThread(mBasePort, serverIP);
		createThread(mBasePort+1, serverIP);
		createThread(mBasePort+2, serverIP);
		createThread(mBasePort+3, serverIP);
	}
	
	public void createThread(final int port, final String serverIP) throws Exception{
		final int numBytes = 1000 * (int) Math.pow(10, port - mBasePort);
		mServerSocket = new ServerSocket();
		mServerSocket.bind(new InetSocketAddress(serverIP, port));
		mServerSocket.setSoTimeout( NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 500));
		Log.i(TAG,  "Server socket = " + mServerSocket.getLocalSocketAddress());

		Thread tcpThread = new Thread() {
			public void run() {
				try {
					while ( !mAmShutdown ) {
						Socket sock = null;
						byte[] buf = new byte[1000];
						try {
							sock = mServerSocket.accept();  // if this fails, we want out of the while loop...
							// should really spawn a thread here, but the code is already complicated enough that we don't bother
							TCPMessageHandler tcpMessageHandlerSocket = null;
							try {
								// this loop exits when readMessageAsString() throws an IOException indicating EOF, or 
								// because it has timed out on the read
								while ( true ) {
									tcpMessageHandlerSocket = new TCPMessageHandler(sock);
									tcpMessageHandlerSocket.setTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.socket", 5000));
									tcpMessageHandlerSocket.setNoDelay(true);
									
									String header = tcpMessageHandlerSocket.readMessageAsString();
									if ( ! header.equalsIgnoreCase(EchoServiceBase.HEADER_STR))
										throw new Exception("Bad header: '" + header + "'");
									JSONObject headStr = tcpMessageHandlerSocket.readMessageAsJSONObject();
									int size = headStr.getInt("transferSize");
									String msg = "Max is awesome!!!!!!";
									// copy the msg i times into buf until buf is full (1000 bytes)
									for (int i = 0; i < (buf.length / msg.getBytes().length); i+=msg.getBytes().length){
										System.arraycopy(msg.getBytes(), 0, buf, i, 1000);
									}
									
									// now respond
									tcpMessageHandlerSocket.sendMessage(EchoServiceBase.RESPONSE_OKAY_STR);
									// send the packet (1000 bytes) i times until the total bytes sent = size
									for (int i = 0; i < size; i+=buf.length){
										tcpMessageHandlerSocket.sendMessage(buf);
									}
									// send the last packet if it's < 1000 bytes
									if (size % 1000 != 0){
										byte[] lastBuf= new byte[size%1000];
										String lastMsg = "!";
										for(int i = 0; i < size%1000; i++){
											System.arraycopy(lastMsg.getBytes(), 0, lastBuf, i, size%1000);
										}
										tcpMessageHandlerSocket.sendMessage(lastBuf);
									}
								}
							} catch (SocketTimeoutException e) {
								Log.e(TAG, "Timed out waiting for data on tcp connection");
							} catch (EOFException e) {
								// normal termination of loop
								Log.d(TAG, "EOF on tcpMessageHandlerSocket.readMessageAsString()");
							} catch (Exception e) {
								Log.i(TAG, "Unexpected exception while handling connection: " + e.getMessage());
							} finally {
								if ( tcpMessageHandlerSocket != null ) try { tcpMessageHandlerSocket.close(); } catch (Exception e) {}
							}
						} catch (SocketTimeoutException e) {
							// this is normal.  Just loop back and see if we're terminating.
						}
					}
				} catch (Exception e) {
					Log.w(TAG, "Server thread exiting due to exception: " + e.getMessage());
				} finally {
					if ( mServerSocket != null )  try { mServerSocket.close(); } catch (Exception e) {}
					mServerSocket = null;
				}
			}
		};
		tcpThread.start();
	}
}
