package rs117.hd.opengl.commandbuffer.commands;

import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL11.glDepthMask;

public final class DepthMaskCommand extends BaseCommand {
	public static boolean SKIP_DEPTH_MASKING;

	public boolean flag;

	public DepthMaskCommand() { super(false, true); }

	@Override
	protected void doWrite() {
		writeFlag(flag);
	}

	@Override
	protected void doRead() {
		flag = readFlag();
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
