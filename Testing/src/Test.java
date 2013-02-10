import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Test {
	public static void main(String[] args){
		byte buf[] = intToByte(5493859);
		System.out.println(buf.length);
		System.out.println(byteToInt(buf));
	}

	public static byte[] intToByte(int i) {
		ByteBuffer b = ByteBuffer.allocate(4);
		b.order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(i);
		byte buf[] = b.array();
		return buf;
	}

	public static int byteToInt(byte buf[]) {
		ByteBuffer b = ByteBuffer.wrap(reverse(buf));
		return b.getInt(0);
	}
	
	private static byte[] reverse(byte buf[]) {
		byte r[] = new byte[4];
		for(int i = 0; i < 4; i++){
			r[i] = buf[3-i];
		}
		return r;
	}

}