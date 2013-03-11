package edu.uw.cs.cse461.net.rpc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableService;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.IPFinder;
import edu.uw.cs.cse461.util.Log;

/**
 * Implements the side of RPC that receives remote invocation requests.
 * 
 * @author zahorjan
 *
 */
public class RPCService extends NetLoadableService implements Runnable, RPCServiceInterface {
	private static final String TAG="RPCService";
	private int port;
	private ServerSocket serverSocket;
	private HashMap<String, HashMap<String, RPCCallableMethod>> map;
	
	/**
	 * Constructor.  Creates the Java ServerSocket and binds it to a port.
	 * If the config file specifies an rpc.server.port value, it should be bound to that port.
	 * Otherwise, you should specify port 0, meaning the operating system should choose a currently unused port.
	 * <p>
	 * Once the port is created, a thread needs to be spun up to listen for connections on it.
	 * 
	 * @throws Exception
	 */
	public RPCService() throws Exception {
		super("rpc");
		ConfigManager config = NetBase.theNetBase().config();
		port = config.getAsInt("rpc.server.port", 0);
		String serverIP = IPFinder.localIP();
		if ( serverIP == null ) throw new Exception("IPFinder isn't providing the local IP address.  Can't run.");
		
		serverSocket =  new ServerSocket();
		serverSocket.setReuseAddress(true);
		serverSocket.bind(new InetSocketAddress(serverIP, port));
		serverSocket.setSoTimeout( NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 500));
		
		map = new HashMap<String, HashMap<String, RPCCallableMethod>>();
		
		Thread t = new Thread(this);
		t.start();
	}
	
	/**
	 * Executed by an RPCService-created thread.  Sits in loop waiting for
	 * connections, then creates an RPCCalleeSocket to handle each one.
	 */
	@Override
	public void run() {
		Socket socket = null;
		boolean persistentConnection = false;
		try {
			while ( !mAmShutdown ) {
				
				try {
					if (socket == null){
						socket = serverSocket.accept();
						
					}else{
						TCPMessageHandler tcpMessageHandler = null;
						RPCMessage rpcMSG = new RPCMessage();
						JSONObject sendMSG = rpcMSG.marshall();
						JSONObject readMSG = null;

						try {
							tcpMessageHandler = new TCPMessageHandler(socket);
							tcpMessageHandler.setTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.socket", 5000));
							tcpMessageHandler.setNoDelay(true);

							readMSG = tcpMessageHandler.readMessageAsJSONObject();

							// Initial Control Handshake
							// send response msg
							if (readMSG.has("options")){
								JSONObject options = readMSG.getJSONObject("options");
								if (options.has("connection")){
									String connection = options.getString("connection");
									persistentConnection = connection.equalsIgnoreCase("keep-alive");
									if (persistentConnection){
										sendMSG.put("value", options);
									}
								}
							}
							sendMSG.put("callid", readMSG.get("id")).put("type", "OK");
							tcpMessageHandler.sendMessage(sendMSG);
							// RPC Call Inovcation

							JSONObject readMSG2 = tcpMessageHandler.readMessageAsJSONObject();
							int callid = readMSG2.getInt("id");
							String serviceName = readMSG2.getString("app");
							String methodName = readMSG2.getString("method");

							RPCCallableMethod method = map.get(serviceName).get(methodName);
							JSONObject retval = method.handleCall(readMSG2.getJSONObject("args"));

							// send invoke msg
							RPCMessage rpcMSG2 = new RPCMessage();
							JSONObject sendMSG2 = rpcMSG2.marshall();
							sendMSG2.put("callid", callid).put("type", "OK")
							.put("value", retval);
							tcpMessageHandler.sendMessage(sendMSG2);

						} catch (Exception e){
							// check for sanity
							if(readMSG != null && !sendMSG.has("type")){
								String type = readMSG.getString("type");

								// Initial Control Handshake Error Response
								if (type.equalsIgnoreCase("control")){
									sendMSG.put("callid", readMSG.get("id")).put("type", "ERROR")
									.put("msg", e);
									try {
										tcpMessageHandler.sendMessage(sendMSG);
									} catch (IOException e1) {
										e1.printStackTrace();
									}
								}

								// RPC Call Inovcation Error Response
								if (type.equalsIgnoreCase("invoke")){
									sendMSG.put("callid", readMSG.get("id")).put("type", "ERROR")
									.put("message", e).put("callargs", readMSG);
									try {
										tcpMessageHandler.sendMessage(sendMSG);
									} catch (IOException e1) {
										e1.printStackTrace();
									}
								}
							}
						} finally {
							if ( socket != null && !persistentConnection){ 
								try { socket.close(); socket = null;} catch (Exception e) {}
							}
						}
					}
				} catch (SocketTimeoutException e) {
					// this is normal.  Just loop back and see if we're terminating.
				} catch (JSONException e) {
					System.out.println("RPC JSONException: " + e);
				} 
			}
		}catch (Exception e) {
			Log.w(TAG, "Server thread exiting due to exception: " + e.getMessage());
		} finally {
			if ( serverSocket != null )  try { serverSocket.close(); } catch (Exception e) {}
			serverSocket = null;
		}

	}
	
	/**
	 * Services and applications with RPC callable methods register them with the RPC service using this routine.
	 * Those methods are then invoked as callbacks when an remote RPC request for them arrives.
	 * @param serviceName  The name of the service.
	 * @param methodName  The external, well-known name of the service's method to call
	 * @param method The descriptor allowing invocation of the Java method implementing the call
	 * @throws Exception
	 */
	@Override
	public synchronized void registerHandler(String serviceName, String methodName, RPCCallableMethod method) throws Exception {
		HashMap<String, RPCCallableMethod> value = new HashMap<String, RPCCallableMethod>();
		value.put(methodName, method);
		map.put(serviceName, value);
	}
	
	/**
	 * Some of the testing code needs to retrieve the current registration for a particular service and method,
	 * so this interface is required.  You probably won't find a use for it in your code, though.
	 * 
	 * @param serviceName  The service name
	 * @param methodName The method name
	 * @return The existing registration for that method of that service, or null if no registration exists.
	 */
	public RPCCallableMethod getRegistrationFor( String serviceName, String methodName) {
		HashMap<String, RPCCallableMethod> value = map.get(serviceName);
		if(value == null){
			return null;
		}
		return value.get(methodName);
	}
	
	/**
	 * Returns the port to which the RPC ServerSocket is bound.
	 * @return The RPC service's port number on this node
	 */
	@Override
	public int localPort() {
		return port;
	}
	
	@Override
	public String dumpState() {
		StringBuilder sb = new StringBuilder("RPC service");
		sb.append("\nListening on: ");
		if ( serverSocket != null ) sb.append(serverSocket.toString());
		sb.append("\n");
		return sb.toString();
	}
}
