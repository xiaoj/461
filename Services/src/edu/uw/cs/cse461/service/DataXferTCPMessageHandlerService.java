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
	private int PACKET_SIZE = 1000;
	
	public DataXferTCPMessageHandlerService() throws Exception{
		super("dataxfertcpmessagehandler");
		// Sanity check -- code below relies on this property
		if ( HEADER_STR.length() != RESPONSE_OKAY_STR.length() )
			throw new Exception("Header and response strings must be same length: '" + HEADER_STR + "' '" + RESPONSE_OKAY_STR + "'");	
		
		String serverIP = IPFinder.localIP();
		if ( serverIP == null ) throw new Exception("IPFinder isn't providing the local IP address.  Can't run.");
		
		int port = 0;
		createThread(port, serverIP);
	}
	
	public void createThread(final int port, final String serverIP) throws Exception{
		mServerSocket = new ServerSocket();
		mServerSocket.bind(new InetSocketAddress(serverIP, port));
		mServerSocket.setSoTimeout( NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 500));

		Log.i(TAG,  "Server socket = " + mServerSocket.getLocalSocketAddress());

		Thread tcpThread = new Thread() {
			public void run() {
				try {
					while ( !mAmShutdown ) {
						Socket sock = null;
						
						try {
							sock = mServerSocket.accept();
							TCPMessageHandler tcpMessageHandler = null;
							
							try {
								tcpMessageHandler = new TCPMessageHandler(sock);
								tcpMessageHandler.setTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.socket", 5000));
								tcpMessageHandler.setNoDelay(true);

								String header = tcpMessageHandler.readMessageAsString();
								if ( ! header.equalsIgnoreCase(HEADER_STR))
									throw new Exception("Bad header: '" + header + "'");
								JSONObject headStr = tcpMessageHandler.readMessageAsJSONObject();
								int transferSize = headStr.getInt("transferSize");
								
								// now respond and send the header
								tcpMessageHandler.sendMessage(EchoServiceBase.RESPONSE_OKAY_STR);

								// send the messages
								String msg = "!"; 
								if ( transferSize >= PACKET_SIZE){
									// transferSize >= 1000 bytes
									byte[] buf = new byte[PACKET_SIZE];
									// copy the msg i times into buf until buf is full (PACKET_SIZE = 1000 bytes)
									for (int i = 0; i < PACKET_SIZE; i++){
										System.arraycopy(msg.getBytes(), 0, buf, i, msg.getBytes().length);
									}

									// send the packet (1000 bytes) i times until the total bytes sent = size
									for (int i = 0; i < transferSize/PACKET_SIZE; i++){
										tcpMessageHandler.sendMessage(buf);
									}
								}

								// send the first/last packet if it's < 1000 bytes
								if (transferSize % PACKET_SIZE != 0){
									byte[] lastBuf= new byte[transferSize % PACKET_SIZE];
									for(int i = 0; i < transferSize % PACKET_SIZE ; i++){
										System.arraycopy(msg.getBytes(), 0, lastBuf, i, msg.getBytes().length);
									}
									tcpMessageHandler.sendMessage(lastBuf);
								}

								sock.shutdownOutput();
							} catch (SocketTimeoutException e) {
								Log.e(TAG, "Timed out waiting for data on tcp connection");
							} catch (EOFException e) {
								// normal termination of loop
								Log.d(TAG, "EOF on tcpMessageHandlerSocket.readMessageAsString()");
							} catch (Exception e) {
								Log.i(TAG, "Unexpected exception while handling connection: " + e.getMessage());
							} finally {
								if ( tcpMessageHandler != null ) try { tcpMessageHandler.close(); } catch (Exception e) {}
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
	
	@Override
	public String dumpState() {
		StringBuilder sb = new StringBuilder(super.dumpState());
		sb.append("\nListening on: ");
		if ( mServerSocket != null ) sb.append(mServerSocket.toString());
		sb.append("\n");
		return sb.toString();
	}
	
}
