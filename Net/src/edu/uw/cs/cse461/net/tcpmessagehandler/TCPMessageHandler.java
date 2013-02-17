package edu.uw.cs.cse461.net.tcpmessagehandler;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.util.Log;


/**
 * Sends/receives a message over an established TCP connection.
 * To be a message means the unit of write/read is demarcated in some way.
 * In this implementation, that's done by prefixing the data with a 4-byte
 * length field.
 * <p>
 * Design note: TCPMessageHandler cannot usefully subclass Socket, but rather must
 * wrap an existing Socket, because servers must use ServerSocket.accept(), which
 * returns a Socket that must then be turned into a TCPMessageHandler.
 *  
 * @author zahorjan
 *
 */
public class TCPMessageHandler implements TCPMessageHandlerInterface {
	private static final String TAG="TCPMessageHandler";
	private Socket socket;
	private int maxLength;
	/**
	 * We need an "on the wire" format for a binary integer.
	 * This method encodes into that format, which is little endian
	 * (low order bits of int are in element [0] of byte array, etc.).
	 * @param i
	 * @return A byte[4] encoding the integer argument.
	 */
	protected static byte[] intToByte(int i) {
		ByteBuffer b = ByteBuffer.allocate(4);
		b.order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(i);
		byte buf[] = b.array();
		return buf;
	}
	
	/**
	 * We need an "on the wire" format for a binary integer.
	 * This method decodes from that format, which is little endian
	 * (low order bits of int are in element [0] of byte array, etc.).
	 * 
	 * @param buf  buf is byte array contains 4 bytes
	 * @return return int store in buf, unless buf contain not 4 bytes
	 */
	protected static int byteToInt(byte buf[]) {
		// You need to implement this.  It's the inverse of intToByte().
		if(buf.length == 4){
			ByteBuffer b = ByteBuffer.wrap(reverse(buf));
			return b.getInt(0);
		}
		return 0;
	}

	/*
	 * helper function to byteToInt to reverse bytes. 
	 * Because ByteBuffer ordering does not reverse bytes.
	 */
	private static byte[] reverse(byte buf[]) {
		byte r[] = new byte[4];
		for(int i = 0; i < 4; i++){
			r[i] = buf[3-i];
		}
		return r;
	}
	
	
	/**
	 * Constructor, associating this TCPMessageHandler with a connected socket.
	 * @param sock
	 * @throws IOException
	 */
	public TCPMessageHandler(Socket sock) throws IOException {
		if (sock == null) {
			throw new NullPointerException("socket is null");
		}
		
		if (!sock.isConnected()) {
			throw new IOException("socket is not connected");
		}	
		socket = sock;
		maxLength = Integer.MAX_VALUE;
	}
	
	/**
	 * Closes the underlying socket and renders this TCPMessageHandler useless.
	 */
	public void close() {
		if(socket != null){
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		socket = null;
	}
	
	/**
	 * Set the read timeout on the underlying socket.
	 * @param timeout Time out, in msec.
	 * @return The previous time out.
	 */
	@Override
	public int setTimeout(int timeout) throws SocketException {
		int prev = socket.getSoTimeout();
		socket.setSoTimeout(timeout);
		return prev;
	}
	
	/**
	 * Enable/disable TCPNoDelay on the underlying TCP socket.
	 * @param value The value to set
	 * @return The old value
	 */
	@Override
	public boolean setNoDelay(boolean value) throws SocketException {
		socket.setTcpNoDelay(value);
		boolean prev = socket.getTcpNoDelay();
		return prev;
	}
	
	/**
	 * Sets the maximum allowed size for which decoding of a message will be attempted.
	 * @return The previous setting of the maximum allowed message length.
	 * 		   If maxLen is invalid input return -1, no new max length is set.
	 */
	@Override
	public int setMaxReadLength(int maxLen) {
		if(maxLen < 0){
			return -1;
		}

		int prev = maxLength;
		maxLength = maxLen;
		return prev;
	}

	/**
	 * Returns the current setting for the maximum read length
	 */
	@Override
	public int getMaxReadLength() {
		return maxLength;
	}
	
	//--------------------------------------------------------------------------------------
	// send routines
	//--------------------------------------------------------------------------------------
	
	@Override
	public void sendMessage(byte[] buf) throws IOException {
		OutputStream os = socket.getOutputStream();
		byte[] length = intToByte(buf.length);
		os.write(length);
		os.write(buf);
	}
	
	/**
	 * Uses str.getBytes() for conversion.
	 */
	@Override
	public void sendMessage(String str) throws IOException {
		byte[] buf = str.getBytes();
		sendMessage(buf);
	}

	/**
	 * We convert the int to the one the wire format and send as bytes.
	 */
	@Override
	public void sendMessage(int value) throws IOException{
		byte[] buf = intToByte(value);
		sendMessage(buf);
	}
	
	/**
	 * Sends JSON string representation of the JSONArray.
	 */
	@Override
	public void sendMessage(JSONArray jsArray) throws IOException {
		String str = jsArray.toString();
		byte[] buf = str.getBytes();
		sendMessage(buf);
	}
	
	/**
	 * Sends JSON string representation of the JSONObject.
	 */
	@Override
	public void sendMessage(JSONObject jsObject) throws IOException {
		String str = jsObject.toString();
		byte[] buf = str.getBytes();
		sendMessage(buf);
	}
	
	//--------------------------------------------------------------------------------------
	// read routines
	//   All of these invert any encoding done by the corresponding send method.
	//--------------------------------------------------------------------------------------
	
	@Override
	public byte[] readMessageAsBytes() throws IOException {
		InputStream is = socket.getInputStream();

		// read the length
		int bufLen;
		byte[] headerBuf = new byte[4];
		int len = is.read(headerBuf, 0, 4);
		int payloadLength = byteToInt(headerBuf);

		// use the smaller length between the "length" in the frame and the maxLength
		if (payloadLength < maxLength){
			bufLen = payloadLength;
		}else{
			bufLen = maxLength;
		}

		// read the payload
		byte[] buf = new byte[bufLen];
		if(bufLen == 0){
			// there's no payload
			return buf;
		} else { 
			// keep reading the payload and store it in the buf[]
			int counter = 0;
			try {
				while ( len >= 0 && counter < bufLen) {
					len = is.read(buf, counter, bufLen-counter);
					if (len > -1){
						counter += len;
					}
				}
			} catch (Exception e) {
				System.out.println("TCP read failed: " + e.getMessage());
			}
		}
		return buf;
	}
	
	@Override
	public String readMessageAsString() throws IOException {
		byte[] buf = readMessageAsBytes();
		return new String(buf);
	}

	@Override
	public int readMessageAsInt() throws IOException {
		byte[] buf = readMessageAsBytes();
		return byteToInt(buf);
	}
	
	@Override
	public JSONArray readMessageAsJSONArray() throws IOException, JSONException {
		byte[] buf = readMessageAsBytes();
		String str = new String(buf);
		JSONArray jArray = new JSONArray(str);
		return jArray;
	}
	
	@Override
	public JSONObject readMessageAsJSONObject() throws IOException, JSONException {
		byte[] buf = readMessageAsBytes();
		String str = new String(buf);
		JSONObject jObject = new JSONObject(str);
		return jObject;
	}
}
