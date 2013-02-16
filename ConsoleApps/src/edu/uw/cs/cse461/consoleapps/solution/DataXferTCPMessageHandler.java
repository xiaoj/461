package edu.uw.cs.cse461.consoleapps.solution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.consoleapps.DataXferInterface;
import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.service.DataXferRawService;
import edu.uw.cs.cse461.service.DataXferServiceBase;
import edu.uw.cs.cse461.service.EchoServiceBase;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRate;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRateInterval;

public class DataXferTCPMessageHandler extends NetLoadableConsoleApp implements DataXferInterface{
	public DataXferTCPMessageHandler() throws Exception {
		super("dataxfertcpmessagehandler");
	}
	
	@Override
	public void run(){	
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
			TransferRateInterval tcpStats = DataXferRate(DataXferServiceBase.HEADER_STR, server, basePort, socketTimeout, xferLength, nTrials);
			System.out.println("\nTCP: xfer rate = " + String.format("%9.0f", tcpStats.mean() * 1000.0) + " bytes/sec.");
			System.out.println("TCP: failure rate = " + String.format("%5.1f", tcpStats.failureRate()) +
					" [" + tcpStats.nAborted()+ "/" + tcpStats.nTrials() + "]");

		} catch (Exception e) {
			System.out.println("Unanticipated exception: " + e.getMessage());
		}
	}
	
	public byte[] DataXfer(String header, String hostIP, int port, int timeout, int xferLength) throws JSONException, IOException{
		Socket tcpSocket;
		System.out.println("port1:"+port);
		tcpSocket = new Socket(hostIP, port);
		tcpSocket.setSoTimeout(timeout);
		tcpSocket.setTcpNoDelay(true);
		System.out.println("port2:"+tcpSocket.getPort());
		// send header
		TCPMessageHandler tcpHandler = new TCPMessageHandler(tcpSocket);
		tcpHandler.sendMessage(header);
		
		// send message
		JSONObject controlMsg = new JSONObject();
		controlMsg.put("transferSize", xferLength);
		tcpHandler.sendMessage(controlMsg);

		tcpSocket.shutdownOutput();
		
		// read the response
		int dataLength = 0; // keep track of the total received data length
		byte[] receiveBuf = new byte[xferLength];
		ByteBuffer totalBuf = ByteBuffer.wrap(receiveBuf);

		// read the header
		String headerStr = tcpHandler.readMessageAsString();
		if ( !headerStr.equalsIgnoreCase(EchoServiceBase.RESPONSE_OKAY_STR)){
			TransferRate.abort("tcp", xferLength);
			System.out.println("Bad response header: got " + headerStr + " but expected '" + EchoServiceBase.RESPONSE_OKAY_STR + "'");
		} else {
			
			// read the messages
			for(int i = 0; i < xferLength/1000; i++){
				byte[] buf = tcpHandler.readMessageAsBytes();
				if (buf.length > 0){
					totalBuf.put(buf);
					dataLength += buf.length;
				}else{
					TransferRate.abort("tcp", xferLength);
					//System.out.println("Bad response: got " + dataLength + " data but expected " + xferLength + " data.");
				}
			}
			
			// read the last message if it's < 1000 bytes
			if (xferLength % 1000 != 0){
				byte[] buf = tcpHandler.readMessageAsBytes();
				dataLength += buf.length;
				if (buf.length == xferLength){
					totalBuf.put(buf);
				}else{
					TransferRate.abort("tcp", xferLength);
				}
			}
			
			if (dataLength != xferLength){
				// server doesn't send back the requested amount of data
				TransferRate.abort("tcp", xferLength);
			}
			
		}
		tcpSocket.close();
		return totalBuf.array();
	}
	
	public TransferRateInterval DataXferRate(String header, String hostIP, int port, int timeout, int xferLength, int nTrials){
		for ( int trial=0; trial<nTrials; trial++) {
			try {
				TransferRate.start("tcp");
				DataXfer(header, hostIP, port, timeout, xferLength);
				TransferRate.stop("tcp", xferLength);
			} catch (Exception e) {
				TransferRate.abort("tcp", xferLength);
				System.out.println("TCP trial failed: " + e.getMessage());
			}
		}
		return TransferRate.get("tcp");
	}
	
}
