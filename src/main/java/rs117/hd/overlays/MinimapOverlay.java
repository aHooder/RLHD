package rs117.hd.overlays;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import rs117.hd.scene.MinimapRenderer;

@Singleton
public class MinimapOverlay extends net.runelite.client.ui.overlay.Overlay {
	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MinimapRenderer minimapRenderer;

	public MinimapOverlay() {
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
	}

	public void setActive(boolean activate) {
		if (activate) {
			overlayManager.add(this);
		} else {
			overlayManager.remove(this);
		}
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		BufferedImage image = new BufferedImage(20,20,1);

		if (minimapRenderer.miniMapImage != null)
			image = minimapRenderer.miniMapImageCircle;

		graphics.drawImage(image, 0, 0, null);

		return new Dimension(image.getWidth(), image.getHeight());
	}
}
