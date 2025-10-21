package rs117.hd.opengl.commandbuffer;

import java.util.Arrays;
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
		(TOGGLE_COMMAND = REGISTER_COMMAND(ToggleCommand::new)),
		(COLOR_MASK_COMMAND = REGISTER_COMMAND(ColorMaskCommand::new)),
		(DEPTH_MASK_COMMAND = REGISTER_COMMAND(DepthMaskCommand::new)),
		(BIND_ELEMENTS_ARRAY_COMMAND = REGISTER_COMMAND(BindElementsArrayCommand::new)),
		(BIND_VERTEX_ARRAY_COMMAND = REGISTER_COMMAND(BindVertexArrayCommand::new)),
		(UPDATE_CMD_UBO_COMMAND = REGISTER_COMMAND(UpdateCMDUBOCommand::new)),
	};

	private final DrawArraysCommand DRAW_ARRAYS_COMMAND;
	private final DrawElementsCommand DRAW_ELEMENTS_COMMAND;
	private final MultiDrawArraysCommand MULTI_DRAW_ARRAYS_COMMAND;
	private final ToggleCommand TOGGLE_COMMAND;
	private final ColorMaskCommand COLOR_MASK_COMMAND;
	private final DepthMaskCommand DEPTH_MASK_COMMAND;
	private final BindElementsArrayCommand BIND_ELEMENTS_ARRAY_COMMAND;
	private final BindVertexArrayCommand BIND_VERTEX_ARRAY_COMMAND;
	private final UpdateCMDUBOCommand UPDATE_CMD_UBO_COMMAND;

	interface ICreateCommand<T extends BaseCommand> { T construct(); }

	private <T extends BaseCommand> T REGISTER_COMMAND(ICreateCommand<T> createCommand) {
		T newCommand = createCommand.construct();
		newCommand.id = COMMAND_COUNT;
		newCommand.buffer = this;
		COMMAND_COUNT++;
		return newCommand;
	}

	public UBOCommandBuffer uboCommandBuffer;

	private long[] cmd = new long[1 << 20]; // ~1 million calls
	private int writeHead = 0;
	private int writeBitHead = 0;

	private int readHead = 0;
	private int readBitHead = 0;

	private final StringBuilder cmd_log = new StringBuilder();

	public void ensureCapacity(int numLongs) {
		if (writeHead + numLongs >= cmd.length)
			cmd = Arrays.copyOf(cmd, cmd.length * 2);
	}

	public void SetBaseOffset(int x, int y, int z) {
		UPDATE_CMD_UBO_COMMAND.isBaseOffset = true;
		UPDATE_CMD_UBO_COMMAND.x = x;
		UPDATE_CMD_UBO_COMMAND.y = y;
		UPDATE_CMD_UBO_COMMAND.z = z;
		UPDATE_CMD_UBO_COMMAND.write();
	}

	public void SetWorldViewIndex(int index) {
		UPDATE_CMD_UBO_COMMAND.isBaseOffset = false;
		UPDATE_CMD_UBO_COMMAND.worldViewId = index;
		UPDATE_CMD_UBO_COMMAND.write();
	}

	public void BindVertexArray(int vao) {
		BIND_VERTEX_ARRAY_COMMAND.vao = vao;
		BIND_VERTEX_ARRAY_COMMAND.write();
	}

	public void BindElementsArray(int ebo) {
		BIND_ELEMENTS_ARRAY_COMMAND.ebo = ebo;
		BIND_ELEMENTS_ARRAY_COMMAND.write();
	}

	public void DepthMask(boolean writeDepth) {
		DEPTH_MASK_COMMAND.flag = writeDepth;
		DEPTH_MASK_COMMAND.write();
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
	public void execute() {
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
	}
}
