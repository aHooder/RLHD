package rs117.hd.opengl;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.BufferUtils;
import rs117.hd.HdPlugin;
import rs117.hd.opengl.compute.OpenCLManager;
import rs117.hd.utils.buffer.GLBuffer;

import static org.lwjgl.opengl.GL31C.*;

@Slf4j
@RequiredArgsConstructor
public abstract class UniformBuffer {
	protected enum PropertyType {
		Int(4, 4, 1),
		IVec2(8, 8, 2),
		IVec3(12, 16, 3),
		IVec4(16, 16, 4),

		IntArray(4, 16, 1, true),
		IVec2Array(8, 16, 2, true),
		IVec3Array(12, 16, 3, true),
		IVec4Array(16, 16, 4, true),

		Float(4, 4, 1),
		FVec2(8, 8, 2),
		FVec3(12, 16, 3),
		FVec4(16, 16, 4),

		FloatArray(4, 16, 1, true),
		FVec2Array(8, 16, 2, true),
		FVec3Array(12, 16, 3, true),
		FVec4Array(16, 16, 4, true),

		Mat3(36, 16, 9),
		Mat4(64, 16, 16);

		private final int size;
		private final int alignment;
		private final int elementSize;
		private final int elementCount;
		private final boolean isArray;

		PropertyType(int size, int alignment, int elementCount) {
			this.size = size;
			this.alignment = alignment;
			this.elementSize = size / elementCount;
			this.elementCount = elementCount;
			this.isArray = false;
		}

		PropertyType(int size, int alignment, int elementCount, boolean isArray) {
			this.size = size;
			this.alignment = alignment;
			this.elementSize = size / elementCount;
			this.elementCount = elementCount;
			this.isArray = isArray;
		}
	}

	@AllArgsConstructor
	@RequiredArgsConstructor
	public static class Property {
		private UniformBuffer owner;
		private int position;
		private final PropertyType type;
		private final String name;

		public final void set(int value) {
			if (type != PropertyType.Int) {
				log.warn("{} - Incorrect Setter(int) called for Property: {}", owner.glBuffer.name, name);
				return;
			}

			if (owner.data.getInt(position) != value) {
				owner.data.putInt(position, value);
				owner.markWaterLine(position, type.size);
			}
		}

		public final void set(int... values) {
			if (type != PropertyType.IVec2 && type != PropertyType.IVec3 && type != PropertyType.IVec4) {
				log.warn("{} - Incorrect Setter(int[]) called for Property: {}", owner.glBuffer.name, name);
				return;
			}

			if (values == null) {
				log.warn("{} - Setter(int[]) was provided with null value for Property: {}", owner.glBuffer.name, name);
				return;
			}

			if ((type.isArray && (values.length % type.elementCount) != 0) || values.length != type.elementCount) {
				log.warn(
					"{} - Setter(int[]) was provided with incorrect number of elements for Property: {}",
					owner.glBuffer.name,
					name
				);
				return;
			}

			if (owner.data == null) {
				log.warn("{} - Hasn't been initialized yet!", owner.glBuffer.name);
				return;
			}

			int elementCount = type.isArray ? values.length : type.elementCount;
			for (int elementIdx = 0, offset = position; elementIdx < elementCount; elementIdx++, offset += type.elementSize) {
				if (owner.data.getInt(offset) != values[elementIdx]) {
					owner.data.putInt(offset, values[elementIdx]);
					owner.markWaterLine(offset, type.elementSize);
				}
			}
		}

		public final void set(float value) {
			if (type != PropertyType.Float) {
				log.warn("{} - Incorrect Setter(float) called for Property: {}", owner.glBuffer.name, name);
				return;
			}

			if (owner.data.getFloat(position) != value) {
				log.debug(
					"Updating {} (buffer {}) property {} from {} to {}",
					owner.glBuffer.name,
					owner.glBuffer.id,
					name,
					owner.data.getFloat(position),
					value
				);
				owner.data.putFloat(position, value);
				owner.markWaterLine(position, type.size);
			}
		}

		public final void set(float... values) {
			if (type != PropertyType.FVec2 &&
				type != PropertyType.FVec3 &&
				type != PropertyType.FVec4 &&
				type != PropertyType.Mat3 &&
				type != PropertyType.Mat4
			) {
				log.warn("{} - Incorrect Setter(float[]) called for Property: {}", owner.glBuffer.name, name);
				return;
			}

			if (values == null) {
				log.warn("{} - Setter(float[]) was provided with null value for Property: {}", owner.glBuffer.name, name);
				return;
			}

			if ((type.isArray && (values.length % type.elementCount) != 0) || values.length != type.elementCount) {
				log.warn(
					"{} - Setter(float[]) was provided with incorrect number of elements for Property: {}",
					owner.glBuffer.name,
					name
				);
				return;
			}

			if (owner.data == null) {
				log.warn("{} - Hasn't been initialized yet!", owner.glBuffer.name);
				return;
			}

			int elementCount = type.isArray ? values.length : type.elementCount;
			for (int elementIdx = 0, offset = position; elementIdx < elementCount; elementIdx++, offset += type.elementSize) {
				if (owner.data.getFloat(offset) != values[elementIdx]) {
					owner.data.putFloat(offset, values[elementIdx]);
					owner.markWaterLine(offset, type.elementSize);
				}
			}
		}
	}

	public interface CreateStructProperty<T extends StructProperty> {
		T create();
	}

	public abstract static class StructProperty {
		protected List<Property> properties = new ArrayList<>();

		protected final Property addProperty(PropertyType type, String name) {
			Property property = new Property(type, name);
			properties.add(property);
			return property;
		}
	}

	public final GLBuffer glBuffer;

	private int size;
	private int dirtyLowTide = Integer.MAX_VALUE;
	private int dirtyHighTide = 0;
	private ByteBuffer data;

	public UniformBuffer(String name, int glUsage) {
		glBuffer = new GLBuffer("UBO " + name, GL_UNIFORM_BUFFER, glUsage);
	}

	protected final <T extends StructProperty> T addStruct(T newStructProp) {
		for (Property property : newStructProp.properties)
			appendToBuffer(property);

		// Structs need to align to 16 bytes
		size += (int) (16 - (size % 16)) % 16;

		newStructProp.properties.clear();
		return newStructProp;
	}

	protected final <T extends StructProperty> T[] addStructs(T[] newStructPropArray, CreateStructProperty<T> createFunction) {
		for (int i = 0; i < newStructPropArray.length; i++) {
			newStructPropArray[i] = createFunction.create();
			addStruct(newStructPropArray[i]);
		}

		return newStructPropArray;
	}

	protected Property addProperty(PropertyType type, String name) {
		return appendToBuffer(new Property(type, name));
	}

	private Property appendToBuffer(Property property) {
		property.owner = this;

		int padding = (int) (property.type.alignment - (size % property.type.alignment)) % property.type.alignment;
		property.position = (int) size + padding;

		size += property.type.size + padding;

		return property;
	}

	private void markWaterLine(int position, int size) {
		dirtyLowTide = Math.min(dirtyLowTide, position);
		dirtyHighTide = Math.max(dirtyHighTide, position + size);
	}

	public final void initialize(int uniformBlockIndex) {
		initialize(uniformBlockIndex, null);
	}

	public final void initialize(int uniformBlockIndex, OpenCLManager clManager) {
		if (data != null)
			destroy();

		glBuffer.initialize(clManager);
		glBuffer.ensureCapacity(size);
		glBindBufferBase(GL_UNIFORM_BUFFER, uniformBlockIndex, glBuffer.id);

		data = BufferUtils.createByteBuffer(size);
	}

	public final void upload() {
		if (data == null || dirtyHighTide - dirtyLowTide <= 0)
			return;
		HdPlugin.checkGLErrors();

		data.position(dirtyLowTide);
		data.limit(dirtyHighTide);

		glBindBuffer(GL_UNIFORM_BUFFER, glBuffer.id);
		HdPlugin.checkGLErrors();
		System.out.println("uploading " + glBuffer.name + " offset " + dirtyLowTide + " size " + data.remaining() + " to buffer sized at "
						   + size);
		glBufferSubData(GL_UNIFORM_BUFFER, dirtyLowTide, data);
		HdPlugin.checkGLErrors();
		glBindBuffer(GL_UNIFORM_BUFFER, 0);
		HdPlugin.checkGLErrors();

		data.clear();

		dirtyLowTide = Integer.MAX_VALUE;
		dirtyHighTide = 0;

		HdPlugin.checkGLErrors();
	}

	public final void destroy() {
		if (data == null)
			return;

		glBuffer.destroy();
		data = null;
	}
}
