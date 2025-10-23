package rs117.hd.opengl.commandbuffer.commands;

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
	protected void doRead() {
		callback = readObject();
	}

	@Override
	public void execute() {
		callback.run();
	}

	@Override
	public void print(StringBuilder sb) {
		sb.append("CallbackCommand");
	}
}
