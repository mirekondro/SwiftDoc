package dk.easv.swiftdoc.dal;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Centralized TIFF decoding through ImageIO with plugin scanning.
 */
public final class TiffImageIO {
    static {
        // Ensure ImageIO discovers TwelveMonkeys readers on all platforms.
        ImageIO.scanForPlugins();
        ImageIO.setUseCache(false);
    }

    private TiffImageIO() {
    }

    public static BufferedImage readFirstImage(byte[] tiffBytes) throws IOException {
        if (tiffBytes == null || tiffBytes.length == 0) {
            throw new IllegalArgumentException("tiffBytes must not be null or empty");
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(tiffBytes);
             ImageInputStream iis = ImageIO.createImageInputStream(bais)) {
            if (iis == null) {
                throw new IOException("ImageIO could not create an ImageInputStream for TIFF bytes");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IOException("ImageIO has no TIFF reader registered");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IOException("ImageIO reader returned null for TIFF bytes");
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }
}

