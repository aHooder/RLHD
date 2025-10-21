package rs117.hd.opengl.commandbuffer.commands;

import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL14C.glBlendFuncSeparate;

public class BlendFuncCommand extends BaseCommand  {
	public int sfactorRGB;
	public int dfactorRGB;
	public int sfactorAlpha;
	public int dfactorAlpha;

	public BlendFuncCommand() { super(false, true); }

	@Override
	protected void doWrite() {
		write32(sfactorRGB);
		write32(dfactorRGB);
		write32(sfactorAlpha);
		write32(dfactorAlpha);
	}

	@Override
	protected void doRead() {
		sfactorRGB = read32();
		dfactorRGB = read32();
		sfactorAlpha = read32();
		dfactorAlpha = read32();
	}

	@Override
	protected void execute() {
		glBlendFuncSeparate(sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha);
	}

	@Override
	protected void print(StringBuilder sb) {
		sb.append("glBlendFuncSeparate(");
		sb.append(sfactorRGB);
		sb.append(", ");
		sb.append(dfactorRGB);
		sb.append(", ");
		sb.append(sfactorAlpha);
		sb.append(", ");
		sb.append(dfactorAlpha);
		sb.append(");");
	}
}
