package rs117.hd.opengl.commandbuffer.commands;

import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;
import rs117.hd.opengl.uniforms.UniformBuffer;

public class SetUniformBufferPropertyCommand extends BaseCommand {

	public UniformBuffer.Property property;
	public int[] intValues;
	public float[] floatValues;
	public boolean isFloat;

	private int stagingSize;
	private int[] stagingIntValues;
	private float[] stagingFloatValues;

	public SetUniformBufferPropertyCommand() { super(false, true); }

	@Override
	protected void doWrite() {
		writeObject(property);
		writeFlag(isFloat);
		if(isFloat) {
			write32(floatValues.length);
			for(float f : floatValues) {
				write32F(f);
			}
		} else {
			write32(intValues.length);
			for(int i : intValues) {
				write32(i);
			}
		}
		floatValues = null;
		intValues = null;
	}

	@Override
	protected void doRead(MemoryStack stack) {
		property = readObject();
		isFloat = readFlag();
		stagingSize = read32();
		if(isFloat) {
			if(stagingFloatValues == null || stagingFloatValues.length < stagingSize) {
				stagingFloatValues = new float[stagingSize];
			}
			for(int i = 0; i < stagingSize; i++) {
				stagingFloatValues[i] = read32F();
			}
		} else {
			if(stagingIntValues == null || stagingIntValues.length < stagingSize) {
				stagingIntValues = new int[stagingSize];
			}
			for(int i = 0; i < stagingSize; i++) {
				stagingIntValues[i] = read32();
			}
		}
	}

	@Override
	protected void execute(MemoryStack stack) {
		if(isFloat) {
			switch (stagingSize) {
				case 1:
					property.set( stagingFloatValues[0] );
					break;
				case 2:
					property.set( stagingFloatValues[0], stagingFloatValues[1] );
					break;
				case 3:
					property.set( stagingFloatValues[0], stagingFloatValues[1], stagingFloatValues[2] );
					break;
				case 4:
					property.set( stagingFloatValues[0], stagingFloatValues[1], stagingFloatValues[2], stagingFloatValues[3] );
					break;
				default:
					property.set( stagingFloatValues );
					break;
			}
		} else {
			switch (stagingSize) {
				case 1:
					property.set( stagingIntValues[0] );
					break;
				case 2:
					property.set( stagingIntValues[0], stagingIntValues[1] );
					break;
				case 3:
					property.set( stagingIntValues[0], stagingIntValues[1], stagingIntValues[2] );
					break;
				case 4:
					property.set( stagingIntValues[0], stagingIntValues[1], stagingIntValues[2], stagingIntValues[3] );
					break;
				default:
					property.set( stagingIntValues );
					break;
			}
		}
		markUniformBufferDirty(property.getOwner());
	}

	@Override
	protected void print(StringBuilder sb) {
		// TODO: Something useful
	}
}
