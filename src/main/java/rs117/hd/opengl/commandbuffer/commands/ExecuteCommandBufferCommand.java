package rs117.hd.opengl.commandbuffer.commands;

import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;
import rs117.hd.opengl.commandbuffer.CommandBuffer;

public class ExecuteCommandBufferCommand extends BaseCommand {

	public CommandBuffer cmd;

	@Override
	protected void doWrite() {
		writeObject(cmd);
	}

	@Override
	protected void doRead(MemoryStack stack) {
		cmd = readObject();
	}

	@Override
	protected void execute(MemoryStack stack) {
		cmd.execute(stack);
	}

	@Override
	protected void print(StringBuilder sb) {
		sb.append("ExecuteCommandBufferCommand");
		cmd.printCommandBuffer(sb);
	}
}
