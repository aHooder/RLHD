package rs117.hd.opengl.commandbuffer.commands;

import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL11C.glDrawArrays;

public final class DrawArraysCommand extends BaseCommand {
	public int mode;
	public int vertexCount;
	public int offset;

	public DrawArraysCommand() { super(true); }

	@Override
	public void doWrite() {
		write8(mode);
		write32(vertexCount);
		write32(offset);
	}

	@Override
	public void doRead() {
		mode = read8();
		vertexCount = read32();
		offset = read32();
	}

	@Override
	public void execute() { glDrawArrays(mode, offset, vertexCount); }

	@Override
	public void print(StringBuilder sb) {
		sb.append("glDrawElements(");
		sb.append(mode);
		sb.append(", ");
		sb.append(vertexCount);
		sb.append(", GL_UNSIGNED_INT, ");
		sb.append(offset);
		sb.append(");");
	}
}