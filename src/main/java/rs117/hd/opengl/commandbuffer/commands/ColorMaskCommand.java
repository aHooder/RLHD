package rs117.hd.opengl.commandbuffer.commands;

import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL11.glColorMask;

public final class ColorMaskCommand extends BaseCommand {
	public boolean red;
	public boolean green;
	public boolean blue;
	public boolean alpha;

	@Override
	protected void doWrite() {
		writeFlag(red);
		writeFlag(green);
		writeFlag(blue);
		writeFlag(alpha);
	}

	@Override
	protected void doRead() {
		red   = readFlag();
		green = readFlag();
		blue  = readFlag();
		alpha = readFlag();
	}

	@Override
	public void execute() { glColorMask(red, green, blue, alpha); }

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
