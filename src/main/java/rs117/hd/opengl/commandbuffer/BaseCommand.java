package rs117.hd.opengl.commandbuffer;

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

	protected CommandBuffer buffer;

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
		write8(id);
		doWrite();
	}

	protected abstract void doWrite();

	public final void read(CommandBuffer buffer) {
		this.buffer = buffer;
		doRead();
	}

	protected abstract void doRead();

	protected final void write1(int value)   { buffer.writeBits(value & 0x1L, 1); }
	protected final void write8(int value)   { buffer.writeBits(value & 0xFFL, 8); }
	protected final void write16(int value)  { buffer.writeBits(value & 0xFFFFL, 16); }
	protected final void write32(int value)  { buffer.writeBits(value & 0xFFFFFFFFL, 32); }
	protected final void write64(long value) { buffer.writeBits(value, 64); }

	protected final int read1()    { return (int) buffer.readBits(1); }
	protected final int read8()    { return (int) buffer.readBits(8); }
	protected final int read16()   { return (int) buffer.readBits(16); }
	protected final int read32()   { return (int) buffer.readBits(32); }
	protected final long read64()  { return buffer.readBits(64); }

	protected final void writeFlag(boolean value) { write1(value ? 1 : 0); }
	protected final boolean readFlag() { return read1() != 0; }
}
