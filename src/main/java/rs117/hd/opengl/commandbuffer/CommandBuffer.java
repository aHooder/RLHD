package rs117.hd.opengl.commandbuffer;

import java.util.Arrays;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import rs117.hd.HdPlugin;
import rs117.hd.opengl.commandbuffer.commands.BindElementsArrayCommand;
import rs117.hd.opengl.commandbuffer.commands.BindVertexArrayCommand;
import rs117.hd.opengl.commandbuffer.commands.ColorMaskCommand;
import rs117.hd.opengl.commandbuffer.commands.DepthMaskCommand;
import rs117.hd.opengl.commandbuffer.commands.DrawArraysCommand;
import rs117.hd.opengl.commandbuffer.commands.DrawElementsCommand;
import rs117.hd.opengl.commandbuffer.commands.MultiDrawArraysCommand;
import rs117.hd.opengl.commandbuffer.commands.ToggleCommand;
import rs117.hd.opengl.commandbuffer.commands.UpdateCMDUBOCommand;
import rs117.hd.opengl.uniforms.UBOCommandBuffer;

@Slf4j
public class CommandBuffer {
	private static final StringBuilder SB = new StringBuilder();

	private static int COMMAND_COUNT = 0;
	private static final BaseCommand[] REGISTERED_COMMANDS = {
		(DRAW_ARRAYS_COMMAND = REGISTER_COMMAND(DrawArraysCommand::new)),
		(DRAW_ELEMENTS_COMMAND = REGISTER_COMMAND(DrawElementsCommand::new)),
		(MULTI_DRAW_ARRAYS_COMMAND = REGISTER_COMMAND(MultiDrawArraysCommand::new)),
		(TOGGLE_COMMAND = REGISTER_COMMAND(ToggleCommand::new)),
		(COLOR_MASK_COMMAND = REGISTER_COMMAND(ColorMaskCommand::new)),
		(DEPTH_MASK_COMMAND = REGISTER_COMMAND(DepthMaskCommand::new)),
		(BIND_ELEMENTS_ARRAY_COMMAND = REGISTER_COMMAND(BindElementsArrayCommand::new)),
		(BIND_VERTEX_ARRAY_COMMAND = REGISTER_COMMAND(BindVertexArrayCommand::new)),
		(UPDATE_CMD_UBO_COMMAND = REGISTER_COMMAND(UpdateCMDUBOCommand::new)),
	};

	private static final DrawArraysCommand DRAW_ARRAYS_COMMAND;
	private static final DrawElementsCommand DRAW_ELEMENTS_COMMAND;
	private static final MultiDrawArraysCommand MULTI_DRAW_ARRAYS_COMMAND;
	private static final ToggleCommand TOGGLE_COMMAND;
	private static final ColorMaskCommand COLOR_MASK_COMMAND;
	private static final DepthMaskCommand DEPTH_MASK_COMMAND;
	private static final BindElementsArrayCommand BIND_ELEMENTS_ARRAY_COMMAND;
	private static final BindVertexArrayCommand BIND_VERTEX_ARRAY_COMMAND;
	private static final UpdateCMDUBOCommand UPDATE_CMD_UBO_COMMAND;

	interface ICreateCommand<T extends BaseCommand> { T construct(); }

	private static <T extends BaseCommand> T REGISTER_COMMAND(ICreateCommand<T> createCommand) {
		T newCommand = createCommand.construct();
		newCommand.id = COMMAND_COUNT;
		COMMAND_COUNT++;
		return newCommand;
	}

	@Setter
	protected UBOCommandBuffer uboCommandBuffer;

	protected long[] cmd = new long[1 << 20]; // ~1 million calls
	protected int writeHead = 0;
	protected int readHead = 0;

	private final boolean validate = false;

	public void ensureCapacity(int numLongs) {
		if (writeHead + numLongs >= cmd.length)
			cmd = Arrays.copyOf(cmd, cmd.length * 2);
	}

	public void SetBaseOffset(int x, int y, int z) {
		UPDATE_CMD_UBO_COMMAND.isBaseOffset = true;
		UPDATE_CMD_UBO_COMMAND.x = x;
		UPDATE_CMD_UBO_COMMAND.y = y;
		UPDATE_CMD_UBO_COMMAND.z = z;
		UPDATE_CMD_UBO_COMMAND.write(this);

		if(validate) {
			UPDATE_CMD_UBO_COMMAND.read(this);
			assert readHead == writeHead &&
				   UPDATE_CMD_UBO_COMMAND.isBaseOffset &&
				   UPDATE_CMD_UBO_COMMAND.x == x &&
				   UPDATE_CMD_UBO_COMMAND.y == y &&
				   UPDATE_CMD_UBO_COMMAND.z == z : UPDATE_CMD_UBO_COMMAND.sb.toString();
		}
	}

	public void SetWorldViewIndex(int index) {
		UPDATE_CMD_UBO_COMMAND.isBaseOffset = false;
		UPDATE_CMD_UBO_COMMAND.worldViewId = index;
		UPDATE_CMD_UBO_COMMAND.write(this);

		if(validate) {
			UPDATE_CMD_UBO_COMMAND.read(this);
			assert readHead == writeHead &&
				   UPDATE_CMD_UBO_COMMAND.worldViewId == index : UPDATE_CMD_UBO_COMMAND.sb.toString();
		}
	}

	public void BindVertexArray(int vao) {
		BIND_VERTEX_ARRAY_COMMAND.vao = vao;
		BIND_VERTEX_ARRAY_COMMAND.write(this);

		if(validate) {
			BIND_VERTEX_ARRAY_COMMAND.read(this);
			assert readHead == writeHead &&
				   BIND_VERTEX_ARRAY_COMMAND.vao == vao : BIND_VERTEX_ARRAY_COMMAND.sb.toString();
		}
	}

	public void BindElementsArray(int ebo) {
		BIND_ELEMENTS_ARRAY_COMMAND.ebo = ebo;
		BIND_ELEMENTS_ARRAY_COMMAND.write(this);

		if(validate) {
			BIND_ELEMENTS_ARRAY_COMMAND.read(this);
			assert readHead == writeHead &&
				   BIND_ELEMENTS_ARRAY_COMMAND.ebo == ebo : BIND_ELEMENTS_ARRAY_COMMAND.sb.toString();
		}
	}

	public void DepthMask(boolean writeDepth) {
		DEPTH_MASK_COMMAND.flag = writeDepth;
		DEPTH_MASK_COMMAND.write(this);

		if(validate) {
			DEPTH_MASK_COMMAND.read(this);
			assert readHead == writeHead &&
				   DEPTH_MASK_COMMAND.flag == writeDepth : DEPTH_MASK_COMMAND.sb.toString();
		}
	}

	public void ColorMask(boolean writeRed, boolean writeGreen, boolean writeBlue, boolean writeAlpha) {
		COLOR_MASK_COMMAND.red = writeRed;
		COLOR_MASK_COMMAND.green = writeGreen;
		COLOR_MASK_COMMAND.blue = writeBlue;
		COLOR_MASK_COMMAND.alpha = writeAlpha;
		COLOR_MASK_COMMAND.write(this);

		if(validate) {
			COLOR_MASK_COMMAND.read(this);
			assert readHead == writeHead &&
				   COLOR_MASK_COMMAND.red == writeRed &&
				   COLOR_MASK_COMMAND.green == writeGreen &&
				   COLOR_MASK_COMMAND.blue == writeBlue &&
				   COLOR_MASK_COMMAND.alpha == writeAlpha : COLOR_MASK_COMMAND.sb.toString();
		}
	}

	public void MultiDrawArrays(int mode, int[] offsets, int[] counts) {
		MULTI_DRAW_ARRAYS_COMMAND.mode = mode;
		MULTI_DRAW_ARRAYS_COMMAND.offsets = offsets;
		MULTI_DRAW_ARRAYS_COMMAND.counts = counts;
		MULTI_DRAW_ARRAYS_COMMAND.write(this);

		if(validate) {
			MULTI_DRAW_ARRAYS_COMMAND.read(this);
			assert readHead == writeHead && MULTI_DRAW_ARRAYS_COMMAND.mode == mode : MULTI_DRAW_ARRAYS_COMMAND.sb.toString();
			for(int i = 0; i < offsets.length; i++) {
				assert offsets[i] == MULTI_DRAW_ARRAYS_COMMAND.offsetsBuffer.get(i) : MULTI_DRAW_ARRAYS_COMMAND.sb.toString();
				assert counts[i] == MULTI_DRAW_ARRAYS_COMMAND.countsBuffer.get(i) : MULTI_DRAW_ARRAYS_COMMAND.sb.toString();
			}
		}
	}

	public void DrawElements(int mode, int vertexCount, long offset) {
		DRAW_ELEMENTS_COMMAND.mode = mode;
		DRAW_ELEMENTS_COMMAND.vertexCount = vertexCount;
		DRAW_ELEMENTS_COMMAND.offset = offset;
		DRAW_ELEMENTS_COMMAND.write(this);

		if(validate) {
			DRAW_ELEMENTS_COMMAND.read(this);
			assert readHead == writeHead &&
				   DRAW_ELEMENTS_COMMAND.mode == mode &&
				   DRAW_ELEMENTS_COMMAND.vertexCount == vertexCount &&
				   DRAW_ELEMENTS_COMMAND.offset == offset : DRAW_ELEMENTS_COMMAND.sb.toString();
		}
	}

	public void DrawArrays(int mode, int offset, int vertexCount) {
		DRAW_ARRAYS_COMMAND.mode = mode;
		DRAW_ARRAYS_COMMAND.offset = offset;
		DRAW_ARRAYS_COMMAND.vertexCount = vertexCount;
		DRAW_ARRAYS_COMMAND.write(this);

		if(validate) {
			DRAW_ARRAYS_COMMAND.read(this);
			assert readHead == writeHead &&
				   DRAW_ARRAYS_COMMAND.mode == mode &&
				   DRAW_ARRAYS_COMMAND.offset == offset &&
				   DRAW_ARRAYS_COMMAND.vertexCount == vertexCount : DRAW_ARRAYS_COMMAND.sb.toString();
		}
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
		TOGGLE_COMMAND.write(this);

		if(validate) {
			TOGGLE_COMMAND.read(this);
			assert TOGGLE_COMMAND.capability == capability &&
				   TOGGLE_COMMAND.state == enabled : TOGGLE_COMMAND.sb.toString();
		}
	}

	@SneakyThrows
	public void execute() {
		readHead = 0;
		while (readHead < writeHead) {
			int type = (int) (cmd[readHead] & 0xFF);
			assert type < REGISTERED_COMMANDS.length;

			BaseCommand command = REGISTERED_COMMANDS[type];
			if (command.isDrawCall() && uboCommandBuffer != null && uboCommandBuffer.isDirty()) {
				uboCommandBuffer.upload();
			}
			command.read(this);
			command.execute();

			if(HdPlugin.checkGLErrors(command.getName())) {
				command.print(SB);
				log.warn("Encountered Error whilst processing command:\n{}", SB.toString());
				SB.setLength(0);
				break;
			}
		}
	}

	public void reset() {
		writeHead = 0;
		readHead = 0;
	}
}
