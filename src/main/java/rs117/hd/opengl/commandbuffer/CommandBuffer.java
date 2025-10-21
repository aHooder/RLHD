package rs117.hd.opengl.commandbuffer;

import java.util.Arrays;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import rs117.hd.HdPlugin;
import rs117.hd.opengl.commandbuffer.commands.BindElementsArrayCommand;
import rs117.hd.opengl.commandbuffer.commands.BindFrameBufferCommand;
import rs117.hd.opengl.commandbuffer.commands.BindVertexArrayCommand;
import rs117.hd.opengl.commandbuffer.commands.BlendFuncCommand;
import rs117.hd.opengl.commandbuffer.commands.BlitFrameBufferCommand;
import rs117.hd.opengl.commandbuffer.commands.ClearCommand;
import rs117.hd.opengl.commandbuffer.commands.ColorMaskCommand;
import rs117.hd.opengl.commandbuffer.commands.DepthFuncCommand;
import rs117.hd.opengl.commandbuffer.commands.DepthMaskCommand;
import rs117.hd.opengl.commandbuffer.commands.DrawArraysCommand;
import rs117.hd.opengl.commandbuffer.commands.DrawElementsCommand;
import rs117.hd.opengl.commandbuffer.commands.ExecuteCommandBufferCommand;
import rs117.hd.opengl.commandbuffer.commands.MultiDrawArraysCommand;
import rs117.hd.opengl.commandbuffer.commands.SetUniformBufferPropertyCommand;
import rs117.hd.opengl.commandbuffer.commands.ShaderProgramCommand;
import rs117.hd.opengl.commandbuffer.commands.ToggleCommand;
import rs117.hd.opengl.commandbuffer.commands.ViewportCommand;
import rs117.hd.opengl.shader.ShaderProgram;
import rs117.hd.opengl.uniforms.UBOCommandBuffer;
import rs117.hd.opengl.uniforms.UniformBuffer;

@Slf4j
public final class CommandBuffer {
	private static final int BITS_PER_WORD = 64;
	private static final long[] MASKS = new long[65];

	static {
		MASKS[0] = 0L;
		for (int i = 1; i < BITS_PER_WORD; i++)  {
			MASKS[i] = (1L << i) - 1L;
		}
		MASKS[BITS_PER_WORD] = ~0L;
	}

	private int COMMAND_COUNT = 0;
	private final BaseCommand[] REGISTERED_COMMANDS = {
		(DRAW_ARRAYS_COMMAND = REGISTER_COMMAND(DrawArraysCommand::new)),
		(DRAW_ELEMENTS_COMMAND = REGISTER_COMMAND(DrawElementsCommand::new)),
		(MULTI_DRAW_ARRAYS_COMMAND = REGISTER_COMMAND(MultiDrawArraysCommand::new)),
		(BIND_FRAMEBUFFER_COMMAND = REGISTER_COMMAND(BindFrameBufferCommand::new)),
		(VIEWPORT_COMMAND = REGISTER_COMMAND(ViewportCommand::new)),
		(CLEAR_COMMAND = REGISTER_COMMAND(ClearCommand::new)),
		(TOGGLE_COMMAND = REGISTER_COMMAND(ToggleCommand::new)),
		(SHADER_PROGRAM_COMMAND = REGISTER_COMMAND(ShaderProgramCommand::new)),
		(COLOR_MASK_COMMAND = REGISTER_COMMAND(ColorMaskCommand::new)),
		(DEPTH_FUNC_COMMAND = REGISTER_COMMAND(DepthFuncCommand::new)),
		(DEPTH_MASK_COMMAND = REGISTER_COMMAND(DepthMaskCommand::new)),
		(BLENDED_FUNC_COMMAND = REGISTER_COMMAND(BlendFuncCommand::new)),
		(BIND_ELEMENTS_ARRAY_COMMAND = REGISTER_COMMAND(BindElementsArrayCommand::new)),
		(BIND_VERTEX_ARRAY_COMMAND = REGISTER_COMMAND(BindVertexArrayCommand::new)),
		(BLIT_FRAME_BUFFER_COMMAND = REGISTER_COMMAND(BlitFrameBufferCommand::new)),
		(SET_UNIFORM_BUFFER_PROPERTY_COMMAND = REGISTER_COMMAND(SetUniformBufferPropertyCommand::new)),
		(EXECUTE_COMMAND_BUFFER_COMMAND = REGISTER_COMMAND(ExecuteCommandBufferCommand::new)),
	};

	private final DrawArraysCommand DRAW_ARRAYS_COMMAND;
	private final DrawElementsCommand DRAW_ELEMENTS_COMMAND;
	private final MultiDrawArraysCommand MULTI_DRAW_ARRAYS_COMMAND;
	private final ToggleCommand TOGGLE_COMMAND;
	private final BindFrameBufferCommand BIND_FRAMEBUFFER_COMMAND;
	private final ViewportCommand VIEWPORT_COMMAND;
	private final ClearCommand CLEAR_COMMAND;
	private final ColorMaskCommand COLOR_MASK_COMMAND;
	private final DepthFuncCommand DEPTH_FUNC_COMMAND;
	private final DepthMaskCommand DEPTH_MASK_COMMAND;
	private final BlendFuncCommand BLENDED_FUNC_COMMAND;
	private final BindElementsArrayCommand BIND_ELEMENTS_ARRAY_COMMAND;
	private final BindVertexArrayCommand BIND_VERTEX_ARRAY_COMMAND;
	private final BlitFrameBufferCommand BLIT_FRAME_BUFFER_COMMAND;
	private final SetUniformBufferPropertyCommand SET_UNIFORM_BUFFER_PROPERTY_COMMAND;
	private final ShaderProgramCommand SHADER_PROGRAM_COMMAND;
	private final ExecuteCommandBufferCommand EXECUTE_COMMAND_BUFFER_COMMAND;

	interface ICreateCommand<T extends BaseCommand> { T construct(); }

	private <T extends BaseCommand> T REGISTER_COMMAND(ICreateCommand<T> createCommand) {
		T newCommand = createCommand.construct();
		newCommand.id = COMMAND_COUNT;
		newCommand.owner = this;
		COMMAND_COUNT++;
		return newCommand;
	}

	public UBOCommandBuffer uboCommandBuffer;

	private long[] cmd = new long[1 << 20]; // ~1 million calls

	private Object[] objects = new Object[100];
	private int objectCount = 0;

	private int writeHead = 0;
	private int writeBitHead = 0;

	private int readHead = 0;
	private int readBitHead = 0;

	private final StringBuilder cmd_log = new StringBuilder();

	public void ensureCapacity(int numLongs) {
		if (writeHead + numLongs >= cmd.length)
			cmd = Arrays.copyOf(cmd, cmd.length * 2);
	}

	public void SetUniformProperty(UniformBuffer.Property property, boolean upload, int... values) {
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.property = property;
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.upload = upload;
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.intValues = values;
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.isFloat = false;
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.write();
	}

	public void SetUniformProperty(UniformBuffer.Property property, boolean upload, float... values) {
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.property = property;
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.upload = upload;
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.floatValues = values;
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.isFloat = true;
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.write();
	}

	public void BindVertexArray(int vao) {
		BIND_VERTEX_ARRAY_COMMAND.vao = vao;
		BIND_VERTEX_ARRAY_COMMAND.write();
	}

	public void BindElementsArray(int ebo) {
		BIND_ELEMENTS_ARRAY_COMMAND.ebo = ebo;
		BIND_ELEMENTS_ARRAY_COMMAND.write();
	}

	public void SetDepthFunc(int DepthFunc) {
		DEPTH_FUNC_COMMAND.mode = DepthFunc;
		DEPTH_FUNC_COMMAND.write();
	}

	public void ExecuteCommandBuffer(CommandBuffer cmd) {
		assert cmd != this;
		EXECUTE_COMMAND_BUFFER_COMMAND.cmd = cmd;
		EXECUTE_COMMAND_BUFFER_COMMAND.write();
	}

	public void SetShaderProgram(ShaderProgram program) {
		assert program != null;
		SHADER_PROGRAM_COMMAND.program = program;
		SHADER_PROGRAM_COMMAND.write();
	}

	public void ClearDepth(float depth) {
		CLEAR_COMMAND.clearColor = false;
		CLEAR_COMMAND.clearDepth = true;
		CLEAR_COMMAND.depth = depth;
		CLEAR_COMMAND.write();
	}

	public void ClearColor(float red, float green, float blue, float alpha) {
		CLEAR_COMMAND.clearColor = true;
		CLEAR_COMMAND.clearDepth = false;
		CLEAR_COMMAND.red = red;
		CLEAR_COMMAND.green = green;
		CLEAR_COMMAND.blue = blue;
		CLEAR_COMMAND.alpha = alpha;
		CLEAR_COMMAND.write();
	}

	public void ClearColorAndDepth(float red, float green, float blue, float alpha, float depth) {
		CLEAR_COMMAND.clearColor = true;
		CLEAR_COMMAND.clearDepth = true;
		CLEAR_COMMAND.depth = depth;
		CLEAR_COMMAND.red = red;
		CLEAR_COMMAND.green = green;
		CLEAR_COMMAND.blue = blue;
		CLEAR_COMMAND.alpha = alpha;
		CLEAR_COMMAND.write();
	}

	public void DepthMask(boolean writeDepth) {
		DEPTH_MASK_COMMAND.flag = writeDepth;
		DEPTH_MASK_COMMAND.write();
	}

	public void BlendFunc(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha) {
		BLENDED_FUNC_COMMAND.sfactorRGB = sfactorRGB;
		BLENDED_FUNC_COMMAND.dfactorRGB = dfactorRGB;
		BLENDED_FUNC_COMMAND.sfactorAlpha = sfactorAlpha;
		BLENDED_FUNC_COMMAND.dfactorAlpha = dfactorAlpha;
		BLENDED_FUNC_COMMAND.write();
	}

	public void ColorMask(boolean writeRed, boolean writeGreen, boolean writeBlue, boolean writeAlpha) {
		COLOR_MASK_COMMAND.red = writeRed;
		COLOR_MASK_COMMAND.green = writeGreen;
		COLOR_MASK_COMMAND.blue = writeBlue;
		COLOR_MASK_COMMAND.alpha = writeAlpha;
		COLOR_MASK_COMMAND.write();
	}

	public void MultiDrawArrays(int mode, int[] offsets, int[] counts) {
		MULTI_DRAW_ARRAYS_COMMAND.mode = mode;
		MULTI_DRAW_ARRAYS_COMMAND.offsets = offsets;
		MULTI_DRAW_ARRAYS_COMMAND.counts = counts;
		MULTI_DRAW_ARRAYS_COMMAND.write();
	}

	public void DrawElements(int mode, int vertexCount, long bytesOffset) {
		DRAW_ELEMENTS_COMMAND.mode = mode;
		DRAW_ELEMENTS_COMMAND.vertexCount = vertexCount;
		DRAW_ELEMENTS_COMMAND.bytesOffset = bytesOffset;
		DRAW_ELEMENTS_COMMAND.write();
	}

	public void DrawArrays(int mode, int offset, int vertexCount) {
		DRAW_ARRAYS_COMMAND.mode = mode;
		DRAW_ARRAYS_COMMAND.offset = offset;
		DRAW_ARRAYS_COMMAND.vertexCount = vertexCount;
		DRAW_ARRAYS_COMMAND.write();
	}

	public void BindFrameBuffer(int target, int fbo) {
		BIND_FRAMEBUFFER_COMMAND.target = target;
		BIND_FRAMEBUFFER_COMMAND.fbo = fbo;
		BIND_FRAMEBUFFER_COMMAND.write();
	}

	public void Viewport(int x, int y, int width, int height) {
		VIEWPORT_COMMAND.x = x;
		VIEWPORT_COMMAND.y = y;
		VIEWPORT_COMMAND.width = width;
		VIEWPORT_COMMAND.height = height;
		VIEWPORT_COMMAND.write();
	}

	public void BlitFramebuffer(
		int srcFbo,
		int resolveFbo,
		int dstFbo,
		int srcWidth,
		int srcHeight,
		int dstX,
		int dstY,
		int dstWidth,
		int dstHeight,
		int filter
	) {
		BLIT_FRAME_BUFFER_COMMAND.srcFbo = srcFbo;
		BLIT_FRAME_BUFFER_COMMAND.resolveFbo = resolveFbo;
		BLIT_FRAME_BUFFER_COMMAND.dstFbo = dstFbo;
		BLIT_FRAME_BUFFER_COMMAND.srcWidth = srcWidth;
		BLIT_FRAME_BUFFER_COMMAND.srcHeight = srcHeight;
		BLIT_FRAME_BUFFER_COMMAND.dstX = dstX;
		BLIT_FRAME_BUFFER_COMMAND.dstY = dstY;
		BLIT_FRAME_BUFFER_COMMAND.dstWidth = dstWidth;
		BLIT_FRAME_BUFFER_COMMAND.dstHeight = dstHeight;
		BLIT_FRAME_BUFFER_COMMAND.filter = filter;
		BLIT_FRAME_BUFFER_COMMAND.write();
	}

	public void Enable(int capability) {
		Toggle(capability, true);
	}

	public void Disable(int capability) {
		Toggle(capability, false);
	}

	public void Toggle(int capability, boolean enabled) {
		TOGGLE_COMMAND.capability = capability;
		TOGGLE_COMMAND.state = enabled;
		TOGGLE_COMMAND.write();
	}

	public void printCommandBuffer() {
		readHead = 0;
		readBitHead = 0;
		cmd_log.setLength(0);

		while (readHead < writeHead || (readHead == writeHead && readBitHead < writeBitHead)) {
			int type = (int) readBits(8);
			assert type < REGISTERED_COMMANDS.length : "Unknown Command Type";

			BaseCommand command = REGISTERED_COMMANDS[type];
			command.doRead();
			command.print(cmd_log);
		}
		log.debug("=== CommandBuffer START ===\n{}\n=== CommandBuffer END ===", cmd_log);
	}

	@SneakyThrows
	public void submit() {
		readHead = 0;
		readBitHead = 0;

		while (readHead < writeHead || (readHead == writeHead && readBitHead < writeBitHead)) {
			final int type = (int)readBits(8);
			assert type < REGISTERED_COMMANDS.length : "Unknown Command Type";

			BaseCommand command = REGISTERED_COMMANDS[type];
			if (command.isDrawCall() && uboCommandBuffer != null && uboCommandBuffer.isDirty()) {
				uboCommandBuffer.upload();
			}

			command.doRead();
			command.execute();

			if(command.isGLCommand() && HdPlugin.checkGLErrors(command.getName())) {
				printCommandBuffer();
				break;
			}
		}
	}

	protected void writeObject(Object object) {
		if (objectCount >= objects.length) {
			objects = Arrays.copyOf(objects, objects.length * 2);
		}
		writeBits(objectCount, 32);
		objects[objectCount++] = object;
	}

	protected <T> T readObject() {
		int index = (int)readBits(32);
		Object object = objects[index];
		objects[index] = null;
		return (T) object;
	}

	protected long readBits(int numBits) {
		long word = cmd[readHead];
		if(readBitHead == 0) {
			if (numBits == BITS_PER_WORD){
				readHead++;
				return word;
			} else {
				readBitHead += numBits;
				return word & MASKS[numBits];
			}
		}

		long result = 0;
		int shift = 0;
		while (numBits > 0) {
			final int bitsToRead = Math.min(BITS_PER_WORD - readBitHead, numBits);
			final long bits = (word >>> readBitHead) & MASKS[bitsToRead];

			result |= (bits << shift);

			readBitHead += bitsToRead;
			if (readBitHead == BITS_PER_WORD) {
				readBitHead = 0;
				readHead++;
				word = cmd[readHead];
			}

			shift += bitsToRead;
			numBits -= bitsToRead;
		}

		return result;
	}

	protected void writeBits(long value, int numBits) {
		if (writeBitHead == 0) {
			if (numBits == BITS_PER_WORD) {
				cmd[writeHead++] = value;
			} else {
				writeBitHead += numBits;
				cmd[writeHead] = value & MASKS[numBits];
			}
			return;
		}

		long word = cmd[writeHead];
		while (numBits > 0) {
			final int bitsToWrite = Math.min(BITS_PER_WORD - writeBitHead, numBits);
			final long bits = value & MASKS[bitsToWrite];

			word |= bits << writeBitHead;
			cmd[writeHead] = word;

			writeBitHead += bitsToWrite;
			if (writeBitHead == BITS_PER_WORD) {
				writeHead++;
				ensureCapacity(writeHead);

				writeBitHead = 0;
				word = 0;
			}

			value >>>= bitsToWrite;
			numBits -= bitsToWrite;
		}
	}

	public void reset() {
		writeHead = 0;
		writeBitHead = 0;

		readHead = 0;
		readBitHead = 0;

		// Objects need to be cleared to avoid holding onto a reference and preventing garbage collection
		Arrays.fill(objects, null);
		objectCount = 0;
	}
}
