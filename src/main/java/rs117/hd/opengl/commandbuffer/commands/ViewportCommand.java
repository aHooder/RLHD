package rs117.hd.opengl.commandbuffer.commands;

import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL11C.glViewport;

public class ViewportCommand extends BaseCommand {
	public int x;
	public int y;
	public int width;
	public int height;

	public ViewportCommand() { super(false, true); }

	@Override
	protected void doWrite() {
		write32(x);
		write32(y);
		write32(width);
		write32(height);
	}

	@Override
	protected void doRead(MemoryStack stack) {
		x = read32();
		y = read32();
		width = read32();
		height = read32();
	}

	@Override
	protected void execute(MemoryStack stack) { glViewport(x, y, width, height); }

	@Override
	protected void print(StringBuilder sb) {
		sb.append("glViewport(");
		sb.append(x);
		sb.append(", ");
		sb.append(y);
		sb.append(", ");
		sb.append(width);
		sb.append(", ");
		sb.append(height);
		sb.append(");");
	}
}
