package edu.uw.cs.cse461.service;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.rpc.RPCCallableMethod;
import edu.uw.cs.cse461.net.rpc.RPCService;
import edu.uw.cs.cse461.util.Base64;

public class DataXferRPCService extends DataXferServiceBase{

	public static final String HEADER_KEY = "header";
	public static final String HEADER_TAG_VALUE = "xfer";
	public static final String HEADER_TAG_KEY = "tag";
	public static final String HEADER_XFERLENGTH_KEY = "xferLength";
	
	// A variable capable of describing a method that can be invoked by RPC.
	private RPCCallableMethod dataxfer;
	
	public DataXferRPCService() throws Exception {
		super("dataxferrpc");
		
		// Sanity check -- code below relies on this property
		if ( HEADER_STR.length() != RESPONSE_OKAY_STR.length() )
			throw new Exception("Header and response strings must be same length: '" + HEADER_STR + "' '" + RESPONSE_OKAY_STR + "'");	

		// Set up the method descriptor variable to refer to this->dataxfer()
		dataxfer = new RPCCallableMethod(this, "_dataxfer");
		// Register the method with the RPC service as externally invocable method "dataxferrpc"
		//((RPCService)NetBase.theNetBase().getService("rpc")).registerHandler(loadablename(), "dataxferrpc", dataxfer );
		((RPCService)NetBase.theNetBase().getService("rpc")).registerHandler(loadablename(), "dataxfer", dataxfer );
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
	public JSONObject _dataxfer(JSONObject args) throws Exception {
		// check header
		JSONObject header = args.getJSONObject(HEADER_KEY);
		if ( header == null  || !header.has(HEADER_TAG_KEY) || !header.getString(HEADER_TAG_KEY).equalsIgnoreCase(HEADER_STR) )
			throw new Exception("Missing or incorrect header value: '" + header + "'");
		
		// To-do: some dataxfer work here, not echo
		int xferLength = header.getInt("xferLength");
		JSONObject retVal = new JSONObject();
		JSONObject retHeader = new JSONObject();
		
		retHeader.put(HEADER_TAG_KEY, RESPONSE_OKAY_STR).put(HEADER_XFERLENGTH_KEY, xferLength);
		retVal.put(HEADER_KEY, retHeader);
		
		String msg = "!";
		byte[] buf = new byte[xferLength];
		// copy the msg i times into buf until buf is full (PACKET_SIZE = 1000 bytes)
		for (int i = 0; i < xferLength; i++){
			System.arraycopy(msg.getBytes(), 0, buf, i, msg.getBytes().length);
		}
		
		retVal.put("data", Base64.encodeBytes(buf));	
		return retVal;
	}
	
}
