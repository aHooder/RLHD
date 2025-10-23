package rs117.hd.opengl.commandbuffer.commands;

import java.nio.IntBuffer;
import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL14.glMultiDrawArrays;

public final class MultiDrawArraysCommand extends BaseCommand {
	public int mode;
	public int[] offsets;
	public int[] counts;

	private IntBuffer offsetsBuffer;
	private IntBuffer countsBuffer;

	public MultiDrawArraysCommand() { super(true); }

	@Override
	public void doWrite() {
		assert offsets.length == counts.length;

		write8(mode);
		write32(offsets.length);
		for(int i = 0; i < offsets.length; i++) {
			write32(offsets[i]);
			write32(counts[i]);
		}

		offsets = null;
		counts = null;
	}

	@Override
	public void doRead(MemoryStack stack) {
		mode = read8();

		int length = read32();
		offsetsBuffer = stack.callocInt(length);
		countsBuffer = stack.callocInt(length);

		for(int i = 0; i < length; i++) {
			offsetsBuffer.put(read32());
			countsBuffer.put(read32());
		}

		offsetsBuffer.flip();
		countsBuffer.flip();
	}

	@Override
	public void execute(MemoryStack stack) {
		glMultiDrawArrays(mode, offsetsBuffer, countsBuffer);
		offsetsBuffer = null; // TODO: Kinda need a clear function?
		countsBuffer  = null;
	}

	@Override
	public void print(StringBuilder sb) {
		sb.append("glMultiDrawArrays(");
		sb.append(mode);
		sb.append(", [");
		for(int i = 0; i < offsetsBuffer.limit(); i++) {
			if(i > 0) sb.append(", ");
			sb.append(offsetsBuffer.get(i));
		}
		sb.append("], [");
		for(int i = 0; i < countsBuffer.limit(); i++) {
			if(i > 0) sb.append(", ");
			sb.append(countsBuffer.get(i));
		}
		sb.append("]);");
	}
}