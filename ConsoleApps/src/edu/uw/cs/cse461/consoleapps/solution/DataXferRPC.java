package edu.uw.cs.cse461.consoleapps.solution;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.consoleapps.DataXferInterface;
import edu.uw.cs.cse461.consoleapps.DataXferInterface.DataXferRawInterface;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRateInterval;

public class DataXferRPC extends NetLoadableConsoleApp implements DataXferInterface{
	private static final String TAG="DataXferRPC";
	
	public DataXferRPC() {
		super("dataxferrpc");
	}
	
	@Override
	public void run() {
		
	}
	
	public byte[] DataXfer(JSONObject header, String hostIP, int port, int timeout) throws JSONException, IOException{
		return null;
		
	}
	
	public TransferRateInterval DataXferRate(JSONObject header, String hostIP, int port, int timeout, int nTrials){
		return null;
		
	}
}
