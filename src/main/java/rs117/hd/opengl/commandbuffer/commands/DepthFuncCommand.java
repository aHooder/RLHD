package rs117.hd.opengl.commandbuffer.commands;

import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL11C.glDepthFunc;

public class DepthFuncCommand extends BaseCommand {

	public int mode;

	public DepthFuncCommand() { super(false, true); }

	@Override
	protected void doWrite() { write32(mode); }

	@Override
	protected void doRead() { mode = read32(); }

	@Override
	protected void execute() { glDepthFunc(mode); }

	@Override
	protected void print(StringBuilder sb) {
		sb.append("glDepthFunc(");
		sb.append(mode);
		sb.append(");");
	}
}
