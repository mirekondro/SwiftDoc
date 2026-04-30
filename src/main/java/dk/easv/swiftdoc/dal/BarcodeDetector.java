package dk.easv.swiftdoc.dal;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Detects barcodes inside TIFF files (US-09, task 2b).
 *
 * If a TIFF contains a barcode, the file is treated as a separator page —
 * the trigger to close the current Document and start a new one.
 * If no barcode is found, the file is a regular page in the current Document.
 *
 * Pipeline:
 *   TIFF bytes  →  ImageIO.read (via TwelveMonkeys)  →  BufferedImage
 *               →  ZXing LuminanceSource  →  BinaryBitmap
 *               →  ZXing MultiFormatReader  →  Result.getText() | NotFoundException
 *
 * Stateless and reentrant. Safe to share a single instance across threads,
 * though for now we just create one per ScanService.
 */
public class BarcodeDetector {

    private final MultiFormatReader reader;

    public BarcodeDetector() {
        this.reader = new MultiFormatReader();

        // TRY_HARDER tells ZXing to spend more CPU on detection — slower but
        // catches barcodes that are slightly rotated, low-contrast, or noisy.
        // Worth it: a missed barcode means a misclassified document, which is
        // user-visible. A fast scan that misses is worse than a slow scan that hits.
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);
    }

    /**
     * Look for a barcode in the given TIFF bytes.
     *
     * @param tiffBytes raw TIFF file content
     * @return Optional.of(barcodeText) if a barcode was detected,
     *         Optional.empty() if no barcode was found.
     * @throws IOException if the bytes can't be decoded as an image
     *         (bad TIFF, corrupted file, etc.) — distinct from "no barcode found"
     */
    public Optional<String> detectBarcode(byte[] tiffBytes) throws IOException {
        if (tiffBytes == null || tiffBytes.length == 0) {
            throw new IllegalArgumentException("tiffBytes must not be null or empty");
        }

        BufferedImage image;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(tiffBytes)) {
            image = ImageIO.read(bais);
        }
        if (image == null) {
            // ImageIO returns null when no registered reader can handle the
            // input. With TwelveMonkeys on the classpath this should never
            // happen for valid TIFFs, but treat it as a real error.
            throw new IOException("ImageIO could not decode TIFF bytes (no compatible reader)");
        }

        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        try {
            Result result = reader.decodeWithState(bitmap);
            return Optional.of(result.getText());
        } catch (NotFoundException notFound) {
            // No barcode in this image — that's a normal answer, not an error.
            // ZXing's design uses an exception for this, but it's a successful
            // negative result from our perspective.
            return Optional.empty();
        } finally {
            // Reset internal state between calls so a previous failure doesn't
            // affect the next image.
            reader.reset();
        }
    }
}
