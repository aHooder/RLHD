package rs117.hd.opengl.commandbuffer.commands;

import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;
import rs117.hd.opengl.uniforms.UniformBuffer;

public final class UploadUniformBufferCommand extends BaseCommand {

	public UniformBuffer<?> buffer;

	public UploadUniformBufferCommand() { super(false, true); }

	@Override
	protected void doWrite() {
		writeObject(buffer);
	}

	@Override
	protected void doRead(MemoryStack stack) {
		buffer = readObject();
	}

	@Override
	protected void execute(MemoryStack stack) {
		markUniformBufferDirty(buffer);
	}

	@Override
	protected void print(StringBuilder sb) {
		sb.append(buffer.getClass().getSimpleName());
		sb.append(".upload()");
	}
}
