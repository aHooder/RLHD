package rs117.hd.renderer;

import java.io.IOException;
import net.runelite.api.hooks.*;
import rs117.hd.opengl.shader.ShaderException;
import rs117.hd.opengl.shader.ShaderIncludes;

public interface Renderer extends DrawCallbacks {
	default void initialize() {}
	default void destroy() {}
	default void initializePrograms(ShaderIncludes includes) throws ShaderException, IOException {}
	default void destroyPrograms() {}
	default void reuploadScene() {}
	default boolean isLoadingScene() {
		return false;
	}
	default void waitUntilIdle() {}
}
