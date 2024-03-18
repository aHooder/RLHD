package rs117.hd.test.utils;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import rs117.hd.data.materials.Material;
import rs117.hd.utils.ResourcePath;

import static rs117.hd.scene.TextureManager.SUPPORTED_IMAGE_EXTENSIONS;
import static rs117.hd.scene.TextureManager.TEXTURE_PATH;

@Slf4j
public class TextureAverageColorTest {
	public static final File DIR = new File("./textureAverageColor/");

	public static void main(String[] args) {
		if (DIR.exists()) {
			if (!DIR.delete()) {
				log.info("Unable to delete {}:", DIR.getAbsolutePath());
				System.exit(0);
			}
		}
		if (DIR.mkdirs()) {
			for (Material material : Material.values()) {
				try {
					String textureName = material.name().toLowerCase();
					for (String ext : SUPPORTED_IMAGE_EXTENSIONS) {
						ResourcePath path = TEXTURE_PATH.resolve(textureName + "." + ext);
						Color color = calculateAverageHSL(path.loadImage());
						BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
						for (int y = 0; y < 50; y++) {
							for (int x = 0; x < 50; x++) {
								image.setRGB(x, y, color.getRGB());
							}
						}
						File outputFile = new File(DIR, material.name() + "." + ext);
						ImageIO.write(image, "png", outputFile);
					}
				} catch (Exception ignored) {
				}
			}
		}
	}

	private static Color calculateAverageHSL(BufferedImage image) {
		BufferedImage scaledImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = scaledImage.createGraphics();
		g.drawImage(image, 0, 0, 1, 1, null);
		g.dispose();

		int pixel = scaledImage.getRGB(0, 0);
		int red = (pixel >> 16) & 0xff;
		int green = (pixel >> 8) & 0xff;
		int blue = pixel & 0xff;

		return new Color(red, green, blue);
	}
}
