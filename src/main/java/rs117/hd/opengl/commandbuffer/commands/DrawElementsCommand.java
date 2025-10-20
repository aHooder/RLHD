package rs117.hd.opengl.commandbuffer.commands;

import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glDrawElements;

public class DrawElementsCommand extends BaseCommand {
	public int mode;
	public int vertexCount;
	public long offset;

	public DrawElementsCommand() { super(true); }

	@Override
	public void doWrite() {
		write16(mode);
		write32(vertexCount);
		write64(offset);
	}

	@Override
	public void doRead() {
		mode = read16();
		vertexCount = read32();
		offset = read64();
	}

	@Override
	public void execute() {
		glDrawElements(mode, vertexCount, GL_UNSIGNED_INT, offset);
	}

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
