package rs117.hd.opengl.commandbuffer.commands;

import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL11.glColorMask;

public final class ColorMaskCommand extends BaseCommand {
	public boolean red;
	public boolean green;
	public boolean blue;
	public boolean alpha;

	public ColorMaskCommand() { super(false, true); }

	@Override
	protected void doWrite() {
		writeFlag(red);
		writeFlag(green);
		writeFlag(blue);
		writeFlag(alpha);
	}

	@Override
	protected void doRead(MemoryStack stack) {
		red   = readFlag();
		green = readFlag();
		blue  = readFlag();
		alpha = readFlag();
	}

	@Override
	public void execute(MemoryStack stack) { glColorMask(red, green, blue, alpha); }

	@Override
	public void print(StringBuilder sb) {
		sb.append("glColorMask(");
		sb.append(red);
		sb.append(", ");
		sb.append(green);
		sb.append(", ");
		sb.append(blue);
		sb.append(", ");
		sb.append(alpha);
		sb.append(");");
	}
}
