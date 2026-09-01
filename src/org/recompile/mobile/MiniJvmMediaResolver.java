package org.recompile.mobile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Reads derived J2ME media streams without trusting available() or one-shot reads. */
public final class MiniJvmMediaResolver
{
	private MiniJvmMediaResolver() { }

	public static byte[] readBounded(InputStream stream, int maximumBytes) throws IOException
	{
		if(stream == null) { throw new IllegalArgumentException("stream"); }
		if(maximumBytes <= 0) { throw new IllegalArgumentException("maximumBytes"); }
		int available = stream.available();
		if(available > maximumBytes) { throw new IOException("Media stream exceeds limit"); }
		ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(Math.max(available, 32), maximumBytes));
		byte[] buffer = new byte[Math.min(4096, maximumBytes)];
		int count;
		while((count = stream.read(buffer, 0, buffer.length)) >= 0)
		{
			if(count == 0)
			{
				int value = stream.read();
				if(value < 0) { break; }
				if(output.size() == maximumBytes) { throw new IOException("Media stream exceeds limit"); }
				output.write(value);
				continue;
			}
			if(output.size() > maximumBytes - count) { throw new IOException("Media stream exceeds limit"); }
			output.write(buffer, 0, count);
		}
		byte[] data = output.toByteArray();
		if(data.length == 0) { throw new IOException("Media stream is empty"); }
		return data;
	}

	/** Detects Standard MIDI data from bytes, otherwise preserves the original bounded stream. */
	public static byte[] readMediaBounded(InputStream stream, int maximumBytes) throws IOException
	{
		if(stream == null) { throw new IllegalArgumentException("stream"); }
		if(maximumBytes <= 0) { throw new IllegalArgumentException("maximumBytes"); }
		ByteArrayOutputStream prefix = new ByteArrayOutputStream(Math.min(65536, maximumBytes));
		int signature = 0;
		int scanLimit = Math.min(65536, maximumBytes);
		while(prefix.size() < scanLimit)
		{
			int value = stream.read();
			if(value < 0)
			{
				byte[] data = prefix.toByteArray();
				if(data.length == 0) { throw new IOException("Media stream is empty"); }
				return data;
			}
			prefix.write(value);
			signature = signature << 8 | value & 0xff;
			if(prefix.size() >= 4 && signature == 0x4d546864)
			{
				if(maximumBytes < 14) { throw new IOException("Media stream exceeds limit"); }
				byte[] header = new byte[14];
				header[0] = 'M'; header[1] = 'T'; header[2] = 'h'; header[3] = 'd';
				byte[] remainder = readExactly(stream, 10);
				System.arraycopy(remainder, 0, header, 4, remainder.length);
				return readStandardMidi(stream, maximumBytes, header);
			}
		}
		byte[] scanned = prefix.toByteArray();
		return readBoundedWithPrefix(stream, maximumBytes, scanned);
	}

	private static byte[] readStandardMidi(InputStream stream, int maximumBytes, byte[] header) throws IOException
	{
		long headerLength = unsignedInt(header, 4);
		if(headerLength < 6 || headerLength > maximumBytes - 8) { throw new IOException("Invalid MIDI stream"); }
		int trackCount = (header[10] & 0xff) << 8 | header[11] & 0xff;
		if(trackCount <= 0) { throw new IOException("Invalid MIDI stream"); }
		ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 65536));
		output.write(header, 0, header.length);
		copyExactly(stream, output, (int) headerLength - 6, maximumBytes);

		for(int track = 0; track < trackCount; track++)
		{
			byte[] trackHeader = readExactly(stream, 8);
			if(!matches(trackHeader, 0, 'M', 'T', 'r', 'k')) { throw new IOException("Invalid MIDI stream"); }
			long trackLength = unsignedInt(trackHeader, 4);
			if(trackLength > maximumBytes - output.size() - trackHeader.length) {
				throw new IOException("Media stream exceeds limit");
			}
			output.write(trackHeader, 0, trackHeader.length);
			copyExactly(stream, output, (int) trackLength, maximumBytes);
		}
		return output.toByteArray();
	}

	private static byte[] readExactly(InputStream stream, int length) throws IOException
	{
		byte[] bytes = new byte[length];
		int offset = 0;
		while(offset < length)
		{
			int count = stream.read(bytes, offset, length - offset);
			if(count < 0) { throw new IOException("Unexpected end of media stream"); }
			if(count == 0)
			{
				int value = stream.read();
				if(value < 0) { throw new IOException("Unexpected end of media stream"); }
				bytes[offset++] = (byte) value;
			}
			else { offset += count; }
		}
		return bytes;
	}

	private static byte[] readBoundedWithPrefix(InputStream stream, int maximumBytes, byte[] prefix) throws IOException
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 65536));
		output.write(prefix, 0, prefix.length);
		byte[] buffer = new byte[Math.min(4096, maximumBytes)];
		int count;
		while((count = stream.read(buffer, 0, buffer.length)) >= 0)
		{
			if(count == 0)
			{
				int value = stream.read();
				if(value < 0) { break; }
				if(output.size() == maximumBytes) { throw new IOException("Media stream exceeds limit"); }
				output.write(value);
			}
			else
			{
				if(output.size() > maximumBytes - count) { throw new IOException("Media stream exceeds limit"); }
				output.write(buffer, 0, count);
			}
		}
		return output.toByteArray();
	}

	private static void copyExactly(InputStream stream, ByteArrayOutputStream output,
		int length, int maximumBytes) throws IOException
	{
		byte[] buffer = new byte[Math.min(4096, Math.max(1, length))];
		int remaining = length;
		while(remaining > 0)
		{
			int count = stream.read(buffer, 0, Math.min(buffer.length, remaining));
			if(count < 0) { throw new IOException("Unexpected end of media stream"); }
			if(count == 0)
			{
				int value = stream.read();
				if(value < 0) { throw new IOException("Unexpected end of media stream"); }
				if(output.size() == maximumBytes) { throw new IOException("Media stream exceeds limit"); }
				output.write(value);
				remaining--;
			}
			else
			{
				if(output.size() > maximumBytes - count) { throw new IOException("Media stream exceeds limit"); }
				output.write(buffer, 0, count);
				remaining -= count;
			}
		}
	}

	private static boolean matches(byte[] bytes, int offset, int first, int second, int third, int fourth)
	{
		return bytes[offset] == (byte) first && bytes[offset + 1] == (byte) second &&
			bytes[offset + 2] == (byte) third && bytes[offset + 3] == (byte) fourth;
	}

	private static long unsignedInt(byte[] bytes, int offset)
	{
		return (long) (bytes[offset] & 0xff) << 24 |
			(long) (bytes[offset + 1] & 0xff) << 16 |
			(long) (bytes[offset + 2] & 0xff) << 8 |
			(long) (bytes[offset + 3] & 0xff);
	}
}
