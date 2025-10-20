package rs117.hd.opengl.commandbuffer.commands;

import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL30.glBindVertexArray;

public class BindVertexArrayCommand extends BaseCommand {
	public int vao;

	@Override
	protected void doWrite() {
		write32(vao);
	}

	@Override
	protected void doRead() {
		vao = read32();
	}

	@Override
	public void execute() {
		glBindVertexArray(vao);
	}

	@Override
	public void print(StringBuilder sb) {
		sb.append("glBindVertexArray(");
		sb.append(vao);
		sb.append(");");
	}
}
