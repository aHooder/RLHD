package rs117.hd.opengl.commandbuffer.commands;

import java.util.concurrent.Semaphore;
import rs117.hd.opengl.commandbuffer.BaseCommand;

public class SignalCommand extends BaseCommand {

	public Semaphore semaphore;

	@Override
	protected void doWrite() {
		writeObject(semaphore);
	}

	@Override
	protected void doRead() {
		semaphore = readObject();
	}

	@Override
	protected void execute() {
		semaphore.release();
	}

	@Override
	protected void print(StringBuilder sb) {}
}
