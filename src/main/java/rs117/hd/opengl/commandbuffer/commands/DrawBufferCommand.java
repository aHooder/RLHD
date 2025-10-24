package rs117.hd.opengl.commandbuffer.commands;

import java.nio.IntBuffer;
import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL20.glDrawBuffers;

public class DrawBufferCommand extends BaseCommand {

	public int[] drawBuffers;

	private IntBuffer stagingDrawBuffers;

	public DrawBufferCommand() { super(false, true); }

	@Override
	protected void doWrite() {
		write32(drawBuffers.length);
		for (int drawBuffer : drawBuffers) {
			write32(drawBuffer);
		}
		drawBuffers = null;
	}

	@Override
	protected void doRead(MemoryStack stack) {
		int count = read32();
		stagingDrawBuffers = stack.mallocInt(count);
		for(int i = 0; i < count; i++) {
			stagingDrawBuffers.put(read32());
		}
		stagingDrawBuffers.flip();
	}

	@Override
	protected void execute(MemoryStack stack) {
		glDrawBuffers(stagingDrawBuffers);
	}

	@Override
	protected void print(StringBuilder sb) {

	}
}
