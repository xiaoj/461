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
		super("dataxfer");
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

			int basePort = config.getAsInt("dataxferraw.server.baseport", -1);
			if ( basePort == -1 ) {
				System.out.print("Enter port number, or empty line to exit: ");
				String portStr = console.readLine();
				if ( portStr == null || portStr.trim().isEmpty() ) return;
				basePort = Integer.parseInt(portStr);
			}
			
			int socketTimeout = config.getAsInt("net.timeout.socket", -1);
			if ( socketTimeout < 0 ) {
				System.out.print("Enter socket timeout (in msec.): ");
				String timeoutStr = console.readLine();
				socketTimeout = Integer.parseInt(timeoutStr);
				
			}

			System.out.print("Enter number of trials: ");
			String trialStr = console.readLine();
			int nTrials = Integer.parseInt(trialStr);

			for ( int index=0; index<DataXferRawService.NPORTS; index++ ) {

				TransferRate.clear();
				
				int port = basePort + index;
				int xferLength = DataXferRawService.XFERSIZE[index];

				System.out.println("\n" + xferLength + " bytes");

				TransferRateInterval tcpStats = DataXferRate(DataXferServiceBase.RESPONSE_OKAY_STR, server, port, socketTimeout, xferLength, nTrials);
				System.out.println("\nTCP: xfer rate = " + String.format("%9.0f", tcpStats.mean() * 1000.0) + " bytes/sec.");
				System.out.println("TCP: failure rate = " + String.format("%5.1f", tcpStats.failureRate()) +
						           " [" + tcpStats.nAborted()+ "/" + tcpStats.nTrials() + "]");
			}
			
		} catch (Exception e) {
			System.out.println("Unanticipated exception: " + e.getMessage());
		}
	}
	
	public byte[] DataXfer(String header, String hostIP, int port, int timeout, int xferLength) throws JSONException, IOException{
		Socket tcpSocket;
		tcpSocket = new Socket(hostIP, port);
		tcpSocket.setSoTimeout(timeout);
		
		InputStream is = tcpSocket.getInputStream();
		OutputStream os = tcpSocket.getOutputStream();
		int dataLength = 0; // keep track of the total received data length
		
		// send header
		TCPMessageHandler tcpHandler = new TCPMessageHandler(tcpSocket);
		tcpHandler.sendMessage(header);
		
		// send message
		JSONObject controlMsg = new JSONObject();
		controlMsg.put("transferSize", xferLength);
		tcpHandler.sendMessage(controlMsg);
		
		tcpSocket.shutdownOutput();
/*
		byte[] receiveBuf = new byte[EchoServiceBase.RESPONSE_OKAY_STR.length() + xferLength];
		ByteBuffer totalBuf = ByteBuffer.wrap(receiveBuf);
		int len = is.read(receiveBuf);
		totalBuf.put(receiveBuf);
		dataLength += len;
*/
		// read the header response
		String headerStr = tcpHandler.readMessageAsString();
		System.out.println("headerStr: "+headerStr);
		if ( !headerStr.equalsIgnoreCase(EchoServiceBase.RESPONSE_OKAY_STR)){
			TransferRate.abort("tcp", xferLength);
			System.out.println("Bad response header: got " + headerStr + " but expected '" + EchoServiceBase.RESPONSE_OKAY_STR + "'");
		} else {
			// read the response messages
			
			System.out.println("message: "+new String(tcpHandler.readMessageAsBytes()));
			/*
			while(len != -1 && dataLength < (EchoServiceBase.RESPONSE_OKAY_STR.length() + xferLength)){
				len = is.read(receiveBuf);
				if (len != -1){
					dataLength += len;
				}else{
					TransferRate.abort("tcp", xferLength);
					System.out.println("Bad response: got " + dataLength + " data but expected " + xferLength + " data.");
				}
			}	
			*/	
		}
		tcpSocket.close();
		return headerStr.getBytes();
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
