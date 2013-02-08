package edu.uw.cs.cse461.consoleapps.solution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import edu.uw.cs.cse461.consoleapps.PingInterface.PingRawInterface;
import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.service.EchoServiceBase;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTime;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTimeInterval;

/**
 * Raw sockets version of ping client.
 * @author zahorjan
 *
 */
public class PingRaw extends NetLoadableConsoleApp implements PingRawInterface {
	private static final String TAG="PingRaw";
	
	// ConsoleApp's must have a constructor taking no arguments
	public PingRaw() {
		super("pingraw");
	}
	
	/* (non-Javadoc)
	 * @see edu.uw.cs.cse461.ConsoleApps.PingInterface#run()
	 */
	@Override
	public void run() {
		try {
			// Eclipse doesn't support System.console()
			BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
			ConfigManager config = NetBase.theNetBase().config();

			try {

				String targetIP = config.getProperty("net.server.ip");
				if ( targetIP == null ) {
					System.out.println("No net.server.ip entry in config file.");
					System.out.print("Enter the server's ip, or empty line to exit: ");
					targetIP = console.readLine();
					if ( targetIP == null || targetIP.trim().isEmpty() ) return;
				}

				int targetUDPPort;
				System.out.print("Enter the server's UDP port, or empty line to skip: ");
				String targetUDPPortStr = console.readLine();
				if ( targetUDPPortStr == null || targetUDPPortStr.trim().isEmpty() ) return;
				targetUDPPort = Integer.parseInt(targetUDPPortStr);

				int targetTCPPort;
				System.out.print("Enter the server's TCP port, or empty line to skip: ");
				String targetTCPPortStr = console.readLine();
				if ( targetTCPPortStr == null || targetTCPPortStr.trim().isEmpty() ) targetTCPPort = 0;
				else targetTCPPort = Integer.parseInt(targetTCPPortStr);

				System.out.print("Enter number of trials: ");
				String trialStr = console.readLine();
				int nTrials = Integer.parseInt(trialStr);

				int socketTimeout = config.getAsInt("net.timeout.socket", 5000);
				
				System.out.println("Host: " + targetIP);
				System.out.println("udp port: " + targetUDPPort);
				System.out.println("tcp port: " + targetTCPPort);
				System.out.println("trials: " + nTrials);
				
				ElapsedTimeInterval udpResult = null;
				ElapsedTimeInterval tcpResult = null;

				if ( targetUDPPort != 0  ) {
					ElapsedTime.clear();
					// we rely on knowing the implementation of udpPing here -- we throw
					// away the return value because we'll print the ElaspedTime stats
					udpResult = udpPing(EchoServiceBase.HEADER_BYTES, targetIP, targetUDPPort, socketTimeout, nTrials);
				}

				if ( targetTCPPort != 0 ) {
					ElapsedTime.clear();
					tcpResult = tcpPing(EchoServiceBase.HEADER_BYTES, targetIP, targetTCPPort, socketTimeout, nTrials);
				}

				if ( udpResult != null ) System.out.println("UDP: " + String.format("%.2f msec (%d failures)", udpResult.mean(), udpResult.nAborted()));
				if ( tcpResult != null ) System.out.println("TCP: " + String.format("%.2f msec (%d failures)", tcpResult.mean(), tcpResult.nAborted()));

			} catch (Exception e) {
				System.out.println("Exception: " + e.getMessage());
			} 
		} catch (Exception e) {
			System.out.println("PingRaw.run() caught exception: " +e.getMessage());
		}
	}
	

	/**
	 * Pings the host/port named by the arguments the number of times named by the arguments.
	 * Returns the mean ping time of the trials.
	 */
	@Override
	public ElapsedTimeInterval udpPing(byte[] header, String hostIP, int udpPort, int socketTimeout, int nTrials) {

		DatagramSocket socket;
		try {
			for (int i = 0; i < nTrials; i++) {
				socket = new DatagramSocket();
				socket.setSoTimeout(socketTimeout);
				DatagramPacket packet = new DatagramPacket(header, header.length, new InetSocketAddress(hostIP, udpPort));

				try {
					ElapsedTime.start("PingRaw_UDPTotalDelay");
					socket.send(packet);
					byte[] receiveBuf = new byte[EchoServiceBase.RESPONSE_OKAY_STR.length()];
					DatagramPacket receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);
					socket.receive(receivePacket);
					if (receivePacket.getLength() != EchoServiceBase.RESPONSE_OKAY_STR.length()){
						ElapsedTime.abort("PingRaw_UDPTotalDelay");
						System.out.println("Bad response: sent "+ header.length + " bytes but got back "+ receivePacket.getLength());
					}
					String rcvdHeader = new String(receiveBuf, 0, 4);
					if (!rcvdHeader.equalsIgnoreCase(EchoServiceBase.RESPONSE_OKAY_STR)){
						ElapsedTime.abort("PingRaw_UDPTotalDelay");
						System.out.println("Bad returned header: got '" + rcvdHeader + "' but wanted '" + EchoServiceBase.RESPONSE_OKAY_STR);
					}
					ElapsedTime.stop("PingRaw_UDPTotalDelay");	
				} catch (SocketTimeoutException e) {
					// This exception is thrown if we wait on receive() longer
					// than the timeout
					ElapsedTime.abort("PingRaw_UDPTotalDelay");
					//System.out.println("UDP socket timeout");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					ElapsedTime.abort("PingRaw_UDPTotalDelay");
					e.printStackTrace();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					ElapsedTime.abort("PingRaw_UDPTotalDelay");
					e.printStackTrace();
				}
				socket.close();
				socket = null;
			}
			
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return ElapsedTime.get("PingRaw_UDPTotalDelay");
	}
	
	@Override
	public ElapsedTimeInterval tcpPing(byte[] header, String hostIP, int tcpPort, int socketTimeout, int nTrials) {
		Socket tcpSocket;
		try {
			for (int i = 0; i < nTrials; i++){
				tcpSocket = new Socket(hostIP, tcpPort);
				tcpSocket.setSoTimeout(socketTimeout);
				InputStream is = tcpSocket.getInputStream();
				OutputStream os = tcpSocket.getOutputStream();
				
				try {
					ElapsedTime.start("PingRaw_TCPTotalDelay");
					// send header
					os.write(header);
					tcpSocket.shutdownOutput();
					// read the header.  Either the entire header arrives in one chunk, or we
					// (mistakenly) reject it.
					byte[] headerBuf = new byte[EchoServiceBase.RESPONSE_OKAY_STR.length()];
					int len = is.read(headerBuf);
					if ( len == -1 ){
						ElapsedTime.abort("PingRaw_TCPTotalDelay");
					} else if ( len != EchoServiceBase.RESPONSE_OKAY_STR.length() ){
						ElapsedTime.abort("PingRaw_TCPTotalDelay");
						System.out.println("Bad response header length: got " + len + " but expected " + EchoServiceBase.RESPONSE_OKAY_STR.length());
					} else {
						String headerStr = new String(headerBuf, 0, 4);
						if ( !headerStr.equalsIgnoreCase(EchoServiceBase.RESPONSE_OKAY_STR)){
							ElapsedTime.abort("PingRaw_TCPTotalDelay");
							System.out.println("Bad response header: got '" + headerStr + "' but expected '" + EchoServiceBase.RESPONSE_OKAY_STR + "'");
						}
						ElapsedTime.stop("PingRaw_TCPTotalDelay");
					}
					
				} catch (SocketTimeoutException e) {
					// This exception is thrown if we wait on receive() longer
					// than the timeout
					ElapsedTime.abort("PingRaw_TCPTotalDelay");
					//System.out.println("UDP socket timeout");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					ElapsedTime.abort("PingRaw_TCPTotalDelay");
					//e.printStackTrace();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					ElapsedTime.abort("PingRaw_TCPTotalDelay");
					//e.printStackTrace();
				}
				tcpSocket.close();
				tcpSocket = null;
			}
			
		} catch (SocketException e) {
			// This exception is thrown if we wait on receive() longer
			// than the timeout
			ElapsedTime.abort("PingRaw_TCPTotalDelay");
			System.out.println("TCP socket timeout");
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ElapsedTime.abort("PingRaw_TCPTotalDelay");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ElapsedTime.abort("PingRaw_TCPTotalDelay");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ElapsedTime.abort("PingRaw_TCPTotalDelay");
		}
		
		return ElapsedTime.get("PingRaw_TCPTotalDelay");
	}
}
