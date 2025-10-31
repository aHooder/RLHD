package rs117.hd.opengl.commandbuffer.commands;

import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;
import rs117.hd.opengl.commandbuffer.FrameSync;

public final class SignalCommand extends BaseCommand {

	public FrameSync frameSync;

	@Override
	protected void doWrite() {
		writeObject(frameSync);
	}

	@Override
	protected void doRead(MemoryStack stack) {
		frameSync = readObject();
	}

	@Override
	protected void execute(MemoryStack stack) {
		frameSync.signalReady();
	}

	@Override
	protected void print(StringBuilder sb) {}
}
