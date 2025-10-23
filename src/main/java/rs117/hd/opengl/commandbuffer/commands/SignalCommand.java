package rs117.hd.opengl.commandbuffer.commands;

import java.util.concurrent.Semaphore;
import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;

public class SignalCommand extends BaseCommand {

	public Semaphore semaphore;

	@Override
	protected void doWrite() {
		writeObject(semaphore);
	}

	@Override
	protected void doRead(MemoryStack stack) {
		semaphore = readObject();
	}

	@Override
	protected void execute(MemoryStack stack) {
		semaphore.release();
	}

	@Override
	protected void print(StringBuilder sb) {}
}
