package rs117.hd.utils;

import java.nio.IntBuffer;
import java.util.Arrays;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.uniforms.UBOCommandBuffer;

import static org.lwjgl.opengl.GL33C.*;

@Slf4j
public class CommandBuffer {
	public static boolean SKIP_DEPTH_MASKING;

	private static final int GL_BIND_VERTEX_ARRAY_TYPE = 0;
	private static final int GL_BIND_ELEMENTS_ARRAY_TYPE = 1;
	private static final int GL_MULTI_DRAW_ARRAYS_TYPE = 2;
	private static final int GL_DRAW_ARRAYS_TYPE = 3;
	private static final int GL_DRAW_ELEMENTS_TYPE = 4;

	private static final int GL_DEPTH_MASK_TYPE = 5;
	private static final int GL_COLOR_MASK_TYPE = 6;

	private static final int UNIFORM_BASE_OFFSET = 7;
	private static final int UNIFORM_WORLD_VIEW_ID = 8;

	private static final int GL_TOGGLE_TYPE = 9; // Combined glEnable & glDisable

	private static final int INT_MASK = 0xFFFF_FFFF;
	private static final int DRAW_MODE_MASK = 0xF;

	@Setter
	private UBOCommandBuffer uboCommandBuffer;

	private long[] cmd = new long[1 << 20]; // ~1 million calls
	private int writeHead = 0;
	private int readHead = 0;

	private void ensureCapacity(int numLongs) {
		if (writeHead + numLongs >= cmd.length)
			cmd = Arrays.copyOf(cmd, cmd.length * 2);
	}

	public void SetBaseOffset(int x, int y, int z) {
		ensureCapacity(2);
		cmd[writeHead++] = UNIFORM_BASE_OFFSET & 0xFF | (long) x << 32;
		cmd[writeHead++] = (long) y << 32 | z & INT_MASK;
	}

	public void SetWorldViewIndex(int index) {
		ensureCapacity(1);
		cmd[writeHead++] = UNIFORM_WORLD_VIEW_ID & 0xFF | (long) index << 8;
	}

	public void BindVertexArray(int vao) {
		ensureCapacity(1);
		cmd[writeHead++] = GL_BIND_VERTEX_ARRAY_TYPE & 0xFF | (long) vao << 8;
	}

	public void BindElementsArray(int ebo) {
		ensureCapacity(1);
		cmd[writeHead++] = GL_BIND_ELEMENTS_ARRAY_TYPE & 0xFF | (long) ebo << 8;
	}

	public void DepthMask(boolean writeDepth) {
		ensureCapacity(1);
		cmd[writeHead++] = GL_DEPTH_MASK_TYPE & 0xFF | (writeDepth ? 1 : 0) << 8;
	}

	public void ColorMask(boolean writeRed, boolean writeGreen, boolean writeBlue, boolean writeAlpha) {
		ensureCapacity(1);
		cmd[writeHead++] =
			GL_COLOR_MASK_TYPE & 0xFF |
			(writeRed ? 1 : 0) << 8 |
			(writeGreen ? 1 : 0) << 9 |
			(writeBlue ? 1 : 0) << 10 |
			(writeAlpha ? 1 : 0) << 11;
	}

	public void MultiDrawArrays(int mode, int[] offsets, int[] counts) {
		assert offsets.length == counts.length;
		assert (mode & DRAW_MODE_MASK) == mode;
		if (offsets.length == 0)
			return;

		ensureCapacity(1 + offsets.length);
		cmd[writeHead++] = GL_MULTI_DRAW_ARRAYS_TYPE & 0xFF | mode << 8 | (long) offsets.length << 32;
		for (int i = 0; i < offsets.length; i++)
			cmd[writeHead++] = (long) offsets[i] << 32 | counts[i] & INT_MASK;
	}

	public void DrawElements(int mode, int vertexCount, long offset) {
		assert (mode & DRAW_MODE_MASK) == mode;
		ensureCapacity(2);
		cmd[writeHead++] = GL_DRAW_ELEMENTS_TYPE & 0xFF | mode << 8 | (long) vertexCount << 32;
		cmd[writeHead++] = offset;
	}

	public void DrawArrays(int mode, int offset, int vertexCount) {
		assert (mode & DRAW_MODE_MASK) == mode;
		ensureCapacity(2);
		cmd[writeHead++] = GL_DRAW_ARRAYS_TYPE & 0xFF | mode << 8;
		cmd[writeHead++] = (long) offset << 32 | vertexCount & INT_MASK;
	}

	public void Enable(int capability) {
		Toggle(capability, true);
	}

	public void Disable(int capability) {
		Toggle(capability, false);
	}

	public void Toggle(int capability, boolean enabled) {
		ensureCapacity(2);
		cmd[writeHead++] = GL_TOGGLE_TYPE;
		cmd[writeHead++] = (enabled ? 1L : 0) << 32 | capability & INT_MASK;
	}

	public void execute() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer offsets = null, counts = null;
			readHead = 0;
			while (readHead < writeHead) {
				// Casting from long to int keeps the lower 32 bits
				long data = cmd[readHead++];
				int type = (int) data & 0xFF;
				switch (type) {
					case UNIFORM_BASE_OFFSET: {
						long packed = cmd[readHead++];
						int x = (int) (data >> 32);
						int y = (int) (packed >> 32);
						int z = (int) packed;
						if (uboCommandBuffer != null)
							uboCommandBuffer.sceneBase.set(x, y, z);
						break;
					}
					case UNIFORM_WORLD_VIEW_ID: {
						int id = (int) (data >> 8);
						if (uboCommandBuffer != null)
							uboCommandBuffer.worldViewIndex.set(id);
						break;
					}
					case GL_DEPTH_MASK_TYPE: {
						int state = (int) (data >> 8) & 1;
						if (SKIP_DEPTH_MASKING)
							continue;
						glDepthMask(state == 1);
						break;
					}
					case GL_COLOR_MASK_TYPE: {
						boolean red = ((data >> 8) & 1) == 1;
						boolean green = ((data >> 9) & 1) == 1;
						boolean blue = ((data >> 10) & 1) == 1;
						boolean alpha = ((data >> 11) & 1) == 1;
						glColorMask(red, green, blue, alpha);
						break;
					}
					case GL_BIND_VERTEX_ARRAY_TYPE: {
						glBindVertexArray((int) (data >> 8));
						break;
					}
					case GL_BIND_ELEMENTS_ARRAY_TYPE: {
						glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, (int) (data >> 8));
						break;
					}
					case GL_DRAW_ARRAYS_TYPE: {
						long packed = cmd[readHead++];

						int mode = (int) data >> 8;
						int offset = (int) (packed >> 32);
						int count = (int) packed;

						if (uboCommandBuffer != null && uboCommandBuffer.isDirty())
							uboCommandBuffer.upload();

						glDrawArrays(mode, offset, count);
						break;
					}
					case GL_DRAW_ELEMENTS_TYPE: {
						int mode = (int) data >> 8;
						int vertexCount = (int) (data >> 32);
						long byteOffset = cmd[readHead++];

						if (uboCommandBuffer != null && uboCommandBuffer.isDirty())
							uboCommandBuffer.upload();

						glDrawElements(mode, vertexCount, GL_UNSIGNED_INT, byteOffset);
						break;
					}
					case GL_MULTI_DRAW_ARRAYS_TYPE: {
						int mode = (int) data >> 8;
						int drawCount = (int) (data >> 32);

						if (offsets == null || offsets.capacity() < drawCount) {
							offsets = stack.callocInt(drawCount);
							counts = stack.callocInt(drawCount);
						}

						for (int i = 0; i < drawCount; i++) {
							long packed = cmd[readHead++];
							offsets.put((int) (packed >> 32));
							counts.put((int) packed);
						}

						offsets.flip();
						counts.flip();

						if (uboCommandBuffer != null && uboCommandBuffer.isDirty())
							uboCommandBuffer.upload();

						glMultiDrawArrays(mode, offsets, counts);

						offsets.clear();
						counts.clear();
						break;
					}
					case GL_TOGGLE_TYPE: {
						long packed = cmd[readHead++];
						int capability = (int) packed;
						if ((packed >> 32) != 0) {
							glEnable(capability);
						} else {
							glDisable(capability);
						}
						break;
					}
					default:
						throw new IllegalArgumentException("Encountered an unknown DrawCall type: " + type);
				}
			}
		}
	}

	public void reset() {
		writeHead = 0;
	}
}
