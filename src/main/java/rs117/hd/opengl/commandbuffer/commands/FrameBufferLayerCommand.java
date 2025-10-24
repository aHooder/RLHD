package rs117.hd.opengl.commandbuffer.commands;

import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.glFramebufferTextureLayer;

public class FrameBufferLayerCommand extends BaseCommand {

	public int attachment;
	public int texId;
	public int level;
	public int layer;

	public FrameBufferLayerCommand() {super(false, true);}

	@Override
	protected void doWrite() {
		write32(attachment);
		write32(texId);
		write32(level);
		write32(layer);
	}

	@Override
	protected void doRead(MemoryStack stack) {
		attachment = read32();
		texId = read32();
		level = read32();
		layer = read32();
	}

	@Override
	protected void execute(MemoryStack stack) {
		glFramebufferTextureLayer(GL_FRAMEBUFFER, attachment, texId, level, layer);
	}

	@Override
	protected void print(StringBuilder sb) {
		sb.append("glFramebufferTextureLayer(GL_FRAMEBUFFER, ");
		sb.append(attachment);
		sb.append(", ");
		sb.append(texId);
		sb.append(", ");
		sb.append(level);
		sb.append(", ");
		sb.append(layer);
		sb.append(");");
	}
}
