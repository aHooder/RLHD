package rs117.hd.opengl.commandbuffer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.uniforms.UniformBuffer;

@Slf4j
public abstract class BaseCommand {

	protected int id;

	@Getter
	private final String name;

	@Getter
	private final boolean isDrawCall;

	@Getter
	private final boolean isGLCommand;

	protected CommandBuffer owner;

	protected BaseCommand(boolean isDrawCall, boolean isGLCommand) {
		this.name = getClass().getSimpleName();
		this.isDrawCall = isDrawCall;
		this.isGLCommand = isDrawCall || isGLCommand;
	}

	protected BaseCommand(boolean isDrawCall) { this(isDrawCall, false); }

	protected BaseCommand() { this(false, false); }

	protected abstract void execute(MemoryStack stack);
	protected abstract void print(StringBuilder sb);

	protected final void write() {
		write8(id);
		doWrite();
	}

	protected abstract void doWrite();
	protected abstract void doRead(MemoryStack stack);

	protected final void markUniformBufferDirty(UniformBuffer buffer) { owner.markUniformBufferDirty(buffer); }

	protected final void write1(int value)   { owner.writeBits(value & 0x1L, 1); }
	protected final void write8(int value)   { owner.writeBits(value & 0xFFL, 8); }
	protected final void write16(int value)  { owner.writeBits(value & 0xFFFFL, 16); }
	protected final void write32(int value)  { owner.writeBits(value & 0xFFFFFFFFL, 32); }
	protected final void write64(long value) { owner.writeBits(value, 64); }

	protected final int read1()    { return (int) owner.readBits(1); }
	protected final int read8()    { return (int) owner.readBits(8); }
	protected final int read16()   { return (int) owner.readBits(16); }
	protected final int read32()   { return (int) owner.readBits(32); }
	protected final long read64()  { return owner.readBits(64); }

	protected final void writeObject(Object value)  {owner.writeObject(value);}
	protected final <T> T readObject()  { return owner.readObject(); }

	protected final void write32F(float value)  { write32(Float.floatToIntBits(value)); }
	protected final float read32F()  { return Float.intBitsToFloat(read32()); }

	protected final void writeFlag(boolean value) { write1(value ? 1 : 0); }
	protected final boolean readFlag() { return read1() != 0; }
}
