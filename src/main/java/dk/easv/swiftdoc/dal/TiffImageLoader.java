package dk.easv.swiftdoc.dal;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Converts TIFF bytes into a JavaFX {@link Image} ready for an ImageView.
 *
 * Why this exists: JavaFX's own image loader does NOT understand TIFF.
 * We bridge through Java AWT's ImageIO (which can read TIFF thanks to the
 * TwelveMonkeys plugin already on the classpath) and then convert the
 * resulting BufferedImage to a JavaFX Image with SwingFXUtils.
 *
 * Stateless. Safe to share / call from any thread, BUT the resulting
 * JavaFX Image must be assigned to an ImageView only on the JavaFX
 * Application thread (Platform.runLater).
 */
public class TiffImageLoader {

    /**
     * Decode TIFF bytes into a JavaFX Image.
     *
     * @param tiffBytes raw TIFF data (e.g. from a saved File row)
     * @return the loaded image, ready to set on an ImageView
     * @throws IOException if ImageIO cannot decode the bytes
     */
    public Image load(byte[] tiffBytes) throws IOException {
        if (tiffBytes == null || tiffBytes.length == 0) {
            throw new IllegalArgumentException("tiffBytes must not be null or empty");
        }

        BufferedImage awtImage;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(tiffBytes)) {
            awtImage = ImageIO.read(bais);
        }
        if (awtImage == null) {
            throw new IOException("ImageIO could not decode TIFF bytes (no compatible reader found)");
        }

        // SwingFXUtils.toFXImage takes an optional WritableImage to reuse;
        // passing null tells it to allocate a fresh one.
        return SwingFXUtils.toFXImage(awtImage, null);
    }
}
