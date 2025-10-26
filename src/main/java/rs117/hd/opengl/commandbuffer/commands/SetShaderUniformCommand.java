package rs117.hd.opengl.commandbuffer.commands;

import org.lwjgl.system.MemoryStack;
import rs117.hd.opengl.commandbuffer.BaseCommand;
import rs117.hd.opengl.shader.ShaderProgram;

public final class SetShaderUniformCommand extends BaseCommand {

	public ShaderProgram.UniformProperty property;
	public int[] intValues;
	public float[] floatValues;
	public boolean isFloat;

	private int stagingSize;
	private int[] stagingIntValues;
	private float[] stagingFloatValues;

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
			if (property instanceof ShaderProgram.Uniform1f) {
				((ShaderProgram.Uniform1f) property).set(stagingFloatValues[0]);
			} else if (property instanceof ShaderProgram.Uniform2f) {
				((ShaderProgram.Uniform2f) property).set(stagingFloatValues[0], stagingFloatValues[1]);
			} else if (property instanceof ShaderProgram.Uniform3f) {
				((ShaderProgram.Uniform3f) property).set(stagingFloatValues[0], stagingFloatValues[1], stagingFloatValues[2]);
			} else if (property instanceof ShaderProgram.Uniform4f) {
				((ShaderProgram.Uniform4f) property).set(
					stagingFloatValues[0],
					stagingFloatValues[1],
					stagingFloatValues[2],
					stagingFloatValues[3]
				);
			}
		} else {
			if (property instanceof ShaderProgram.UniformBool) {
				((ShaderProgram.UniformBool) property).set(stagingIntValues[0] == 1);
			} else if (property instanceof ShaderProgram.UniformTexture) {
				((ShaderProgram.UniformTexture) property).set(stagingIntValues[0]);
			} else if (property instanceof ShaderProgram.Uniform1i) {
				((ShaderProgram.Uniform1i) property).set(stagingIntValues[0]);
			} else if (property instanceof ShaderProgram.Uniform2i) {
				((ShaderProgram.Uniform2i) property).set(stagingIntValues[0], stagingIntValues[1]);
			} else if (property instanceof ShaderProgram.Uniform3i) {
				((ShaderProgram.Uniform3i) property).set(stagingIntValues[0], stagingIntValues[1], stagingIntValues[2]);
			} else if (property instanceof ShaderProgram.Uniform4i) {
				((ShaderProgram.Uniform4i) property).set(
					stagingIntValues[0],
					stagingIntValues[1],
					stagingIntValues[2],
					stagingIntValues[3]
				);
			}
		}
	}

	@Override
	protected void print(StringBuilder sb) {
		// TODO: Something useful
	}
}
