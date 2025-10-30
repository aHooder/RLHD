package rs117.hd.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.lwjgl.opengl.ARBDrawIndirect.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL11.glColorMask;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;

public class RenderState {
	private final List<GLState> states = new ArrayList<>();

	public final GLBindVAO vao = addState(GLBindVAO::new);
	public final GLBindEBO ebo = addState(GLBindEBO::new);
	public final GLBindUBO ubo = addState(GLBindUBO::new);
	public final GLBindIDO ido = addState(GLBindIDO::new);
	public final GLDepthMask depthMask = addState(GLDepthMask::new);
	public final GLColorMask colorMask = addState(GLColorMask::new);
	public final GLEnable enable = addState(GLEnable::new);
	public final GLDisable disable = addState(GLDisable::new);

	public void apply() {
		for (GLState state : states)  state.apply();
	}

	public void reset() {
		for (GLState state : states) state.reset();
	}

	private <T extends GLState> T addState(Supplier<T> supplier) {
		T state = supplier.get();
		state.owner = this;
		states.add(state);
		return state;
	}

	public static abstract class GLState {
		RenderState owner;
		boolean hasValue;
		boolean hasApplied;
		void reset() { hasValue = hasApplied = false; }
		public void apply() {
			if (hasValue) {
				internalApply();
				hasValue = false;
				hasApplied = true;
			}
		}
		abstract void internalApply();
	}

	public static class GLBindVAO extends GLState {
		private int value, appliedValue;
		public void set(int vao) {
			hasValue = true;
			this.value = vao;
		}
		@Override void internalApply() {
			if (!hasApplied || value != appliedValue) {
				glBindVertexArray(value);
				appliedValue = value;
			}
		}
	}

	public static class GLBindEBO extends GLState {
		private int value, appliedValue;
		public void set(int ebo) {
			hasValue = true;
			this.value = ebo;
		}
		@Override void internalApply() {
			if (!hasApplied || value != appliedValue) {
				glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, value);
				appliedValue = value;
			}
		}
	}

	public static class GLBindUBO extends GLState {
		private int value, appliedValue;
		public void set(int ebo) {
			hasValue = true;
			this.value = ebo;
		}
		@Override void internalApply() {
			if (!hasApplied || value != appliedValue) {
				glBindBuffer(GL_UNIFORM_BUFFER, value);
				appliedValue = value;
			}
		}
	}

	public static class GLBindIDO extends GLState {
		private int value, appliedValue;
		public void set(int ido) {
			hasValue = true;
			this.value = ido;
		}
		@Override void internalApply() {
			if (!hasApplied || value != appliedValue) {
				glBindBuffer(GL_DRAW_INDIRECT_BUFFER , value);
				appliedValue = value;
			}
		}
	}

	public static class GLDepthMask extends GLState {
		private boolean value, appliedValue;
		public void set(boolean enabled) {
			hasValue = true;
			this.value = enabled;
		}
		@Override void internalApply() {
			if (!hasApplied || value != appliedValue) {
				glDepthMask(value);
				appliedValue = value;
			}
		}
	}

	public static class GLColorMask extends GLState {
		private boolean red, green, blue, alpha;
		public void set(boolean r, boolean g, boolean b, boolean a) {
			hasValue = true;
			red = r; green = g; blue = b; alpha = a;
		}
		@Override void internalApply() {
			glColorMask(red, green, blue, alpha);
		}
	}

	public static class GLEnable extends GLState {
		private final Set<Integer> targets = new HashSet<>();
		public void set(int target) {
			hasValue = true;
			targets.add(target);
			owner.disable.targets.remove(target);
		}
		@Override void internalApply() {
			for (int target : targets) glEnable(target);
			targets.clear();
		}
		@Override void reset() {
			super.reset();
			targets.clear();
		}
	}

	public static class GLDisable extends GLState {
		private final Set<Integer> targets = new HashSet<>();
		public void set(int target) {
			hasValue = true;
			targets.add(target);
			owner.enable.targets.remove(target);
		}
		@Override void internalApply() {
			for (int target : targets) glDisable(target);
			targets.clear();
		}
		@Override void reset() {
			super.reset();
			targets.clear();
		}
	}
}
