import dk.easv.swiftdoc.dal.ScanApiClient;
import dk.easv.swiftdoc.dal.TiffBatchUnzipper;

public static void main(String[] args) throws Exception {
    byte[] zip = new ScanApiClient().fetchRandomBatch();
    System.out.println("Got " + zip.length + " bytes from API");

    List<TiffBatchUnzipper.TiffEntry> tiffs = new TiffBatchUnzipper().extractTiffs(zip);
    System.out.println("Extracted " + tiffs.size() + " TIFFs:");
    for (TiffBatchUnzipper.TiffEntry t : tiffs) {
        System.out.println("  - " + t.fileName() + " (" + t.data().length + " bytes)");
    }
}