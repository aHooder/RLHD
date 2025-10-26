package rs117.hd.opengl.commandbuffer.commands;

import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL30C.glBindFramebuffer;

public final class BindFrameBufferCommand extends BaseCommand {
	public int target;
	public int fbo;

	public BindFrameBufferCommand() { super(false, true); }

	@Override
	protected void doWrite() {
		write32(target);
		write32(fbo);
	}

	@Override
	protected void doRead(MemoryStack stack) {
		target = read32();
		fbo = read32();
	}

	@Override
	protected void execute(MemoryStack stack) { glBindFramebuffer(target, fbo); }

	@Override
	protected void print(StringBuilder sb) {
		sb.append("glBindFramebuffer(");
		sb.append(target);
		sb.append(", ");
		sb.append(fbo);
		sb.append(");");
	}
}
