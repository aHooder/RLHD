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
		assert mode >= 0 : "mode must be >= 0";
		assert vertexCount >= 0 : "vertexCount must be >= 0";
		assert bytesOffset >= 0 : "bytesOffset must be >= 0";

		write16(mode);
		write32(vertexCount);
		write64(bytesOffset);
	}

	@Override
	public void doRead() {
		mode = read16();
		vertexCount = read32();
		bytesOffset = read64();

		assert mode >= 0 : "mode must be >= 0";
		assert vertexCount >= 0 : "vertexCount must be >= 0";
		assert bytesOffset >= 0 : "bytesOffset must be >= 0";
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
