package rs117.hd.opengl.commandbuffer.commands;

import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBlitFramebuffer;


public class BlitFrameBufferCommand extends BaseCommand {
	public int srcFbo;
	public int resolveFbo; // optional intermediate FBO for MSAA resolve
	public int dstFbo;

	public int srcWidth;
	public int srcHeight;

	public int dstX;
	public int dstY;
	public int dstWidth;
	public int dstHeight;

	public int filter; // GL_NEAREST or GL_LINEAR

	public BlitFrameBufferCommand() { super(false, true); }

	@Override
	protected void execute(MemoryStack stack) {
		glBindFramebuffer(GL_READ_FRAMEBUFFER, srcFbo);

		if (resolveFbo != 0) {
			// Optional MSAA resolve step
			glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resolveFbo);
			glBlitFramebuffer(
				0, 0, srcWidth, srcHeight,
				0, 0, srcWidth, srcHeight,
				GL_COLOR_BUFFER_BIT, filter
			);
			glBindFramebuffer(GL_READ_FRAMEBUFFER, resolveFbo);
		}

		// Blit from (resolved) source to destination framebuffer
		glBindFramebuffer(GL_DRAW_FRAMEBUFFER, dstFbo);
		glBlitFramebuffer(
			0, 0, srcWidth, srcHeight,
			dstX, dstY,
			dstX + dstWidth, dstY + dstHeight,
			GL_COLOR_BUFFER_BIT, filter
		);
	}

	@Override
	protected void doWrite() {
		write32(srcFbo);
		write32(resolveFbo);
		write32(dstFbo);
		write32(srcWidth);
		write32(srcHeight);
		write32(dstX);
		write32(dstY);
		write32(dstWidth);
		write32(dstHeight);
		write32(filter);
	}

	@Override
	protected void doRead(MemoryStack stack) {
		srcFbo = read32();
		resolveFbo = read32();
		dstFbo = read32();
		srcWidth = read32();
		srcHeight = read32();
		dstX = read32();
		dstY = read32();
		dstWidth = read32();
		dstHeight = read32();
		filter = read32();
	}

	@Override
	protected void print(StringBuilder sb) {
		sb.append("BlitFrameBufferCommand(")
			.append("srcFbo=").append(srcFbo).append(", ")
			.append("resolveFbo=").append(resolveFbo).append(", ")
			.append("dstFbo=").append(dstFbo).append(", ")
			.append("srcSize=").append(srcWidth).append("x").append(srcHeight).append(", ")
			.append("dstRect=[").append(dstX).append(", ").append(dstY)
			.append(", ").append(dstWidth).append(", ").append(dstHeight).append("], ")
			.append("filter=").append(filter)
			.append(");");
	}
}
