package rs117.hd.opengl.commandbuffer.commands;

import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL11.glDepthMask;

public class DepthMaskCommand extends BaseCommand {
	public static boolean SKIP_DEPTH_MASKING;

	public boolean flag;

	@Override
	protected void doWrite() {
		write1(flag ? 1 : 0);
	}

	@Override
	protected void doRead() {
		flag = read1() == 1;
	}

	@Override
	public void execute() {
		if(SKIP_DEPTH_MASKING)
			return;
		glDepthMask(flag);
	}

	@Override
	public void print(StringBuilder sb) {
		sb.append("glDepthMask(");
		sb.append(flag);
		sb.append(");");

		if(SKIP_DEPTH_MASKING)
			sb.append(" - SKIPPED");
	}
}
