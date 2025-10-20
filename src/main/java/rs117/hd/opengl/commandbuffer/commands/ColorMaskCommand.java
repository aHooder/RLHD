package rs117.hd.opengl.commandbuffer.commands;

import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL11.glColorMask;

public class ColorMaskCommand extends BaseCommand {
	public boolean red;
	public boolean green;
	public boolean blue;
	public boolean alpha;


	@Override
	protected void doWrite() {
		write1(red   ? 1 : 0);
		write1(green ? 1 : 0);
		write1(blue  ? 1 : 0);
		write1(alpha ? 1 : 0);
	}

	@Override
	protected void doRead() {
		red   = read1() == 1;
		green = read1() == 1;
		blue  = read1() == 1;
		alpha = read1() == 1;
	}

	@Override
	public void execute() {
		glColorMask(red, green, blue, alpha);
	}

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
