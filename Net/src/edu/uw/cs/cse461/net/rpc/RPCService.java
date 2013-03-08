package edu.uw.cs.cse461.net.rpc;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableService;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCCallMessage;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCCallMessage.RPCControlMessage;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCCallMessage.RPCInvokeMessage;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCResponseMessage;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCResponseMessage.RPCErrorResponseMessage;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCResponseMessage.RPCNormalResponseMessage;
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
		map = new HashMap<String, HashMap<String, RPCCallableMethod>>();
		String serverIP = IPFinder.localIP();
		if ( serverIP == null ) throw new Exception("IPFinder isn't providing the local IP address.  Can't run.");
		serverSocket =  new ServerSocket();
		serverSocket.bind(new InetSocketAddress(serverIP, port));
		serverSocket.setSoTimeout( NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 500));
	}
	
	/**
	 * Executed by an RPCService-created thread.  Sits in loop waiting for
	 * connections, then creates an RPCCalleeSocket to handle each one.
	 */
	@Override
	public void run() {
		Socket socket = null;
		while ( !mAmShutdown ) {
			try {
				socket = serverSocket.accept();
				TCPMessageHandler tcpMessageHandler = null;
				RPCMessage rpcMSG = new RPCMessage();
				JSONObject sendMSG = rpcMSG.marshall();
				JSONObject readMSG = null;

				try {
					tcpMessageHandler = new TCPMessageHandler(socket);
					tcpMessageHandler.setTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.socket", 5000));
					tcpMessageHandler.setNoDelay(true);

					readMSG = tcpMessageHandler.readMessageAsJSONObject();
					String type = readMSG.getString("type");
					System.out.println("type: "+type);
					// Initial Control Handshake
					if (type.equalsIgnoreCase("control")){
						// send response msg
						sendMSG.put("callid", readMSG.get("id")).put("type", "OK");
						tcpMessageHandler.sendMessage(sendMSG);
						System.out.println("connect response sent");
					}

					// RPC Call Inovcation
					if (type.equalsIgnoreCase("invoke")){
						int callid = readMSG.getInt("id");
						String serviceName = readMSG.getString("app");
						String methodName = readMSG.getString("method");
						RPCCallableMethod method = map.get(serviceName).get(methodName);
						JSONObject retval = method.handleCall(readMSG.getJSONObject("args"));
						
						// send invoke msg
						sendMSG.put("callid", callid).put("type", "OK")
									.put("value", retval);
						tcpMessageHandler.sendMessage(sendMSG);
						System.out.println("invoke response sent");
					}

				} catch (Exception e){
					// check for sanity
					if(readMSG != null && !sendMSG.has("type")){
						String type = readMSG.getString("type");
						
						// Initial Control Handshake Error Response
						if (type.equalsIgnoreCase("control")){
							sendMSG.put("callid", readMSG.get("id")).put("type", "ERROR")
							.put("msg", e);
							tcpMessageHandler.sendMessage(sendMSG);
						}
						
						// RPC Call Inovcation Error Response
						if (type.equalsIgnoreCase("invoke")){
							sendMSG.put("callid", readMSG.get("id")).put("type", "ERROR")
							.put("message", e).put("callargs", readMSG);
							tcpMessageHandler.sendMessage(sendMSG);
						}
					}
				}
			} catch (IOException e) {
				System.out.println("RPC IOException: " + e);
			} catch (JSONException e) {
				System.out.println("RPC JSONException: " + e);
			}
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
