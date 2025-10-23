package rs117.hd.opengl.commandbuffer.commands;

import net.runelite.rlawt.AWTContext;
import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;

public class SwapBuffersCommand extends BaseCommand {

	public AWTContext awtContext;

	@Override
	protected void doWrite() {
		writeObject(awtContext);
	}

	@Override
	protected void doRead(MemoryStack stack) {
		awtContext = readObject();
	}

	@Override
	protected void execute(MemoryStack stack) {
		awtContext.swapBuffers();
	}

	@Override
	protected void print(StringBuilder sb) {
		sb.append("awtContext.swapBuffers();");
	}
}
