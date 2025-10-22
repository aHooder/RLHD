package rs117.hd.opengl.commandbuffer.commands;

import java.nio.ByteBuffer;
import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glTexSubImage2D;
import static org.lwjgl.opengl.GL12C.GL_BGRA;
import static org.lwjgl.opengl.GL12C.GL_UNSIGNED_INT_8_8_8_8_REV;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL15C.GL_WRITE_ONLY;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glMapBuffer;
import static org.lwjgl.opengl.GL15C.glUnmapBuffer;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_UNPACK_BUFFER;

public class UploadPixelDataCommand extends BaseCommand {

	public int texUnit;
	public int tex;
	public int pbo;
	public int width;
	public int height;
	public int[] data;

	@Override
	protected void doWrite() {
		write32(texUnit);
		write32(tex);
		write32(pbo);
		write32(width);
		write32(height);
		writeObject(data);
		data = null;
	}

	@Override
	protected void doRead() {
		texUnit = read32();
		tex = read32();
		pbo = read32();
		width = read32();
		height = read32();
		data = readObject();
	}

	@Override
	protected void execute() {
		glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo);
		ByteBuffer mappedBuffer = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY);

		if (mappedBuffer != null) {
			mappedBuffer.asIntBuffer().put(data, 0, width * height);
			data = null;

			glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
			glActiveTexture(texUnit);
			glBindTexture(GL_TEXTURE_2D, tex);
			glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, 0);
		}
		glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
	}

	@Override
	protected void print(StringBuilder sb) {

	}
}
