package dk.easv.swiftdoc.dal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP client for the student TIFF API (US-09, task 1).
 *
 * Single responsibility: call /getRandomFile, return the raw response body
 * as a byte array. The body is a ZIP file containing one or more TIFFs
 * (some of which may be barcode separator pages).
 *
 * Does NOT:
 *  - unzip the response (task 2 / TiffBatchUnzipper)
 *  - detect barcodes (task 2 / BarcodeDetector)
 *  - touch the database (task 3 / FileDAO)
 *  - show UI dialogs (task 5 / controller layer)
 *
 * Lives in dal/ because this is data acquisition — the same architectural
 * role as a DAO, just talking to HTTP instead of JDBC.
 */
public class ScanApiClient {

    /**
     * Base URL of the API. Hardcoded for Sprint 1; could move to
     * config.properties later if we deploy to multiple environments.
     */
    private static final String BASE_URL =
            "https://studentiffapi-production.up.railway.app";

    private static final String ENDPOINT = "/getRandomFile";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;

    public ScanApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * Fetch a random batch of TIFF files from the API.
     *
     * @return raw ZIP bytes (the response body). The ZIP contains one or
     *         more TIFF files in arbitrary order. Caller is responsible
     *         for unzipping and barcode detection.
     * @throws IOException          on network failure or non-200 response
     * @throws InterruptedException if the calling thread is interrupted
     */
    public byte[] fetchRandomBatch() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + ENDPOINT))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<byte[]> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        int status = response.statusCode();
        if (status != 200) {
            throw new IOException(
                    "Scan API returned HTTP " + status + " (expected 200). "
                            + "Body length: " + response.body().length + " bytes");
        }

        byte[] body = response.body();
        if (body.length == 0) {
            throw new IOException("Scan API returned an empty body");
        }

        return body;
    }
}
