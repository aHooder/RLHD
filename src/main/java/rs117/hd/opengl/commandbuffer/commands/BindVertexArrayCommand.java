package rs117.hd.opengl.commandbuffer.commands;

import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL30.glBindVertexArray;

public final class BindVertexArrayCommand extends BaseCommand {
	public int vao;

	public BindVertexArrayCommand() { super(false, true); }

	@Override
	protected void doWrite() {
		write32(vao);
	}

	@Override
	protected void doRead(MemoryStack stack) {
		vao = read32();
	}

	@Override
	public void execute(MemoryStack stack) {
		glBindVertexArray(vao);
	}

	@Override
	public void print(StringBuilder sb) {
		sb.append("glBindVertexArray(");
		sb.append(vao);
		sb.append(");");
	}
}
