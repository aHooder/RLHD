package rs117.hd.opengl.commandbuffer.commands;

import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;

public class CallbackCommand extends BaseCommand {
	public interface ICallback {
		void run();
	}

	public ICallback callback;

	@Override
	protected void doWrite() {
		writeObject(callback);
	}

	@Override
	protected void doRead(MemoryStack stack) {
		callback = readObject();
	}

	@Override
	public void execute(MemoryStack stack) {
		callback.run();
	}

	@Override
	public void print(StringBuilder sb) {
		sb.append("CallbackCommand");
	}
}
