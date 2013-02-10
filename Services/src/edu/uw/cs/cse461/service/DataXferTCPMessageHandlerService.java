package edu.uw.cs.cse461.service;

import java.io.IOException;

import org.json.JSONException;

import edu.uw.cs.cse461.util.SampledStatistic.TransferRateInterval;

public class DataXferTCPMessageHandlerService {
	public byte[] DataXfer(String header, String hostIP, int port, int timeout, int xferLength) throws JSONException, IOException{
		return null;
	}
	public TransferRateInterval DataXferRate(String header, String hostIP, int port, int timeout, int xferLength, int nTrials){
		return null;
	}
}
