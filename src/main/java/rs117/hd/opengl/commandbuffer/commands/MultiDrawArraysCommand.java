package rs117.hd.opengl.commandbuffer.commands;

import java.nio.IntBuffer;
import org.lwjgl.system.MemoryUtil;
import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL14.glMultiDrawArrays;

public final class MultiDrawArraysCommand extends BaseCommand {
	public int mode;
	public int[] offsets;
	public int[] counts;

	public IntBuffer offsetsBuffer;
	public IntBuffer countsBuffer;

	public MultiDrawArraysCommand() { super(true); }

	@Override
	public void doWrite() {
		assert offsets.length == counts.length;

		write16(mode);
		write32(offsets.length);
		for(int i = 0; i < offsets.length; i++) {
			assert offsets[i] >= 0 : "offset must be >= 0";
			assert counts[i] >= 0 : "vertexCount must be >= 0";
			write32(offsets[i]);
			write32(counts[i]);
		}

		offsets = null;
		counts = null;
	}

	@Override
	public void doRead() {
		mode = read16();

		int length = read32();
		if(offsetsBuffer == null || offsetsBuffer.capacity() < length) {
			if(offsetsBuffer != null) MemoryUtil.memFree(offsetsBuffer);
			if(countsBuffer != null)  MemoryUtil.memFree(countsBuffer);

			offsetsBuffer = MemoryUtil.memAllocInt(length * 2);
			countsBuffer  = MemoryUtil.memAllocInt(length * 2);
		} else {
			offsetsBuffer.clear();
			countsBuffer.clear();
		}

		for(int i = 0; i < length; i++) {
			offsetsBuffer.put(read32());
			countsBuffer.put(read32());
		}

		offsetsBuffer.flip();
		countsBuffer.flip();
	}

	@Override
	public void execute() {

		glMultiDrawArrays(mode, offsetsBuffer, countsBuffer);
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