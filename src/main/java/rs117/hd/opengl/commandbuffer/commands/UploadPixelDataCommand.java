package rs117.hd.opengl.commandbuffer.commands;

import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
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
	public Semaphore copySema;
	private ByteBuffer mappedBuffer = null;

	@Override
	protected void doWrite() {
		write32(texUnit);
		write32(tex);
		write32(pbo);
		write32(width);
		write32(height);
		writeObject(data);
		writeFlag(copySema != null);
		if(copySema != null) {
			writeObject(copySema);
		}
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
		if(readFlag()) {
			copySema = readObject();
		}
	}

	@Override
	protected void execute() {
		glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo);
		mappedBuffer = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY, mappedBuffer);

		if (mappedBuffer != null) {
			mappedBuffer.asIntBuffer().put(data, 0, width * height);
			if(copySema != null) copySema.release();

			glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
			glActiveTexture(texUnit);
			glBindTexture(GL_TEXTURE_2D, tex);
			glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, 0);
		} else {
			if(copySema != null) copySema.release();
		}
		glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
	}

	@Override
	protected void print(StringBuilder sb) {

	}
}
