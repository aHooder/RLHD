package rs117.hd.opengl.commandbuffer;

import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import net.runelite.rlawt.AWTContext;
import org.lwjgl.system.MemoryStack;
import rs117.hd.HdPlugin;
import rs117.hd.opengl.commandbuffer.commands.BindElementsArrayCommand;
import rs117.hd.opengl.commandbuffer.commands.BindFrameBufferCommand;
import rs117.hd.opengl.commandbuffer.commands.BindVertexArrayCommand;
import rs117.hd.opengl.commandbuffer.commands.BlendFuncCommand;
import rs117.hd.opengl.commandbuffer.commands.BlitFrameBufferCommand;
import rs117.hd.opengl.commandbuffer.commands.CallbackCommand;
import rs117.hd.opengl.commandbuffer.commands.ClearCommand;
import rs117.hd.opengl.commandbuffer.commands.ColorMaskCommand;
import rs117.hd.opengl.commandbuffer.commands.DepthFuncCommand;
import rs117.hd.opengl.commandbuffer.commands.DepthMaskCommand;
import rs117.hd.opengl.commandbuffer.commands.DrawArraysCommand;
import rs117.hd.opengl.commandbuffer.commands.DrawBufferCommand;
import rs117.hd.opengl.commandbuffer.commands.DrawElementsCommand;
import rs117.hd.opengl.commandbuffer.commands.ExecuteCommandBufferCommand;
import rs117.hd.opengl.commandbuffer.commands.FrameBufferLayerCommand;
import rs117.hd.opengl.commandbuffer.commands.FrameTimerCommand;
import rs117.hd.opengl.commandbuffer.commands.MultiDrawArraysCommand;
import rs117.hd.opengl.commandbuffer.commands.SetShaderUniformCommand;
import rs117.hd.opengl.commandbuffer.commands.SetUniformBufferPropertyCommand;
import rs117.hd.opengl.commandbuffer.commands.ShaderProgramCommand;
import rs117.hd.opengl.commandbuffer.commands.SignalCommand;
import rs117.hd.opengl.commandbuffer.commands.SwapBuffersCommand;
import rs117.hd.opengl.commandbuffer.commands.ToggleCommand;
import rs117.hd.opengl.commandbuffer.commands.UploadPixelDataCommand;
import rs117.hd.opengl.commandbuffer.commands.UploadUniformBufferCommand;
import rs117.hd.opengl.commandbuffer.commands.ViewportCommand;
import rs117.hd.opengl.shader.ShaderProgram;
import rs117.hd.opengl.uniforms.UniformBuffer;
import rs117.hd.overlays.FrameTimer;
import rs117.hd.overlays.Timer;

import static rs117.hd.utils.MathUtils.*;

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
	private final BaseCommand[] REGISTERED_COMMANDS = new BaseCommand[100];

	private final DrawArraysCommand DRAW_ARRAYS_COMMAND = REGISTER_COMMAND(DrawArraysCommand::new);
	private final DrawElementsCommand DRAW_ELEMENTS_COMMAND = REGISTER_COMMAND(DrawElementsCommand::new);
	private final MultiDrawArraysCommand MULTI_DRAW_ARRAYS_COMMAND = REGISTER_COMMAND(MultiDrawArraysCommand::new);
	private final ToggleCommand TOGGLE_COMMAND = REGISTER_COMMAND(ToggleCommand::new);
	private final BindFrameBufferCommand BIND_FRAMEBUFFER_COMMAND = REGISTER_COMMAND(BindFrameBufferCommand::new);
	private final ViewportCommand VIEWPORT_COMMAND = REGISTER_COMMAND(ViewportCommand::new);
	private final ClearCommand CLEAR_COMMAND = REGISTER_COMMAND(ClearCommand::new);
	private final ColorMaskCommand COLOR_MASK_COMMAND = REGISTER_COMMAND(ColorMaskCommand::new);
	private final DepthFuncCommand DEPTH_FUNC_COMMAND = REGISTER_COMMAND(DepthFuncCommand::new);
	private final DepthMaskCommand DEPTH_MASK_COMMAND = REGISTER_COMMAND(DepthMaskCommand::new);
	private final BlendFuncCommand BLENDED_FUNC_COMMAND = REGISTER_COMMAND(BlendFuncCommand::new);
	private final BindElementsArrayCommand BIND_ELEMENTS_ARRAY_COMMAND = REGISTER_COMMAND(BindElementsArrayCommand::new);
	private final BindVertexArrayCommand BIND_VERTEX_ARRAY_COMMAND = REGISTER_COMMAND(BindVertexArrayCommand::new);
	private final BlitFrameBufferCommand BLIT_FRAME_BUFFER_COMMAND = REGISTER_COMMAND(BlitFrameBufferCommand::new);
	private final SetUniformBufferPropertyCommand SET_UNIFORM_BUFFER_PROPERTY_COMMAND = REGISTER_COMMAND(SetUniformBufferPropertyCommand::new);
	private final SetShaderUniformCommand SET_SHADER_PROPERTY_COMMAND = REGISTER_COMMAND(SetShaderUniformCommand::new);
	private final ShaderProgramCommand SHADER_PROGRAM_COMMAND = REGISTER_COMMAND(ShaderProgramCommand::new);
	private final UploadPixelDataCommand UPLOAD_PIXEL_DATA_COMMAND = REGISTER_COMMAND(UploadPixelDataCommand::new);
	private final ExecuteCommandBufferCommand EXECUTE_COMMAND_BUFFER_COMMAND = REGISTER_COMMAND(ExecuteCommandBufferCommand::new);
	private final SwapBuffersCommand SWAP_BUFFER_COMMAND = REGISTER_COMMAND(SwapBuffersCommand::new);
	private final FrameTimerCommand FRAME_TIMER_COMMAND = REGISTER_COMMAND(FrameTimerCommand::new);
	private final CallbackCommand CALLBACK_COMMAND = REGISTER_COMMAND(CallbackCommand::new);
	private final SignalCommand SIGNAL_COMMAND = REGISTER_COMMAND(SignalCommand::new);;
	private final UploadUniformBufferCommand UPLOAD_UNIFORM_BUFFER_COMMAND = REGISTER_COMMAND(UploadUniformBufferCommand::new);
	private final DrawBufferCommand DRAW_BUFFER_COMMAND = REGISTER_COMMAND(DrawBufferCommand::new);
	private final FrameBufferLayerCommand FRAME_BUFFER_LAYER_COMMAND = REGISTER_COMMAND(FrameBufferLayerCommand::new);

	interface ICreateCommand<T extends BaseCommand> { T construct(); }

	private <T extends BaseCommand> T REGISTER_COMMAND(ICreateCommand<T> createCommand) {
		{
			T ExecutorCommand = createCommand.construct();
			ExecutorCommand.id = COMMAND_COUNT;
			ExecutorCommand.owner = this;

			REGISTERED_COMMANDS[COMMAND_COUNT] = ExecutorCommand;
		}

		{
			T WriterCommand = createCommand.construct();
			WriterCommand.id = COMMAND_COUNT;
			WriterCommand.owner = this;
			COMMAND_COUNT++;
			return WriterCommand;
		}
	}

	private long[] cmd = new long[1000];

	private final UniformBuffer<?>[] pendingUBOUploads = new UniformBuffer[100];
	private int pendingUBOUploadsCount = 0;

	private Object[] objects = new Object[100];
	private int objectCount = 0;

	private int writeHead = 0;
	private int writeBitHead = 0;

	private int readHead = 0;
	private int readBitHead = 0;

	protected boolean pooled;

	private Timer executionTimer = null;

	public CommandBuffer SetExecutionTimer(Timer timer) {
		this.executionTimer = timer;
		return this;
	}

	public void SetUniformProperty(UniformBuffer.Property property, int... values) {
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.property = property;
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.intValues = values;
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.isFloat = false;
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.write();
	}

	public void SetUniformProperty(UniformBuffer.Property property, float... values) {
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.property = property;
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.floatValues = values;
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.isFloat = true;
		SET_UNIFORM_BUFFER_PROPERTY_COMMAND.write();
	}

	public void SetUniformProperty(ShaderProgram.UniformProperty property, int... values) {
		SET_SHADER_PROPERTY_COMMAND.property = property;
		SET_SHADER_PROPERTY_COMMAND.intValues = values;
		SET_SHADER_PROPERTY_COMMAND.isFloat = false;
		SET_SHADER_PROPERTY_COMMAND.write();
	}

	public void SetUniformProperty(ShaderProgram.UniformProperty property, float... values) {
		SET_SHADER_PROPERTY_COMMAND.property = property;
		SET_SHADER_PROPERTY_COMMAND.floatValues = values;
		SET_SHADER_PROPERTY_COMMAND.isFloat = true;
		SET_SHADER_PROPERTY_COMMAND.write();
	}

	public void FrameBufferLayer(int attachment, int texId, int level, int layer) {
		FRAME_BUFFER_LAYER_COMMAND.attachment = attachment;
		FRAME_BUFFER_LAYER_COMMAND.texId = texId;
		FRAME_BUFFER_LAYER_COMMAND.level = level;
		FRAME_BUFFER_LAYER_COMMAND.layer = layer;
		FRAME_BUFFER_LAYER_COMMAND.write();
	}

	public void DrawBuffers(int... drawBuffers) {
		DRAW_BUFFER_COMMAND.drawBuffers = drawBuffers;
		DRAW_BUFFER_COMMAND.write();
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

	public void UploadPixelData(int texUnit, int tex, int pbo, int width, int height, int[] data) {
		UPLOAD_PIXEL_DATA_COMMAND.texUnit = texUnit;
		UPLOAD_PIXEL_DATA_COMMAND.tex = tex;
		UPLOAD_PIXEL_DATA_COMMAND.pbo = pbo;
		UPLOAD_PIXEL_DATA_COMMAND.width = width;
		UPLOAD_PIXEL_DATA_COMMAND.height = height;
		UPLOAD_PIXEL_DATA_COMMAND.data = data;
		UPLOAD_PIXEL_DATA_COMMAND.stageData = null;
		UPLOAD_PIXEL_DATA_COMMAND.stageComplete = null;
		UPLOAD_PIXEL_DATA_COMMAND.write();
	}

	public void UploadPixelData(int texUnit, int tex, int pbo, int width, int height, int[] data, AtomicBoolean stageData, AtomicBoolean stageComplete) {
		UPLOAD_PIXEL_DATA_COMMAND.texUnit = texUnit;
		UPLOAD_PIXEL_DATA_COMMAND.tex = tex;
		UPLOAD_PIXEL_DATA_COMMAND.pbo = pbo;
		UPLOAD_PIXEL_DATA_COMMAND.width = width;
		UPLOAD_PIXEL_DATA_COMMAND.height = height;
		UPLOAD_PIXEL_DATA_COMMAND.data = data;
		UPLOAD_PIXEL_DATA_COMMAND.stageData = stageData;
		UPLOAD_PIXEL_DATA_COMMAND.stageComplete = stageComplete;
		UPLOAD_PIXEL_DATA_COMMAND.write();
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

	public void Callback(CallbackCommand.ICallback callback) {
		CALLBACK_COMMAND.callback = callback;
		CALLBACK_COMMAND.write();
	}

	public void Signal(Semaphore semaphore) {
		SIGNAL_COMMAND.semaphore = semaphore;
		SIGNAL_COMMAND.write();
	}

	public void UploadUniformBuffer(UniformBuffer<?> buffer) {
		UPLOAD_UNIFORM_BUFFER_COMMAND.buffer = buffer;
		UPLOAD_UNIFORM_BUFFER_COMMAND.write();
	}

	public void BeginTimer(Timer timer) {
		FRAME_TIMER_COMMAND.timer = timer;
		FRAME_TIMER_COMMAND.begin = true;
		FRAME_TIMER_COMMAND.write();
	}

	public void EndTimer(Timer timer) {
		FRAME_TIMER_COMMAND.timer = timer;
		FRAME_TIMER_COMMAND.begin = false;
		FRAME_TIMER_COMMAND.write();
	}

	public void SwapBuffers(AWTContext context) {
		SWAP_BUFFER_COMMAND.awtContext = context;
		SWAP_BUFFER_COMMAND.write();
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

	public void printCommandBuffer(StringBuilder sb) {
		readHead = 0;
		readBitHead = 0;

		try (MemoryStack stack = MemoryStack.stackPush()){
			while (readHead < writeHead || (readHead == writeHead && readBitHead < writeBitHead)) {
				int type = (int) readBits(8);
				assert type < REGISTERED_COMMANDS.length : "Unknown Command Type";

				BaseCommand command = REGISTERED_COMMANDS[type];
				command.doRead(stack);
				command.print(sb);
				sb.append('\n');
			}
		}
	}

	public void execute() {
		try(MemoryStack stack = MemoryStack.stackPush()) {
			execute(stack);
		}
	}

	public void execute(MemoryStack stack) {
		readHead = 0;
		readBitHead = 0;

		long readElapsed = 0;
		long readTimestamp = 0;
		long executeTimestamp = 0;

		if(executionTimer != null) {
			executeTimestamp = System.nanoTime();
		}

		while (readHead < writeHead || (readHead == writeHead && readBitHead < writeBitHead)) {
			final int type = (int)readBits(8);
			assert type < REGISTERED_COMMANDS.length : "Unknown Command Type";

			BaseCommand command = REGISTERED_COMMANDS[type];

			if (command.isDrawCall())
				uploadPendingUniformBuffers();

			stack.push();
			readTimestamp = System.nanoTime();
			command.doRead(stack);
			readElapsed += System.nanoTime() - readTimestamp;

			command.execute(stack);
			stack.pop();

			if(command.isGLCommand() && HdPlugin.checkGLErrors(command.getName())) {
				//StringBuilder sb = new StringBuilder();
				//printCommandBuffer(sb);
				//log.debug("=== CommandBuffer START ===\n{}\n=== CommandBuffer END ===", sb);
				//break;
			}
		}

		uploadPendingUniformBuffers();

		if(executionTimer != null) {
			FrameTimer.getInstance().add(executionTimer, System.nanoTime() - executeTimestamp);
		}
		FrameTimer.getInstance().add(Timer.COMMAND_BUFFER_READ, readElapsed);
	}

	private void uploadPendingUniformBuffers() {
		for(int i = 0; i < pendingUBOUploadsCount; i++) {
			if(pendingUBOUploads[i].isDirty()) {
				pendingUBOUploads[i].upload();
				pendingUBOUploads[i] = null;
			}
		}
		pendingUBOUploadsCount = 0;
	}

	protected void markUniformBufferDirty(UniformBuffer<?> buffer) {
		for(int i = 0; i < pendingUBOUploadsCount; i++) {
			if(pendingUBOUploads[i] == buffer) {
				return;
			}
		}
		pendingUBOUploads[pendingUBOUploadsCount++] = buffer;
	}

	protected void writeObject(Object object) {
		for(int i = 0; i < objectCount; i++) {
			if(objects[i] == object) {
				writeBits(i, 8);
				return;
			}
		}

		if (objectCount >= objects.length) {
			objects = Arrays.copyOf(objects, objects.length * 2);
		}

		assert objectCount < 255 : "Too many objects in command buffer, limit needs increasing: " + objectCount + " > 255";
		writeBits(objectCount, 8);
		objects[objectCount++] = object;
	}

	protected <T> T readObject() {
		int index = (int)readBits(8);
		return (T) objects[index];
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
			final int bitsToRead = min(BITS_PER_WORD - readBitHead, numBits);
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
			final int bitsToWrite = min(BITS_PER_WORD - writeBitHead, numBits);
			final long bits = value & MASKS[bitsToWrite];

			word |= bits << writeBitHead;
			cmd[writeHead] = word;

			writeBitHead += bitsToWrite;
			if (writeBitHead == BITS_PER_WORD) {
				writeHead++;
				if (writeHead >= cmd.length)
					cmd = Arrays.copyOf(cmd, cmd.length * 2);

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
		Arrays.fill(objects, 0, objectCount, null);
		objectCount = 0;

		Arrays.fill(pendingUBOUploads, 0, pendingUBOUploadsCount, null);
		pendingUBOUploadsCount = 0;
	}
}
