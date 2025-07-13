package rs117.hd.utils.buffer;

import rs117.hd.HdPlugin;
import rs117.hd.opengl.compute.ComputeMode;
import rs117.hd.opengl.compute.OpenCLManager;

import static org.lwjgl.opencl.CL10.*;

public class SharedGLBuffer extends GLBuffer {
	public final int clUsage;

	private OpenCLManager clManager;
	public long clId;

	public SharedGLBuffer(String name, int target, int glUsage, int clUsage) {
		super(name, target, glUsage);
		this.clUsage = clUsage;
	}

	@Override
	public void initialize(OpenCLManager clManager) {
		this.clManager = clManager;
		super.initialize(clManager);
	}

	@Override
	public void destroy() {
		if (clId != 0) {
			clReleaseMemObject(clId);
			clId = 0;
		}

		super.destroy();
	}

	@Override
	public void ensureCapacity(int offset, long numBytes) {
		super.ensureCapacity(offset, numBytes);

		if (HdPlugin.computeMode == ComputeMode.OPENCL)
			clManager.recreateCLBuffer(this);
	}
}
