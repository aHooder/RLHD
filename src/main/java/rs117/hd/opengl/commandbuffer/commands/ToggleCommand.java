package rs117.hd.opengl.commandbuffer.commands;

import rs117.hd.opengl.commandbuffer.BaseCommand;

import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;

public final class ToggleCommand extends BaseCommand {
	public int capability;
	public boolean state;

	@Override
	public void doWrite() {
		write32(capability);
		writeFlag(state);
	}

	@Override
	public void doRead() {
		capability = read32();
		state = readFlag();
	}

	@Override
	public void execute() {
		if (state) {
			glEnable(capability);
		} else {
			glDisable(capability);
		}
	}

	@Override
	public void print(StringBuilder sb) {
		if (state) {
			sb.append("glEnable(");
			sb.append(capability);
			sb.append(");");
		} else {
			sb.append("glDisable(");
			sb.append(capability);
			sb.append(");");
		}
	}
}
