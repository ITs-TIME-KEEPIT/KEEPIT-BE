package com.keepit.image.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

public final class TestImages {
	private TestImages() {}

	public static byte[] encode(String format) {
		var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		try (var output = new ByteArrayOutputStream()) {
			if (!ImageIO.write(image, format, output)) {
				throw new IllegalArgumentException(format);
			}
			return output.toByteArray();
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}
}
