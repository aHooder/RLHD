package rs117.hd.overlays;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import rs117.hd.HdPlugin;
import rs117.hd.scene.MinimapRenderer;

@Singleton
public class MiniMapOverlay extends net.runelite.client.ui.overlay.Overlay {

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;


	@Inject
	private MinimapRenderer minimapRenderer;

	public MiniMapOverlay() {
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
	}

	public void setActive(boolean activate) {
		if (activate)
			overlayManager.add(this);
		else
			overlayManager.remove(this);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{

		BufferedImage image = new BufferedImage(20,20,1);


		if (minimapRenderer.minimiapImage != null) {
			image = minimapRenderer.minimiapImage;
		}

		graphics.drawImage(image, 0, 0, null);

		return new Dimension(image.getWidth(), image.getHeight());
	}

}
