package rs117.hd.opengl.commandbuffer.commands;

import rs117.hd.opengl.commandbuffer.BaseCommand;
import rs117.hd.opengl.shader.ShaderProgram;

public class ShaderProgramCommand extends BaseCommand {
	public ShaderProgram program;

	public ShaderProgramCommand() { super(false, true); }

	@Override
	protected void doWrite() {
		writeObject(program);
	}

	@Override
	protected void doRead() {
		program = readObject();
	}

	@Override
	protected void execute() {
		program.use();
	}

	@Override
	protected void print(StringBuilder sb) {
		sb.append(program.getClass().getSimpleName());
		sb.append(".use();");
	}
}
