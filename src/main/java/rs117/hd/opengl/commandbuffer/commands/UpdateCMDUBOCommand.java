package rs117.hd.opengl.commandbuffer.commands;

import rs117.hd.opengl.commandbuffer.BaseCommand;

public class UpdateCMDUBOCommand extends BaseCommand {
	public boolean isBaseOffset;
	public int x, y, z;
	public int worldViewId;

	@Override
	protected void doWrite() {
		write1(isBaseOffset ? 1 : 0);
		if(isBaseOffset) {
			write32(x);
			write32(y);
			write32(z);
		} else {
			write32(worldViewId);
		}
	}

	@Override
	protected void doRead() {
		isBaseOffset = read1() == 1;
		if(isBaseOffset) {
			x = read32();
			y = read32();
			z = read32();
		} else {
			worldViewId = read32();
		}
	}

	@Override
	public void execute() {
		if(isBaseOffset) {
			getUBOCommandBuffer().sceneBase.set(x, y, z);
		} else {
			getUBOCommandBuffer().worldViewIndex.set(worldViewId);
		}
	}

	@Override
	public void print(StringBuilder sb) {
		if(isBaseOffset) {
			sb.append("UBOCommandBuffer::sceneBase = (");
			sb.append(x);
			sb.append(", ");
			sb.append(y);
			sb.append(", ");
			sb.append(z);
			sb.append(");");
		} else {
			sb.append("UBOCommandBuffer::worldViewIndex = (");
			sb.append(worldViewId);
			sb.append(");");
		}
	}
}
