package rs117.hd.opengl.commandbuffer.commands;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
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
	public AtomicBoolean stageData;
	public AtomicBoolean stageComplete;

	private IntBuffer stagingData = null;
	private ByteBuffer mappedBuffer = null;

	@Override
	protected void doWrite() {
		write32(texUnit);
		write32(tex);
		write32(pbo);
		write32(width);
		write32(height);
		writeObject(data);
		if(stageData != null && stageComplete != null) {
			writeFlag(true);
			writeObject(stageData);
			writeObject(stageComplete);
		} else {
			writeFlag(false);
		}
		data = null;
	}

	@Override
	protected void doRead(MemoryStack stack) {
		texUnit = read32();
		tex = read32();
		pbo = read32();
		width = read32();
		height = read32();
		data = readObject();
		if(readFlag()) {
			stageData = readObject();
			stageComplete = readObject();
		} else {
			stageData = null;
			stageComplete = null;
		}
	}

	@Override
	protected void execute(MemoryStack stack) {
		glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo);

		mappedBuffer = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY, (long)(width * height) * Integer.BYTES, mappedBuffer);

		if (mappedBuffer != null) {
			IntBuffer mappedIntBuffer = mappedBuffer.asIntBuffer();

			boolean stagingSupport = stageData != null && stageComplete != null;
			if(stagingSupport && (stagingData == null || stagingData.capacity() < data.length)) {
				stagingData = MemoryUtil.memAllocInt(data.length);
			}

			int chunks = 8;
			int remaining = width * height;
			int chunkSize = remaining / chunks;
			int offset = 0;
			for(int i = 0; i < chunks; i++) {
				if (stagingSupport && stageData.get()) {
					stagingData.put(data, offset, remaining);
					stageComplete.set(true);

					stagingData.flip();
					mappedIntBuffer.put(stagingData);
					stagingData.clear();
					break;
				} else {
					mappedIntBuffer.put(data, offset, chunkSize);
					offset += chunkSize;
					remaining -= chunkSize;
				}
			}

			if (stagingSupport) {
				stageComplete.set(true);
			}

			glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
			glActiveTexture(texUnit);
			glBindTexture(GL_TEXTURE_2D, tex);
			glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, 0);
		}
		glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
	}

	@Override
	protected void print(StringBuilder sb) {}
}
