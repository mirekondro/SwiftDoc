package dk.easv.swiftdoc.dal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts TIFF files from a ZIP byte array (US-09, task 2a).
 *
 * Input: raw bytes of a ZIP file (as returned by {@link ScanApiClient#fetchRandomBatch}).
 * Output: an ordered list of {@link TiffEntry} — one per TIFF file inside the ZIP.
 *
 * Filters out:
 *  - Directory entries
 *  - Non-TIFF files (anything not starting with TIFF magic bytes)
 *  - Empty entries
 *
 * Does NOT decode images or detect barcodes — that's {@code BarcodeDetector}'s job.
 */
public class TiffBatchUnzipper {

    /**
     * TIFF little-endian magic: "II*\0" = 0x49 0x49 0x2A 0x00.
     */
    private static final byte[] TIFF_MAGIC_LE = { 0x49, 0x49, 0x2A, 0x00 };

    /**
     * TIFF big-endian magic: "MM\0*" = 0x4D 0x4D 0x00 0x2A.
     */
    private static final byte[] TIFF_MAGIC_BE = { 0x4D, 0x4D, 0x00, 0x2A };

    /**
     * One TIFF file extracted from the ZIP.
     *
     * @param fileName name as it appeared inside the ZIP (e.g. "5.tiff")
     * @param data     raw TIFF bytes, ready to feed into ImageIO or a barcode reader
     */
    public record TiffEntry(String fileName, byte[] data) {}

    /**
     * Unzip a ZIP byte array and return all TIFF entries inside.
     *
     * @param zipBytes raw ZIP file bytes
     * @return list of TIFF entries in the order they appear in the ZIP.
     *         Empty list if the ZIP contains no TIFFs.
     * @throws IOException              if the bytes aren't a valid ZIP
     * @throws IllegalArgumentException if zipBytes is null or empty
     */
    public List<TiffEntry> extractTiffs(byte[] zipBytes) throws IOException {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException("zipBytes must not be null or empty");
        }

        List<TiffEntry> tiffs = new ArrayList<>();

        try (ByteArrayInputStream bais = new ByteArrayInputStream(zipBytes);
             ZipInputStream zis = new ZipInputStream(bais)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                byte[] entryBytes = readEntry(zis);

                if (entryBytes.length == 0) {
                    continue;
                }
                if (!looksLikeTiff(entryBytes)) {
                    // Not a TIFF — could be a manifest file, README, whatever.
                    // Silently skip; the API contract says ZIPs contain TIFFs,
                    // but we don't crash if it ever changes.
                    continue;
                }

                tiffs.add(new TiffEntry(entry.getName(), entryBytes));
            }
        }

        return tiffs;
    }

    /**
     * Read the current ZIP entry's bytes into memory.
     * ZipInputStream does not give us the entry size up front for streamed
     * ZIPs, so we use a growable buffer.
     */
    private byte[] readEntry(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = zis.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    /**
     * Check whether the first 4 bytes match TIFF magic (either endianness).
     */
    private boolean looksLikeTiff(byte[] data) {
        if (data.length < 4) return false;
        return startsWith(data, TIFF_MAGIC_LE) || startsWith(data, TIFF_MAGIC_BE);
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }
}
