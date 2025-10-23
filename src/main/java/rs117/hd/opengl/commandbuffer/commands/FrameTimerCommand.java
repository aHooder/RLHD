package rs117.hd.opengl.commandbuffer.commands;

import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;
import rs117.hd.overlays.FrameTimer;
import rs117.hd.overlays.Timer;

public class FrameTimerCommand extends BaseCommand {
	public Timer timer;
	public boolean begin;

	@Override
	protected void doWrite() {
		write32(timer.ordinal());
		writeFlag(begin);
	}

	@Override
	protected void doRead(MemoryStack stack) {
		timer = Timer.TIMERS[read32()];
		begin = readFlag();
	}

	@Override
	protected void execute(MemoryStack stack) {
		if(begin) {
			FrameTimer.getInstance().begin(timer);
		} else {
			FrameTimer.getInstance().end(timer);
		}
	}

	@Override
	protected void print(StringBuilder sb) {
		// EEEEERM
	}
}
