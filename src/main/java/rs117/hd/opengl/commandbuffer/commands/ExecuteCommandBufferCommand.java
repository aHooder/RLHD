package rs117.hd.opengl.commandbuffer.commands;

import rs117.hd.opengl.commandbuffer.BaseCommand;
import rs117.hd.opengl.commandbuffer.CommandBuffer;

public class ExecuteCommandBufferCommand extends BaseCommand {

	public CommandBuffer cmd;

	@Override
	protected void doWrite() {
		writeObject(cmd);
	}

	@Override
	protected void doRead() {
		cmd = readObject();
	}

	@Override
	protected void execute() {
		cmd.execute();
	}

	@Override
	protected void print(StringBuilder sb) {
		sb.append("ExecuteCommandBufferCommand");
		cmd.printCommandBuffer(sb);
	}
}
