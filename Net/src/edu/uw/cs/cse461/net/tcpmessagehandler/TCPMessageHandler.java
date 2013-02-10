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
import edu.uw.cs.cse461.service.EchoServiceBase;
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
	//--------------------------------------------------------------------------------------
	// helper routines
	//--------------------------------------------------------------------------------------

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
	 * @param buf
	 * @return 
	 */
	protected static int byteToInt(byte buf[]) {
		// You need to implement this.  It's the inverse of intToByte().
		ByteBuffer b = ByteBuffer.allocate(4);
		b.order(ByteOrder.BIG_ENDIAN);
		b.put(buf);
		return b.getInt(0);
	}

	/**
	 * Constructor, associating this TCPMessageHandler with a connected socket.
	 * @param sock
	 * @throws IOException
	 */
	public TCPMessageHandler(Socket sock) throws IOException {
		
	}
	
	/**
	 * Closes the underlying socket and renders this TCPMessageHandler useless.
	 */
	public void close() {
	}
	
	/**
	 * Set the read timeout on the underlying socket.
	 * @param timeout Time out, in msec.
	 * @return The previous time out.
	 */
	@Override
	public int setTimeout(int timeout) throws SocketException {
		return 0;
	}
	
	/**
	 * Enable/disable TCPNoDelay on the underlying TCP socket.
	 * @param value The value to set
	 * @return The old value
	 */
	@Override
	public boolean setNoDelay(boolean value) throws SocketException {
		return false;
	}
	
	/**
	 * Sets the maximum allowed size for which decoding of a message will be attempted.
	 * @return The previous setting of the maximum allowed message length.
	 */
	@Override
	public int setMaxReadLength(int maxLen) {
		return 0;
	}

	/**
	 * Returns the current setting for the maximum read length
	 */
	@Override
	public int getMaxReadLength() {
		return 0;
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
		socket.shutdownOutput();
	}
	
	/**
	 * Uses str.getBytes() for conversion.
	 */
	@Override
	public void sendMessage(String str) throws IOException {
	}

	/**
	 * We convert the int to the one the wire format and send as bytes.
	 */
	@Override
	public void sendMessage(int value) throws IOException{
	}
	
	/**
	 * Sends JSON string representation of the JSONArray.
	 */
	@Override
	public void sendMessage(JSONArray jsArray) throws IOException {
	}
	
	/**
	 * Sends JSON string representation of the JSONObject.
	 */
	@Override
	public void sendMessage(JSONObject jsObject) throws IOException {
	}
	
	//--------------------------------------------------------------------------------------
	// read routines
	//   All of these invert any encoding done by the corresponding send method.
	//--------------------------------------------------------------------------------------
	
	@Override
	public byte[] readMessageAsBytes() throws IOException {
		InputStream is = socket.getInputStream();
		
		// read the length
		byte[] headerBuf = new byte[4];
		int len = is.read(headerBuf);
		int length = byteToInt(headerBuf);
		
		// read the payload
		byte[] buf = new byte[length];
		try {
			len = 0;
			while ( len >= 0 ) {
				len = is.read(buf, 0, buf.length);
				if ( len > 0 ) {
					String response = new String(buf, 0, len);
					System.out.print(response);
				}
			}
		} catch (Exception e) {
			System.out.println("TCP read failed: " + e.getMessage());
		}
		return buf;
	}
	
	@Override
	public String readMessageAsString() throws IOException {
		return null;
	}

	@Override
	public int readMessageAsInt() throws IOException {
		return 0;
	}
	
	@Override
	public JSONArray readMessageAsJSONArray() throws IOException, JSONException {
		return null;
	}
	
	@Override
	public JSONObject readMessageAsJSONObject() throws IOException, JSONException {
		return null;
	}
}
