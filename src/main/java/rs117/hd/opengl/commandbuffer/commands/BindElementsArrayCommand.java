package rs117.hd.opengl.commandbuffer.commands;

import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;

public final class BindElementsArrayCommand extends BaseCommand {
	public int ebo;

	public BindElementsArrayCommand() { super(false, true); }

	@Override
	protected void doWrite() {
		write32(ebo);
	}

	@Override
	protected void doRead(MemoryStack stack) {
		ebo = read32();
	}

	@Override
	public void execute(MemoryStack stack) {
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
	}

	@Override
	public void print(StringBuilder sb) {
		sb.append("glBindBuffer(");
		sb.append(ebo);
		sb.append(");");
	}
}
