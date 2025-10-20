package rs117.hd.opengl.commandbuffer;

import lombok.Getter;
import rs117.hd.opengl.uniforms.UBOCommandBuffer;

public abstract class BaseCommand {
	protected int id;
	@Getter
	private final boolean isDrawCall;

	protected int bitHead;
	protected int wordIndex;
	protected CommandBuffer buffer;

	protected BaseCommand() {
		this.isDrawCall = false;
	}

	protected BaseCommand(boolean isDrawCall) {
		this.isDrawCall = isDrawCall;
	}

	protected UBOCommandBuffer getUBOCommandBuffer() { return buffer.uboCommandBuffer; }

	public abstract void execute();
	public abstract void print(StringBuilder sb);

	public final void write(CommandBuffer buffer) {
		this.buffer = buffer;

		buffer.ensureCapacity(1);
		buffer.cmd[buffer.writeHead++] = 0;

		this.wordIndex = buffer.writeHead - 1;
		this.bitHead = 0;

		write8(id);
		doWrite();

		// Advance writeHead if needed
		if (bitHead > 0)
			wordIndex++;

		buffer.writeHead = wordIndex;
	}

	protected abstract void doWrite();

	public final void read(CommandBuffer buffer) {
		this.buffer = buffer;
		this.wordIndex = buffer.readHead;
		this.bitHead = 0;

		read8(); // Skip over the command id
		doRead();

		if (bitHead > 0)
			wordIndex++;

		buffer.readHead = wordIndex;
	}

	protected abstract void doRead();

	protected void writeBits(long value, int numBits) {
		while (numBits > 0) {
			buffer.ensureCapacity(wordIndex + 1);
			int bitsLeftInWord = 64 - bitHead;
			int bitsToWrite = Math.min(bitsLeftInWord, numBits);

			long mask = (1L << bitsToWrite) - 1;
			long bits = value & mask;

			buffer.cmd[wordIndex] |= bits << bitHead;

			bitHead += bitsToWrite;
			if (bitHead == 64) {
				bitHead = 0;
				wordIndex++;
			}

			value >>>= bitsToWrite;
			numBits -= bitsToWrite;
		}
	}

	protected void write1(int value)  { writeBits(value, 1); }
	protected void write8(int value)  { writeBits(value, 8); }
	protected void write16(int value) { writeBits(value, 16); }
	protected void write32(int value) { writeBits(value, 32); }
	protected void write64(long value){ writeBits(value, 64); }

	protected long readBits(int numBits) {
		long result = 0;
		int shift = 0;

		while (numBits > 0) {
			long word = buffer.cmd[wordIndex];
			int bitsLeftInWord = 64 - bitHead;
			int bitsToRead = Math.min(bitsLeftInWord, numBits);

			long mask = (1L << bitsToRead) - 1;
			long bits = (word >>> bitHead) & mask;

			result |= bits << shift;

			bitHead += bitsToRead;
			if (bitHead == 64) {
				bitHead = 0;
				wordIndex++;
			}

			shift += bitsToRead;
			numBits -= bitsToRead;
		}
		return result;
	}

	protected int read1()  { return (int) readBits(1); }
	protected int read8()  { return (int) readBits(8); }
	protected int read16() { return (int) readBits(16); }
	protected int read32() { return (int) readBits(32); }
	protected long read64() { return readBits(64); }
}
