package rs117.hd.opengl.commandbuffer.commands;

import java.nio.IntBuffer;
import org.lwjgl.system.MemoryUtil;
import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL14.glMultiDrawArrays;

public class MultiDrawArraysCommand extends BaseCommand {
	public int mode;
	public int[] offsets;
	public int[] counts;

	private IntBuffer offsetsBuffer;
	private IntBuffer countsBuffer;

	public MultiDrawArraysCommand() { super(true); }

	@Override
	public void doWrite() {
		assert offsets.length == counts.length;

		write16(mode);
		write32(offsets.length);
		for(int i = 0; i < offsets.length; i++) {
			write32(offsets[i]);
			write32(counts[i]);
		}

		offsets = null;
		counts = null;
	}

	@Override
	public void doRead() {
		mode = read16();

		int count = read32();
		if(offsetsBuffer == null || offsetsBuffer.capacity() < count) {
			if(offsetsBuffer != null) MemoryUtil.memFree(offsetsBuffer);
			if(countsBuffer != null)  MemoryUtil.memFree(countsBuffer);

			offsetsBuffer = MemoryUtil.memAllocInt(count * 2);
			countsBuffer  = MemoryUtil.memAllocInt(count * 2);
		} else {
			offsetsBuffer.clear();
			countsBuffer.clear();
		}

		for(int i = 0; i < count; i++) {
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
		sb.append("], ");
		for(int i = 0; i < countsBuffer.limit(); i++) {
			if(i > 0) sb.append(", ");
			sb.append(countsBuffer.get(i));
		}
		sb.append(");");
	}
}