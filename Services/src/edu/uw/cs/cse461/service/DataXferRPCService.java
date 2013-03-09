package edu.uw.cs.cse461.service;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadableInterface.NetLoadableServiceInterface;
import edu.uw.cs.cse461.net.rpc.RPCCallableMethod;
import edu.uw.cs.cse461.net.rpc.RPCService;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.IPFinder;

public class DataXferRPCService extends DataXferServiceBase implements NetLoadableServiceInterface{

	public static final String HEADER_KEY = "header";
	public static final String HEADER_TAG_VALUE = "xfer";
	public static final String HEADER_TAG_KEY = "tag";
	public static final String HEADER_XFERLENGTH_KEY = "xferLength";
	
	// A variable capable of describing a method that can be invoked by RPC.
	private RPCCallableMethod dataxferrpc;
	
	public DataXferRPCService() throws Exception {
		super("dataxferrpc");
		
		// Sanity check -- code below relies on this property
		if ( HEADER_STR.length() != RESPONSE_OKAY_STR.length() )
			throw new Exception("Header and response strings must be same length: '" + HEADER_STR + "' '" + RESPONSE_OKAY_STR + "'");	

		String serverIP = IPFinder.localIP();
		if ( serverIP == null ) throw new Exception("IPFinder isn't providing the local IP address.  Can't run.");

		// get the base port
		ConfigManager config = NetBase.theNetBase().config();
		int port = config.getAsInt("dataxferraw.server.baseport", 0);
		if ( port == 0 ) throw new RuntimeException("dataxferraw service can't run -- no dataxferraw.server.baseport entry in config file");
		
		// Set up the method descriptor variable to refer to this->_dataxferrpc()
		dataxferrpc = new RPCCallableMethod(this, "_dataxferrpc");
		// Register the method with the RPC service as externally invocable method "dataxferrpc"
		((RPCService)NetBase.theNetBase().getService("rpc")).registerHandler(loadablename(), "dataxferrpc", dataxferrpc );		
	}
	
	/**
	 * This method is callable by RPC (because of the actions taken by the constructor).
	 * <p>
	 * All RPC-callable methods take a JSONObject as their single parameter, and return
	 * a JSONObject.  (The return value can be null.)  This particular method simply
	 * echos its arguments back to the caller. 
	 * @param args
	 * @return
	 * @throws JSONException
	 */
	public JSONObject _dataxferrpc(JSONObject args) throws Exception {
		
		// check header
		JSONObject header = args.getJSONObject(HEADER_KEY);
		if ( header == null  || !header.has(HEADER_TAG_KEY) || !header.getString(HEADER_TAG_KEY).equalsIgnoreCase(HEADER_STR) )
			throw new Exception("Missing or incorrect header value: '" + header + "'");
		
		// To-do: some dataxfer work here, not echo
		
		return null;
	}
	
}
