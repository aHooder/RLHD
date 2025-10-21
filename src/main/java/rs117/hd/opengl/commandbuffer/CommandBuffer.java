package rs117.hd.opengl.commandbuffer;

import java.nio.BufferUnderflowException;
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
	public static boolean VALIDATE = false;

	private static final int BITS_PER_WORD = 64;
	private static final long[] MASKS = new long[65];

	static {
		MASKS[0] = 0L;
		for (int i = 1; i < BITS_PER_WORD; i++)  {
			MASKS[i] = (1L << i) - 1L;
		}
		MASKS[BITS_PER_WORD] = ~0L;
	}

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

	public UBOCommandBuffer uboCommandBuffer;

	private long[] cmd = new long[1 << 20]; // ~1 million calls
	private int writeHead = 0;
	private int writeBitHead = 0;

	private int readHead = 0;
	private int readBitHead = 0;

	private final StringBuilder cmd_log = new StringBuilder();
	private final StringBuilder validation_log = new StringBuilder();

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
	}

	public void SetWorldViewIndex(int index) {
		UPDATE_CMD_UBO_COMMAND.isBaseOffset = false;
		UPDATE_CMD_UBO_COMMAND.worldViewId = index;
		UPDATE_CMD_UBO_COMMAND.write(this);
	}

	public void BindVertexArray(int vao) {
		BIND_VERTEX_ARRAY_COMMAND.vao = vao;
		BIND_VERTEX_ARRAY_COMMAND.write(this);
	}

	public void BindElementsArray(int ebo) {
		BIND_ELEMENTS_ARRAY_COMMAND.ebo = ebo;
		BIND_ELEMENTS_ARRAY_COMMAND.write(this);
	}

	public void DepthMask(boolean writeDepth) {
		DEPTH_MASK_COMMAND.flag = writeDepth;
		DEPTH_MASK_COMMAND.write(this);
	}

	public void ColorMask(boolean writeRed, boolean writeGreen, boolean writeBlue, boolean writeAlpha) {
		COLOR_MASK_COMMAND.red = writeRed;
		COLOR_MASK_COMMAND.green = writeGreen;
		COLOR_MASK_COMMAND.blue = writeBlue;
		COLOR_MASK_COMMAND.alpha = writeAlpha;
		COLOR_MASK_COMMAND.write(this);
	}

	public void MultiDrawArrays(int mode, int[] offsets, int[] counts) {
		MULTI_DRAW_ARRAYS_COMMAND.mode = mode;
		MULTI_DRAW_ARRAYS_COMMAND.offsets = offsets;
		MULTI_DRAW_ARRAYS_COMMAND.counts = counts;
		MULTI_DRAW_ARRAYS_COMMAND.write(this);
	}

	public void DrawElements(int mode, int vertexCount, long bytesOffset) {
		DRAW_ELEMENTS_COMMAND.mode = mode;
		DRAW_ELEMENTS_COMMAND.vertexCount = vertexCount;
		DRAW_ELEMENTS_COMMAND.bytesOffset = bytesOffset;
		DRAW_ELEMENTS_COMMAND.write(this);
	}

	public void DrawArrays(int mode, int offset, int vertexCount) {
		DRAW_ARRAYS_COMMAND.mode = mode;
		DRAW_ARRAYS_COMMAND.offset = offset;
		DRAW_ARRAYS_COMMAND.vertexCount = vertexCount;
		DRAW_ARRAYS_COMMAND.write(this);
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
	}

	public void printCommandBuffer() {
		readHead = 0;
		readBitHead = 0;
		validation_log.setLength(0);
		cmd_log.setLength(0);

		while (readHead < writeHead || (readHead == writeHead && readBitHead < writeBitHead)) {
			int type = (int) readBits(8);
			assert type < REGISTERED_COMMANDS.length : validation_log.toString();

			BaseCommand command = REGISTERED_COMMANDS[type];
			command.read(this);
			command.print(cmd_log);
		}
		log.debug("=== CommandBuffer START ===\n{}\n=== CommandBuffer END ===", cmd_log);
	}

	@SneakyThrows
	public void execute() {
		readHead = 0;
		readBitHead = 0;
		validation_log.setLength(0);

		while (readHead < writeHead || (readHead == writeHead && readBitHead < writeBitHead)) {
			int type = (int)readBits(8);
			assert type < REGISTERED_COMMANDS.length : validation_log.toString();

			BaseCommand command = REGISTERED_COMMANDS[type];
			if (command.isDrawCall() && uboCommandBuffer != null && uboCommandBuffer.isDirty()) {
				uboCommandBuffer.upload();
			}

			command.read(this);
			command.execute();

			if(HdPlugin.checkGLErrors(command.getName())) {
				printCommandBuffer();
				break;
			}
		}
	}

	protected long readBits(int numBits) {
		if (readHead >= cmd.length)
			throw new BufferUnderflowException();

		long result = 0;
		long word = cmd[readHead];
		if(readBitHead == 0) {
			if (numBits == BITS_PER_WORD){
				readHead++;
				result = word;
			} else {
				readBitHead += numBits;
				result = word & MASKS[numBits];
			}
		} else {
			int shift = 0;
			while (numBits > 0) {
				int bitsLeftInWord = BITS_PER_WORD - readBitHead;
				int bitsToRead = Math.min(bitsLeftInWord, numBits);

				if (VALIDATE) logReadBits(numBits, readHead, readBitHead, bitsToRead);

				long mask = (bitsToRead == BITS_PER_WORD) ? ~0L : MASKS[bitsToRead];
				long bits = (word >>> readBitHead) & mask;
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
		}

		if(VALIDATE) logReadResult(result);

		return result;
	}

	protected void writeBits(long value, int numBits) {
		long originalValue = value;
		int originalNumBits = numBits;
		int originalWriteHead = writeHead;
		int originalWriteBitHead = writeBitHead;

		if (writeBitHead == 0) {
			if (numBits == BITS_PER_WORD) {
				cmd[writeHead++] = value;
			} else {
				writeBitHead += numBits;
				cmd[writeHead] = value & MASKS[numBits];
			}
		} else {
			long word = cmd[writeHead];
			while (numBits > 0) {
				int bitsLeftInWord = BITS_PER_WORD - writeBitHead;
				int bitsToWrite = Math.min(bitsLeftInWord, numBits);

				long bits = (bitsToWrite == BITS_PER_WORD) ? value : (value & MASKS[bitsToWrite]);

				if (VALIDATE) logWriteBits(value, numBits, writeHead, writeBitHead, bitsToWrite);

				long destMask = (bitsToWrite == BITS_PER_WORD) ? ~0L : (MASKS[bitsToWrite] << writeBitHead);
				word = (word & ~destMask) | ((bits << writeBitHead) & destMask);
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

			if (VALIDATE) {
				int originalReadHead = readHead;
				int originalReadBitHead = readBitHead;

				readHead = originalWriteHead;
				readBitHead = originalWriteBitHead;

				long decoded = readBits(originalNumBits);
				assert decoded == originalValue :
					"read: " + decoded + " but expected: " + originalValue + "\nValidation Log:" + validation_log;

				readHead = originalReadHead;
				readBitHead = originalReadBitHead;
			}
		}
	}

	protected void appendToValidationLog(String str) {
		validation_log.append(str);
	}

	private void logWriteBits(long value, int numBits, int wordIndex, int bitHead, int bitsToWrite) {
		validation_log.append("writeBits(");
		validation_log.append(value);
		validation_log.append(", ");
		validation_log.append(numBits);
		validation_log.append(") wordIndex: ");
		validation_log.append(wordIndex);
		validation_log.append(" bitHead: ");
		validation_log.append(bitHead);
		validation_log.append(" bitsToWrite: ");
		validation_log.append(bitsToWrite);
		validation_log.append("\n");
	}

	private void logReadBits(int numBits, int wordIndex, int bitHead, int bitsToRead) {
		validation_log.append("readBits(");
		validation_log.append(numBits);
		validation_log.append(") wordIndex: ");
		validation_log.append(wordIndex);
		validation_log.append(" bitHead: ");
		validation_log.append(bitHead);
		validation_log.append(" bitsToRead: ");
		validation_log.append(bitsToRead);
		validation_log.append("\n");
	}

	private void logReadResult(long result) {
		validation_log.append("readBits() Result: ");
		validation_log.append(result);
		validation_log.append("\n");
	}

	public void reset() {
		writeHead = 0;
		writeBitHead = 0;

		readHead = 0;
		readBitHead = 0;

		validation_log.setLength(0);
	}
}
