package rs117.hd.opengl.commandbuffer.commands;

import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.glClearDepth;

public class ClearCommand extends BaseCommand {
	public boolean clearColor;
	public boolean clearDepth;

	public float depth;

	public float red;
	public float green;
	public float blue;
	public float alpha;

	public ClearCommand() { super(false, true); }

	@Override
	protected void doWrite() {
		writeFlag(clearColor);
		if(clearColor) {
			write32F(red);
			write32F(green);
			write32F(blue);
			write32F(alpha);
		}

		writeFlag(clearDepth);
		if(clearDepth) {
			write32F(depth);
		}
	}

	@Override
	protected void doRead() {
		clearColor = readFlag();

		if(clearColor) {
			red = read32F();
			green = read32F();
			blue = read32F();
			alpha = read32F();
		}

		clearDepth = readFlag();
		if(clearDepth) {
			depth = read32F();
		}
	}

	@Override
	protected void execute() {
		if(clearColor) {
			glClearColor(red, green, blue, alpha);
		}
		if(clearDepth) {
			glClearDepth(depth);
		}
		glClear((clearColor ? GL_COLOR_BUFFER_BIT : 0) | (clearDepth ? GL_DEPTH_BUFFER_BIT : 0));
	}

	@Override
	protected void print(StringBuilder sb) {
		if(clearColor) {
			sb.append("glClearColor(");
			sb.append(red);
			sb.append(", ");
			sb.append(green);
			sb.append(", ");
			sb.append(blue);
			sb.append(", ");
			sb.append(alpha);
			sb.append(");");
		}

		if(clearDepth) {
			sb.append("glClearDepth(");
			sb.append(depth);
			sb.append(");");
		}

		sb.append("glClear(");
		sb.append((clearColor ? GL_COLOR_BUFFER_BIT : 0) | (clearDepth ? GL_DEPTH_BUFFER_BIT : 0));
		sb.append(");");
	}
}
