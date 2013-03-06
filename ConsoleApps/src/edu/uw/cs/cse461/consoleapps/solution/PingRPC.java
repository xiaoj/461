package edu.uw.cs.cse461.consoleapps.solution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.json.JSONObject;

import edu.uw.cs.cse461.consoleapps.PingInterface;
import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.rpc.RPCCall;
import edu.uw.cs.cse461.service.EchoRPCService;
//import edu.uw.cs.cse461.service.EchoServiceBase;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTime;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTimeInterval;

public class PingRPC extends NetLoadableConsoleApp implements PingInterface{
	//private static final String TAG="PingRPC";
	
	public PingRPC() {
		super("pingrpc");
	}
	
	@Override
	public void run() {
		try {
			// Eclipse doesn't support System.console()
			BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
			ConfigManager config = NetBase.theNetBase().config();

			int timeout = config.getAsInt("net.timeout.socket", 5000);
			
			String targetIP = config.getProperty("net.server.ip");
			if ( targetIP == null ) {
				System.out.println("No net.server.ip entry in config file.");
				System.out.print("Enter a server ip, or empty line to exit: ");
				targetIP = console.readLine();
				if ( targetIP == null || targetIP.trim().isEmpty() ) return;
			}

			System.out.print("Enter the server's RPC port, or empty line to exit: ");
			String targetTCPPortStr = console.readLine();
			if ( targetTCPPortStr == null || targetTCPPortStr.trim().isEmpty() ) return;
			int targetRPCPort = Integer.parseInt(targetTCPPortStr);
			
			System.out.print("Enter number of trials: ");
			String trialStr = console.readLine();
			int nTrials = Integer.parseInt(trialStr);
			
			ElapsedTimeInterval tcpResult = null;
			if ( targetRPCPort != 0 ) {
				ElapsedTime.clear();
				JSONObject header = new JSONObject().put(EchoRPCService.HEADER_TAG_KEY, EchoRPCService.HEADER_STR);
				tcpResult = ping(header, targetIP, targetRPCPort, timeout, nTrials);
			}
			
			if ( tcpResult != null ) {
				System.out.println("TCP: " + String.format("%.2f msec (%d failures)", tcpResult.mean(), tcpResult.nAborted()));
			}

		} catch (Exception e) {
			System.out.println("PingRPC.run() caught exception: " +e.getMessage());
		}
	}
	
	public ElapsedTimeInterval ping(JSONObject header, String hostIP, int port, int timeout, int nTrials) throws Exception{
		try {
			for (int i = 0; i < nTrials; i++){
				ElapsedTime.start("Ping_RPCTotalDelay");
				// send message
				JSONObject args = new JSONObject().put(EchoRPCService.HEADER_KEY, header)
				.put(EchoRPCService.PAYLOAD_KEY, "");

				JSONObject response = RPCCall.invoke(hostIP, port, "echorpc", "echo", args, timeout );

				if ( response == null ) throw new IOException("RPC failed; response is null");
				// examine response
				
				JSONObject rcvdHeader = response.optJSONObject(EchoRPCService.HEADER_KEY);
				
				if ( rcvdHeader == null || !rcvdHeader.has(EchoRPCService.HEADER_TAG_KEY)||
						!rcvdHeader.getString(EchoRPCService.HEADER_TAG_KEY).equalsIgnoreCase(EchoRPCService.RESPONSE_OKAY_STR)){
					throw new IOException("bad response");
				}
				ElapsedTime.stop("Ping_RPCTotalDelay");
			}
		} catch (Exception e) {
			ElapsedTime.abort("Ping_RPCTotalDelay");
			System.out.println("Exception: " + e.getMessage());
		} 
		
		return ElapsedTime.get("Ping_RPCTotalDelay");
	}
}
