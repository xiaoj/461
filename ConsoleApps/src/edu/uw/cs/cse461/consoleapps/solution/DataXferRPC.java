package edu.uw.cs.cse461.consoleapps.solution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.ByteBuffer;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.consoleapps.DataXferInterface;
import edu.uw.cs.cse461.net.base.NetBase;

import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.rpc.RPCCall;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.service.DataXferRPCService;
import edu.uw.cs.cse461.service.DataXferServiceBase;
import edu.uw.cs.cse461.service.EchoRPCService;
import edu.uw.cs.cse461.service.EchoServiceBase;
import edu.uw.cs.cse461.util.Base64;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRate;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRateInterval;


public class DataXferRPC extends NetLoadableConsoleApp implements DataXferInterface{
	private static final String TAG="DataXferRPC";
	
	public DataXferRPC() {
		super("dataxferrpc");
	}
	
	@Override
	public void run() {
		
		try {
			// Eclipse doesn't support System.console()
			BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

			ConfigManager config = NetBase.theNetBase().config();
			String server = config.getProperty("net.server.ip");
			if ( server == null ) {
				System.out.print("Enter a host ip, or exit to exit: ");
				server = console.readLine();
				if ( server == null ) return;
				if ( server.equals("exit")) return;
			}
			
			System.out.print("Enter port number, or empty line to exit: ");
			String portStr = console.readLine();
			if ( portStr == null || portStr.trim().isEmpty() ) return;
			int basePort = Integer.parseInt(portStr);
			
			int socketTimeout = config.getAsInt("net.timeout.socket", -1);
			if ( socketTimeout < 0 ) {
				System.out.print("Enter socket timeout (in msec.): ");
				String timeoutStr = console.readLine();
				socketTimeout = Integer.parseInt(timeoutStr);
			}

			System.out.print("Enter number of trials: ");
			String trialStr = console.readLine();
			int nTrials = Integer.parseInt(trialStr);
			System.out.print("Enter transfer size: ");
			String xferLengthStr = console.readLine();
			int xferLength = Integer.parseInt(xferLengthStr);

			System.out.println("\n" + xferLength + " bytes");
			
			TransferRate.clear();
			JSONObject header = new JSONObject().put(DataXferRPCService.HEADER_TAG_KEY, DataXferRPCService.HEADER_STR);
			TransferRateInterval tcpStats = DataXferRate(header, server, basePort, socketTimeout, nTrials);
			System.out.println("\nTCP: xfer rate = " + String.format("%9.0f", tcpStats.mean() * 1000.0) + " bytes/sec.");
			System.out.println("TCP: failure rate = " + String.format("%5.1f", tcpStats.failureRate()) +
					" [" + tcpStats.nAborted()+ "/" + tcpStats.nTrials() + "]");

		} catch (Exception e) {
			System.out.println("Unanticipated exception: " + e.getMessage());
		}
	
		
	}
	
	public byte[] DataXfer(JSONObject header, String hostIP, int port, int timeout) throws Exception{
		try {
			// send message
			JSONObject args = new JSONObject().put(DataXferRPCService.HEADER_KEY, header);
			System.out.println("args: "+args);
			JSONObject response = RPCCall.invoke(hostIP, port, "dataxferrpc", "dataxfer", args, timeout );
			if ( response == null ) throw new IOException("dataXferRPC failed; response is null");

			// examine response
			JSONObject rcvdHeader = response.optJSONObject(DataXferRPCService.HEADER_KEY);
			if ( rcvdHeader == null || !rcvdHeader.has(DataXferRPCService.HEADER_TAG_KEY)||
					!rcvdHeader.getString(DataXferRPCService.HEADER_TAG_KEY).equalsIgnoreCase(DataXferServiceBase.RESPONSE_OKAY_STR))
				throw new IOException("Bad response header: got '" + rcvdHeader.toString() +
						               "' but wanted a JSONOBject with key '" + DataXferRPCService.HEADER_TAG_KEY + "' and string value '" +
						               DataXferServiceBase.RESPONSE_OKAY_STR + "'");

			return Base64.decode(response.getString("data"));
		} catch (Exception e) {
			throw new Exception ("Exception: " + e.getMessage());
			
		} 
	}
	
	public TransferRateInterval DataXferRate(JSONObject header, String hostIP, int port, int timeout, int nTrials) throws JSONException{
		int xferLength = header.getInt("xferLength");
		for ( int trial=0; trial<nTrials; trial++) {
			try {
				TransferRate.start("rpc");
				DataXfer(header, hostIP, port, timeout);
				TransferRate.stop("rpc", xferLength);
			} catch (Exception e) {
				TransferRate.abort("rpc", xferLength);
				System.out.println("RPC trial failed: " + e.getMessage());
			}
		}
		return TransferRate.get("rpc");
	}
		
	
}
