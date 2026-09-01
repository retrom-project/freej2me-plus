package org.recompile.mobile;

import java.io.IOException;
import java.io.InputStream;

/** Reads compact-VM JAR resources without probing their unreliable EOF boundary. */
final class MiniJvmResourceReader
{
	private MiniJvmResourceReader() { }

	static byte[] readAvailable(InputStream stream) throws IOException
	{
		int available = stream.available();
		if(available <= 0) { return new byte[0]; }
		byte[] bytes = new byte[available];
		int position = 0;
		while(position < available)
		{
			int count = stream.read(bytes, position, available - position);
			if(count <= 0) { break; }
			position += count;
		}
		if(position == available) { return bytes; }
		byte[] exact = new byte[position];
		System.arraycopy(bytes, 0, exact, 0, position);
		return exact;
	}
}
