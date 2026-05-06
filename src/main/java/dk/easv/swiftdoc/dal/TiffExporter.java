package dk.easv.swiftdoc.dal;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Writes a list of images into a single multi-page TIFF file.
 *
 * Pure mechanical — no business logic, no DB, no model classes.
 * Knows only: input images, output file, compression scheme.
 *
 * Uses the TwelveMonkeys TIFF writer which is already on the classpath
 * for read support; it also handles writing.
 */
public class TiffExporter {

    /**
     * LZW is lossless and supported by every TIFF reader. For pure B&W
     * scans, "CCITT T.6" would produce smaller files but is lossy on color
     * pages. LZW is the safer default.
     */
    private static final String COMPRESSION_LZW = "LZW";

    /**
     * Write a multi-page TIFF.
     *
     * @param pages       images to include, in order. Must contain at least
     *                    one element.
     * @param outputFile  where to write. Will be overwritten if it exists.
     * @throws IOException if no TIFF writer is available, or any write fails
     */
    public void writeMultiPage(List<BufferedImage> pages, File outputFile) throws IOException {
        if (pages == null || pages.isEmpty()) {
            throw new IllegalArgumentException("pages must not be null or empty");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("outputFile must not be null");
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("TIFF");
        if (!writers.hasNext()) {
            throw new IOException("No TIFF writer registered with ImageIO. "
                    + "Check that the TwelveMonkeys imageio-tiff dependency is on the classpath.");
        }
        ImageWriter writer = writers.next();

        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionType(COMPRESSION_LZW);

        try (ImageOutputStream output = ImageIO.createImageOutputStream(outputFile)) {
            writer.setOutput(output);

            // Multi-page sequence: prepareWriteSequence -> writeToSequence per
            // image -> endWriteSequence. This is the standard ImageIO pattern
            // for any container format that holds multiple images.
            writer.prepareWriteSequence(null);
            for (BufferedImage page : pages) {
                writer.writeToSequence(new IIOImage(page, null, null), params);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }
}
