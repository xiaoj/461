package edu.uw.cs.cse461.consoleapps.solution;

//import edu.uw.cs.cse461.consoleapps.PingInterface.PingRawInterface;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTimeInterval;
import edu.uw.cs.cse461.consoleapps.PingInterface;


public class PingTCPMessageHandler extends NetLoadableConsoleApp implements PingInterface{
	public PingTCPMessageHandler() {
		super("ping");
		// TODO Auto-generated constructor stub
	}

	public ElapsedTimeInterval ping(String header, String hostIP, int port, int timeout, int nTrials) throws Exception{
		return null;
	}
	
	@Override
	public void run(){
		
	}
}
