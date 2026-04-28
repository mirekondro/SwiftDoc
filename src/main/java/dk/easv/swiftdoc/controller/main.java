public static void main(String[] args) throws Exception {
    // 1. Verify TwelveMonkeys: list all formats ImageIO can now read
    System.out.println("ImageIO readers available:");
    for (String fmt : javax.imageio.ImageIO.getReaderFormatNames()) {
        System.out.println("  - " + fmt);
    }

    // 2. Verify ZXing: just instantiate the main reader class
    com.google.zxing.MultiFormatReader reader = new com.google.zxing.MultiFormatReader();
    System.out.println("\nZXing reader instantiated: " + reader.getClass().getName());
}