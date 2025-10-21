package rs117.hd.opengl.commandbuffer.commands;

import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glDrawElements;

public final class DrawElementsCommand extends BaseCommand {
	public int mode;
	public int vertexCount;
	public long bytesOffset;

	public DrawElementsCommand() { super(true); }

	@Override
	public void doWrite() {
		write8(mode);
		write32(vertexCount);
		write64(bytesOffset);
	}

	@Override
	public void doRead() {
		mode = read8();
		vertexCount = read32();
		bytesOffset = read64();
	}

	@Override
	public void execute() {
		glDrawElements(mode, vertexCount, GL_UNSIGNED_INT, bytesOffset);
	}

	@Override
	public void print(StringBuilder sb) {
		sb.append("glDrawElements(");
		sb.append(mode);
		sb.append(", ");
		sb.append(vertexCount);
		sb.append(", GL_UNSIGNED_INT, ");
		sb.append(bytesOffset);
		sb.append(");");
	}
}
