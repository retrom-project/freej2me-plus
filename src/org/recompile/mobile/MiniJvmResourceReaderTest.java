package org.recompile.mobile;

import java.io.IOException;
import java.io.InputStream;

public final class MiniJvmResourceReaderTest
{
	public static void main(String[] args) throws Exception
	{
		ChunkedBoundedStream stream = new ChunkedBoundedStream(new byte[] { 1, 2, 3, 4 });
		byte[] bytes = MiniJvmResourceReader.readAvailable(stream);
		if(bytes.length != 4 || bytes[0] != 1 || bytes[3] != 4)
		{
			throw new AssertionError("resource must be read completely within its advertised boundary");
		}
		if(stream.readCalls != 2)
		{
			throw new AssertionError("reader must not probe the compact VM stream after the boundary");
		}
	}

	private static final class ChunkedBoundedStream extends InputStream
	{
		private final byte[] bytes;
		private int position;
		private int readCalls;

		ChunkedBoundedStream(byte[] bytes) { this.bytes = bytes; }

		public int available() { return bytes.length; }

		public int read() throws IOException
		{
			throw new AssertionError("single-byte EOF probing is not supported by compact VM streams");
		}

		public int read(byte[] target, int offset, int length) throws IOException
		{
			if(position >= bytes.length)
			{
				throw new AssertionError("read called past the advertised resource boundary");
			}
			readCalls++;
			int count = Math.min(2, Math.min(length, bytes.length - position));
			System.arraycopy(bytes, position, target, offset, count);
			position += count;
			return count;
		}
	}
}
