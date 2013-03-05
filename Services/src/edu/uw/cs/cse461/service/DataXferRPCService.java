package edu.uw.cs.cse461.service;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadableInterface.NetLoadableServiceInterface;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.IPFinder;

public class DataXferRPCService extends DataXferServiceBase implements NetLoadableServiceInterface{

	public static final String HEADER_KEY = "header";
	public static final String HEADER_TAG_VALUE = "xfer";
	public static final String HEADER_TAG_KEY = "tag";
	public static final String HEADER_XFERLENGTH_KEY = "xferLength";
	
	public DataXferRPCService() throws Exception {
		// TODO Auto-generated constructor stub
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
				
	}
	
	
	
}
