package rs117.hd.opengl.commandbuffer.commands;

import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;
import rs117.hd.opengl.shader.ShaderProgram;

public final class ShaderProgramCommand extends BaseCommand {
	public ShaderProgram program;

	public ShaderProgramCommand() { super(false, true); }

	@Override
	protected void doWrite() {
		writeObject(program);
	}

	@Override
	protected void doRead(MemoryStack stack) {
		program = readObject();
	}

	@Override
	protected void execute(MemoryStack stack) {
		program.use();
	}

	@Override
	protected void print(StringBuilder sb) {
		sb.append(program.getClass().getSimpleName());
		sb.append(".use();");
	}
}
