package rs117.hd.opengl.commandbuffer;

import java.nio.BufferUnderflowException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import rs117.hd.opengl.uniforms.UBOCommandBuffer;

@Slf4j
public abstract class BaseCommand {
	protected int id;

	@Getter
	private final String name;

	@Getter
	private final boolean isDrawCall;

	protected int bitHead;
	protected int wordIndex;
	protected CommandBuffer buffer;
	protected final StringBuilder sb = new StringBuilder();
	protected boolean validate = false;

	protected BaseCommand(boolean isDrawCall) {
		this.name = getClass().getSimpleName();
		this.isDrawCall = isDrawCall;
	}

	protected BaseCommand() { this(false); }

	protected UBOCommandBuffer getUBOCommandBuffer() {
		return buffer.uboCommandBuffer;
	}

	public abstract void execute();
	public abstract void print(StringBuilder sb);

	public final void write(CommandBuffer buffer) {
		this.buffer = buffer;

		buffer.ensureCapacity(buffer.writeHead + 1);
		buffer.cmd[buffer.writeHead] = 0;

		this.wordIndex = buffer.writeHead;
		this.bitHead = 0;

		if(validate) {
			sb.setLength(0);
			sb.append(name);
			sb.append("::write() wordIndex: ");
			sb.append(wordIndex);
			sb.append(" START\n");
		}

		write8(id);
		doWrite();

		if (bitHead > 0) {
			bitHead = 0;
			wordIndex++;
		}

		if(validate) {
			sb.append(name);
			sb.append("::write() wordIndex: ");
			sb.append(wordIndex);
			sb.append(" END\n");
		}

		buffer.writeHead = wordIndex;
	}

	protected abstract void doWrite();

	public final void read(CommandBuffer buffer) {
		this.buffer = buffer;

		this.wordIndex = buffer.readHead;
		this.bitHead = 0;

		if(validate) {
			sb.append(name);
			sb.append("::read() wordIndex: ");
			sb.append(wordIndex);
			sb.append(" START\n");
		}

		assert read8() == id;
		doRead();

		if (bitHead > 0) {
			bitHead = 0;
			wordIndex++;
		}

		if(validate) {
			sb.append(name);
			sb.append("::read() wordIndex: ");
			sb.append(wordIndex);
			sb.append(" END\n");
		}

		buffer.readHead = wordIndex;
	}

	protected abstract void doRead();

	protected void writeBits(long value, int numBits) {
		while (numBits > 0) {
			int bitsLeftInWord = 64 - bitHead;
			int bitsToWrite = Math.min(bitsLeftInWord, numBits);

			long mask = bitsToWrite == 64 ? ~0L : (1L << bitsToWrite) - 1;
			long bits = value & mask;

			if(validate) {
				sb.append(name);
				sb.append("::writeBits(");
				sb.append(value);
				sb.append(", ");
				sb.append(numBits);
				sb.append(") wordIndex: ");
				sb.append(wordIndex);
				sb.append(" bitHead: ");
				sb.append(bitHead);
				sb.append(" bitsToWrite: ");
				sb.append(bitsToWrite);
				sb.append("\n");
			}

			buffer.cmd[wordIndex] |= bits << bitHead;

			bitHead += bitsToWrite;
			if (bitHead == 64) {
				bitHead = 0;
				wordIndex++;
				buffer.ensureCapacity(wordIndex);
				buffer.cmd[wordIndex] = 0;
			}

			value >>>= bitsToWrite;
			numBits -= bitsToWrite;
		}
	}

	protected void write1(int value)   { writeBits(value, 1); }
	protected void write8(int value)   { writeBits(value, 8); }
	protected void write16(int value)  { writeBits(value, 16); }
	protected void write32(int value)  { writeBits(value, 32); }
	protected void write64(long value) { writeBits(value, 64); }

	protected long readBits(int numBits) {
		long result = 0;
		int shift = 0;

		while (numBits > 0) {
			 if (wordIndex >= buffer.cmd.length)
			     throw new BufferUnderflowException();

			long word = buffer.cmd[wordIndex];
			int bitsLeftInWord = 64 - bitHead;
			int bitsToRead = Math.min(bitsLeftInWord, numBits);

			if(validate) {
				sb.append(name);
				sb.append("::readBits(");
				sb.append(numBits);
				sb.append(") wordIndex: ");
				sb.append(wordIndex);
				sb.append(" bitHead: ");
				sb.append(bitHead);
				sb.append(" bitsToWrite: ");
				sb.append(bitsToRead);
				sb.append("\n");
			}

			long mask = bitsToRead == 64 ? ~0L : (1L << bitsToRead) - 1;
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

		if(validate) {
			sb.append(name);
			sb.append("::readBits() Result: ");
			sb.append(result);
			sb.append("\n");
		}

		return result;
	}

	protected int read1()    { return (int) readBits(1); }
	protected int read8()    { return (int) readBits(8); }
	protected int read16()   { return (int) readBits(16); }
	protected int read32()   { return (int) readBits(32); }
	protected long read64()  { return readBits(64); }
}
