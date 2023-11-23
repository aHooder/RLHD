package rs117.hd.overlays;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import rs117.hd.HdPlugin;
import rs117.hd.opengl.shader.ShaderException;
import rs117.hd.utils.AABB;
import rs117.hd.utils.Mat4;

import static org.lwjgl.opengl.GL20C.*;
import static rs117.hd.HdPlugin.TEXTURE_UNIT_BASE;
import static rs117.hd.HdPlugin.TEXTURE_UNIT_SHADOW_MAP;

@Slf4j
@Singleton
public class ShadowMapOverlay extends Overlay {
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private HdPlugin plugin;

	private boolean isActive;

	public ShadowMapOverlay() {
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPosition(OverlayPosition.TOP_LEFT);
		setResizable(true);
	}

	public void setActive(boolean activate) {
		if (activate == isActive)
			return;
		isActive = activate;

		if (activate) {
			overlayManager.add(this);
			plugin.enableShadowMapOverlay = true;
			eventBus.register(this);
		} else {
			overlayManager.remove(this);
			plugin.enableShadowMapOverlay = false;
			eventBus.unregister(this);
		}

		clientThread.invoke(() -> {
			try {
				plugin.recompilePrograms();
			} catch (ShaderException | IOException ex) {
				log.error("Error while recompiling shaders:", ex);
				plugin.stopPlugin();
			}
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
			glUseProgram(plugin.glUiProgram);
			int uniBounds = glGetUniformLocation(plugin.glUiProgram, "shadowMapOverlayDimensions");
			if (uniBounds != -1)
				glUniform4i(uniBounds, 0, 0, 0, 0);
		}
	}

	@Override
	public Dimension render(Graphics2D g) {
		var overlay = getBounds();

		clientThread.invoke(() -> {
			if (plugin.glUiProgram == 0 || client.getGameState().getState() < GameState.LOGGED_IN.getState())
				return;

			glUseProgram(plugin.glUiProgram);
			int uniShadowMap = glGetUniformLocation(plugin.glUiProgram, "shadowMap");
			if (uniShadowMap != -1)
				glUniform1i(uniShadowMap, TEXTURE_UNIT_SHADOW_MAP - TEXTURE_UNIT_BASE);
			int uniBounds = glGetUniformLocation(plugin.glUiProgram, "shadowMapOverlayDimensions");
			if (uniBounds != -1) {
				int canvasWidth = client.getCanvasWidth();
				int canvasHeight = client.getCanvasHeight();
				float scaleX = 1;
				float scaleY = 1;
				if (client.isStretchedEnabled()) {
					var stretchedDims = client.getStretchedDimensions();
					scaleX = (float) stretchedDims.width / canvasWidth;
					scaleY = (float) stretchedDims.height / canvasHeight;
				}
				glUniform4i(uniBounds,
					(int) Math.floor((overlay.x + 1) * scaleX), (int) Math.floor((canvasHeight - overlay.height - overlay.y) * scaleY),
					(int) Math.ceil((overlay.width - 1) * scaleX), (int) Math.ceil((overlay.height - 1) * scaleY)
				);
			}

			plugin.checkGLErrors();
		});

		g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		g.clipRect(0, 0, overlay.width, overlay.height);

		int[][] points = new int[8][2];
		float[] proj = { 0, 0, 0, 1 };

		var sceneContext = plugin.getSceneContext();
		if (sceneContext != null) {
			g.setStroke(new BasicStroke(.6f));
			var bounds = AABB.fromArray(sceneContext.bounds);
			float[][] corners = {
				{ bounds.minX, bounds.minY, bounds.minZ, 1 },
				{ bounds.minX, bounds.minY, bounds.maxZ, 1 },
				{ bounds.maxX, bounds.minY, bounds.maxZ, 1 },
				{ bounds.maxX, bounds.minY, bounds.minZ, 1 },
				{ bounds.minX, bounds.maxY, bounds.minZ, 1 },
				{ bounds.minX, bounds.maxY, bounds.maxZ, 1 },
				{ bounds.maxX, bounds.maxY, bounds.maxZ, 1 },
				{ bounds.maxX, bounds.maxY, bounds.minZ, 1 },
			};

			// Project and draw scene bounding box
			for (int i = 0; i < 8; i++) {
				Mat4.projectVec(proj, plugin.lightProjectionMatrix, corners[i]);
				points[i][0] = (int) ((proj[0] * .5f + .5f) * overlay.width);
				points[i][1] = (int) ((proj[1] * -.5f + .5f) * overlay.height);
			}
			g.setColor(Color.MAGENTA);
			for (int i = 0; i < 4; i++) {
				// Draw bottom plane
				drawLine(g, points[i], points[(i + 1) % 4]);
				// Draw top plane
				drawLine(g, points[i + 4], points[((i + 1) % 4) + 4]);
				// Draw left and right
				drawLine(g, points[i], points[i + 4]);
			}
		}

		for (int i = 0; i < 8; i++) {
			var corner = plugin.frustumCornersWorldSpace[i];
			System.arraycopy(corner, 0, proj, 0, 3);
			Mat4.projectVec(proj, plugin.lightProjectionMatrix, proj);
			points[i][0] = (int) ((proj[0] * .5f + .5f) * overlay.width);
			points[i][1] = (int) ((proj[1] * -.5f + .5f) * overlay.height);
		}

		g.setStroke(new BasicStroke(1.2f));

		// Draw line in camera view direction
		g.setColor(Color.CYAN);
		int[] centerNear = new int[2];
		int[] centerFar = new int[2];
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 2; j++) {
				centerNear[j] += points[i][j];
				centerFar[j] += points[i + 4][j];
			}
		}
		for (int i = 0; i < 2; i++) {
			centerNear[i] /= 4;
			centerFar[i] /= 4;
		}
		drawLine(g, centerNear, centerFar);

		// Draw far plane diagonals
		g.setColor(Color.BLUE);
		drawLine(g, points[4], points[6]);
		drawLine(g, points[5], points[7]);

		for (int i = 0; i < 4; i++) {
			// Draw near plane
			g.setColor(Color.CYAN);
			drawLine(g, points[i], points[(i + 1) % 4]);
			// Draw far plane
			g.setColor(Color.BLUE);
			drawLine(g, points[i + 4], points[((i + 1) % 4) + 4]);
			// Draw right plane in red, left in green
			g.setColor(i < 2 ? Color.GREEN : Color.RED);
			drawLine(g, points[i], points[i + 4]);
		}

		g.setColor(Color.BLACK);
		g.setStroke(new BasicStroke(1));
		g.drawRect(0, 0, overlay.width, overlay.height);

		return getPreferredSize() == null ? new Dimension(256, 256) : getPreferredSize();
	}

	private void drawLine(Graphics2D g, int[] from, int[] to) {
		g.drawLine(from[0], from[1], to[0], to[1]);
	}
}
