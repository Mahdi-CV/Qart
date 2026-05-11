package io.github.scola.qart.web.engine;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

public class CuteR {

    private static final Logger LOG = Logger.getLogger(CuteR.class.getName());

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final int TRANSPARENT = 0x00000000;

    private static final int SCALE_NORMAL_QR = 10;
    private static final float FULL_LOGO_QR = 507.1f;
    private static final float LOGO_BACKGROUND = 140.7f;
    private static final float LOGO_SIZE = 126.7f;
    private static final int MAX_LOGO_SIZE = 1080;

    // Thread-safe encode result bundles the QR image with its pattern centers
    private static class EncodeResult {
        BufferedImage image;
        int[] patternCenters;
    }

    // Carries both the merged QR image and the scale factor used
    private static class ProductResult {
        BufferedImage image;
        int scaleQR;
    }

    // -------------------------------------------------------------------------
    // Public production methods
    // -------------------------------------------------------------------------

    public static BufferedImage ProductNormal(String txt, boolean colorful, int color) {
        EncodeResult encoded;
        try {
            encoded = encodeAsBitmap(txt, ErrorCorrectionLevel.H);
        } catch (WriterException e) {
            LOG.severe("encodeAsBitmap: " + e);
            return null;
        }
        BufferedImage qrImage = encoded.image;
        if (colorful && color != BLACK) {
            qrImage = replaceColor(qrImage, color);
        }
        return scaleImage(qrImage, qrImage.getWidth() * SCALE_NORMAL_QR,
                qrImage.getHeight() * SCALE_NORMAL_QR, false);
    }

    public static BufferedImage Product(String txt, BufferedImage input, boolean colorful, int color) {
        ProductResult r = productInternal(txt, input, colorful, color, 1.0f, 0, 0);
        return r == null ? null : r.image;
    }

    public static BufferedImage Product(String txt, BufferedImage input, boolean colorful, int color,
                                        float bgScale, int bgOffsetX, int bgOffsetY) {
        ProductResult r = productInternal(txt, input, colorful, color, bgScale, bgOffsetX, bgOffsetY);
        return r == null ? null : r.image;
    }

    private static ProductResult productInternal(String txt, BufferedImage input, boolean colorful, int color,
                                                 float bgScale, int bgOffsetX, int bgOffsetY) {
        EncodeResult encoded;
        try {
            encoded = encodeAsBitmap(txt, ErrorCorrectionLevel.H);
        } catch (WriterException e) {
            LOG.severe("encodeAsBitmap: " + e);
            return null;
        }

        BufferedImage qrImage = encoded.image;
        int[] patternCenters = encoded.patternCenters;

        if (colorful && color != BLACK) {
            qrImage = replaceColor(qrImage, color);
        }

        int inputSize = Math.max(input.getWidth(), input.getHeight());
        int scale = (int) Math.ceil(1.0 * inputSize / qrImage.getWidth());
        scale = Math.max(scale, 9);
        if (scale % 3 != 0) {
            scale += (3 - scale % 3);
        }

        int scaleQR = scale;
        BufferedImage scaledQRImage = scaleImage(qrImage,
                qrImage.getWidth() * scale, qrImage.getHeight() * scale, false);

        // The QR data area (excluding quiet zone on both sides)
        int imageSize = scaledQRImage.getWidth() - scaleQR * 4 * 2;

        // Scale the image to fill the QR data area, preserving aspect ratio
        BufferedImage resizedImage;
        if (input.getWidth() < input.getHeight()) {
            int newW = (int) (imageSize * bgScale);
            int newH = (int) (newW * (1.0 * input.getHeight() / input.getWidth()));
            resizedImage = scaleImage(input, Math.max(1, newW), Math.max(1, newH), true);
        } else {
            int newH = (int) (imageSize * bgScale);
            int newW = (int) (newH * (1.0 * input.getWidth() / input.getHeight()));
            resizedImage = scaleImage(input, Math.max(1, newW), Math.max(1, newH), true);
        }

        int patternGridSize = scaledQRImage.getWidth() - scaleQR * 4 * 2;
        int[][] pattern = new int[patternGridSize][patternGridSize];

        if (patternCenters != null) {
            for (int i = 0; i < patternCenters.length; i++) {
                for (int j = 0; j < patternCenters.length; j++) {
                    if (patternCenters[i] == 6 && patternCenters[j] == patternCenters[patternCenters.length - 1]
                            || (patternCenters[j] == 6 && patternCenters[i] == patternCenters[patternCenters.length - 1])
                            || (patternCenters[i] == 6 && patternCenters[j] == 6)) {
                        continue;
                    }
                    int initx = scale * (patternCenters[i] - 2);
                    int inity = scale * (patternCenters[j] - 2);
                    for (int x = initx; x < initx + scale * 5 && x < patternGridSize; x++) {
                        for (int y = inity; y < inity + scale * 5 && y < patternGridSize; y++) {
                            pattern[x][y] = 1;
                        }
                    }
                }
            }
        }

        // For B&W mode use clean grayscale (no Floyd-Steinberg dithering).
        // The center of each module retains the exact QR value; the surrounding
        // area just needs to look good, so smooth grayscale beats a harsh halftone.
        BufferedImage blackWhite = resizedImage;
        if (!colorful) {
            blackWhite = ConvertToBlackAndWhite(createContrast(blackWhite, 30, 0));
        }

        int finderGuard = scaleQR * 9;
        int timingStart = 6 * scaleQR;
        int timingEnd = 7 * scaleQR;
        float halfS = scale * 0.5f;

        for (int i = 0; i < imageSize; i++) {
            // Normalized Chebyshev distance from module centre on the i-axis (0=centre, 1=corner)
            float normDistX = Math.abs((i % scale) + 0.5f - halfS) / halfS;
            for (int j = 0; j < imageSize; j++) {
                float normDistY = Math.abs((j % scale) + 0.5f - halfS) / halfS;
                float normDist = Math.max(normDistX, normDistY);

                // Centre zone: preserve the original QR pixel so decoders sample correctly
                if (normDist <= 0.333f) {
                    continue;
                }
                // Protect timing patterns (row 6 and column 6 of the QR module grid)
                if ((i >= timingStart && i < timingEnd) || (j >= timingStart && j < timingEnd)) {
                    continue;
                }
                // Protect the three finder pattern corners with a safe margin
                if (i < finderGuard && (j < finderGuard || j > imageSize - (finderGuard + 1))) {
                    continue;
                }
                if (i > imageSize - (finderGuard + 1) && j < finderGuard) {
                    continue;
                }
                if (i < patternGridSize && j < patternGridSize && pattern[i][j] == 1) {
                    continue;
                }

                int srcX = i - bgOffsetX;
                int srcY = j - bgOffsetY;
                if (srcX < 0 || srcX >= blackWhite.getWidth() || srcY < 0 || srcY >= blackWhite.getHeight()) {
                    continue;
                }

                int imgPixel = blackWhite.getRGB(srcX, srcY);

                // Narrow blend zone just outside the centre dot: fade from QR colour to image.
                // This softens the hard edge between the dot and the background image.
                if (normDist <= 0.5f) {
                    float t = (normDist - 0.333f) / 0.167f; // 0 = QR, 1 = image
                    int qrPixel = scaledQRImage.getRGB(i + scaleQR * 4, j + scaleQR * 4);
                    imgPixel = blendPixels(imgPixel, qrPixel, t);
                }

                scaledQRImage.setRGB(i + scaleQR * 4, j + scaleQR * 4, imgPixel);
            }
        }

        ProductResult result = new ProductResult();
        result.image = scaledQRImage;
        result.scaleQR = scaleQR;
        return result;
    }

    public static BufferedImage ProductLogo(BufferedImage logo, String txt, boolean colorful, int color) {
        BufferedImage qrImage = ProductNormal(txt, colorful, color);
        if (qrImage == null) return null;

        int fullSize = qrImage.getWidth() - 4 * 2 * SCALE_NORMAL_QR;
        int finalSize = (int) (logo.getWidth() * FULL_LOGO_QR / LOGO_SIZE);
        finalSize = Math.min(finalSize, MAX_LOGO_SIZE);
        int scale = SCALE_NORMAL_QR;

        if (finalSize > fullSize) {
            scale = SCALE_NORMAL_QR * finalSize / fullSize;
            qrImage = scaleImage(qrImage,
                    qrImage.getWidth() * finalSize / fullSize,
                    qrImage.getHeight() * finalSize / fullSize, false);
            fullSize = finalSize;
        }

        int background = (int) (fullSize * LOGO_BACKGROUND / FULL_LOGO_QR);
        int logoSize = (int) (fullSize * LOGO_SIZE / FULL_LOGO_QR);

        BufferedImage white = new BufferedImage(background, background, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = white.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, background, background);
        g.dispose();

        int boundary = (background - logoSize) / 2;
        BufferedImage scaleLogo = scaleImage(logo, logoSize, logoSize, false);
        scaleLogo = fillBoundary(scaleLogo, boundary, WHITE);

        Graphics2D gw = white.createGraphics();
        gw.drawImage(scaleLogo, boundary, boundary, null);
        gw.dispose();

        white = fillBoundary(white, boundary, TRANSPARENT);

        Graphics2D gqr = qrImage.createGraphics();
        gqr.drawImage(white,
                scale * 4 + (fullSize - background) / 2,
                scale * 4 + (fullSize - background) / 2, null);
        gqr.dispose();

        return qrImage;
    }

    public static BufferedImage ProductEmbed(String txt, BufferedImage input, boolean colorful,
                                             int color, int x, int y, BufferedImage originBitmap) {
        return ProductEmbed(txt, input, colorful, color, x, y, originBitmap, 1.0f, 0, 0);
    }

    public static BufferedImage ProductEmbed(String txt, BufferedImage input, boolean colorful,
                                             int color, int x, int y, BufferedImage originBitmap,
                                             float bgScale, int bgOffsetX, int bgOffsetY) {
        int originalSize = input.getWidth();
        ProductResult pr = productInternal(txt, input, colorful, color, bgScale, bgOffsetX, bgOffsetY);
        if (pr == null) return null;

        BufferedImage qrBitmap = pr.image;
        int scaleQR = pr.scaleQR;

        double newScale = 1.0 * originalSize * scaleQR / (qrBitmap.getWidth() - 2 * 4 * scaleQR);
        int targetSize = qrBitmap.getWidth() * originalSize / (qrBitmap.getWidth() - 2 * 4 * scaleQR);
        qrBitmap = resizeQuiteZone(qrBitmap, newScale);
        qrBitmap = scaleImage(qrBitmap, targetSize, targetSize, false);

        BufferedImage result = copyImage(originBitmap);
        Graphics2D gc = result.createGraphics();
        gc.drawImage(qrBitmap, x - (int) (4 * newScale), y - (int) (4 * newScale), null);
        gc.dispose();
        return result;
    }

    // -------------------------------------------------------------------------
    // Image processing helpers
    // -------------------------------------------------------------------------

    public static BufferedImage convertBlackWhiteFull(BufferedImage img) {
        img = createContrast(img, 50, 30);
        img = ConvertToBlackAndWhite(img);
        img = convertGreyImgByFloyd2(img);
        return img;
    }

    public static BufferedImage ConvertToBlackAndWhite(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] pixels = src.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int a = (p >> 24) & 0xFF;
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            int grey = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            pixels[i] = (a << 24) | (grey << 16) | (grey << 8) | grey;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, pixels, 0, w);
        return out;
    }

    public static BufferedImage createContrast(BufferedImage src, double value, int brightness) {
        int width = src.getWidth();
        int height = src.getHeight();
        double contrast = Math.pow((100 + value) / 100, 2);
        BufferedImage bmOut = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = src.getRGB(x, y);
                int A = (pixel >> 24) & 0xFF;
                int R = (pixel >> 16) & 0xFF;
                int G = (pixel >> 8) & 0xFF;
                int B = pixel & 0xFF;

                R = (int) (((((R / 255.0) - 0.5) * contrast) + 0.5) * 255.0) + brightness;
                R = Math.max(0, Math.min(255, R));

                G = (int) (((((G / 255.0) - 0.5) * contrast) + 0.5) * 255.0) + brightness;
                G = Math.max(0, Math.min(255, G));

                B = (int) (((((B / 255.0) - 0.5) * contrast) + 0.5) * 255.0) + brightness;
                B = Math.max(0, Math.min(255, B));

                bmOut.setRGB(x, y, (A << 24) | (R << 16) | (G << 8) | B);
            }
        }
        return bmOut;
    }

    public static BufferedImage convertGreyImgByFloyd2(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int[] pixels = img.getRGB(0, 0, width, height, null, 0, width);
        int[] gray = new int[height * width];

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                gray[width * i + j] = pixels[width * i + j] & 0xFF;
            }
        }

        int divide = 16;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int g = gray[width * i + j];
                int newPixel = (g >> 7) * 255;
                int e = g - newPixel;
                pixels[width * i + j] = newPixel > 0 ? WHITE : BLACK;

                if (j + 1 < width) {
                    gray[width * i + j + 1] += e * 7 / divide;
                }
                if (j - 1 >= 0 && i + 1 < height) {
                    gray[width * (i + 1) + j - 1] += e * 3 / divide;
                }
                if (i + 1 < height) {
                    gray[width * (i + 1) + j] += e * 5 / divide;
                }
                if (j + 1 < width && i + 1 < height) {
                    gray[width * (i + 1) + j + 1] += e / divide;
                }
            }
        }

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, width, height, pixels, 0, width);
        return out;
    }

    public static BufferedImage replaceColor(BufferedImage qrImage, int color) {
        int w = qrImage.getWidth(), h = qrImage.getHeight();
        int[] pixels = qrImage.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < pixels.length; i++) {
            if (pixels[i] == BLACK) {
                pixels[i] = color;
            }
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, pixels, 0, w);
        return out;
    }

    public static BufferedImage rotateImage(BufferedImage source, float angleDegrees) {
        double rad = Math.toRadians(angleDegrees);
        int w = source.getWidth(), h = source.getHeight();
        double sin = Math.abs(Math.sin(rad)), cos = Math.abs(Math.cos(rad));
        int newW = (int) Math.floor(w * cos + h * sin);
        int newH = (int) Math.floor(h * cos + w * sin);
        BufferedImage out = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.translate((newW - w) / 2.0, (newH - h) / 2.0);
        g.rotate(rad, w / 2.0, h / 2.0);
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return out;
    }

    public static BufferedImage getResizedBitmap(BufferedImage source, float scaleX, float scaleY) {
        if (source == null) return null;
        int newW = (int) (source.getWidth() * scaleX);
        int newH = (int) (source.getHeight() * scaleY);
        return scaleImage(source, newW, newH, true);
    }

    // -------------------------------------------------------------------------
    // QR verification
    // -------------------------------------------------------------------------

    /**
     * Attempts to decode a QR code from the given image.
     * Returns the decoded text, or null if no QR code could be read.
     */
    public static String decode(BufferedImage image) {
        try {
            LuminanceSource source = new AwtLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            return new QRCodeReader().decode(bitmap, hints).getText();
        } catch (Exception e) {
            return null;
        }
    }

    /** Minimal LuminanceSource wrapping a BufferedImage — no extra dependencies. */
    private static final class AwtLuminanceSource extends LuminanceSource {
        private final byte[] luminances;

        AwtLuminanceSource(BufferedImage image) {
            super(image.getWidth(), image.getHeight());
            int w = image.getWidth(), h = image.getHeight();
            luminances = new byte[w * h];
            int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
            for (int i = 0; i < pixels.length; i++) {
                int p = pixels[i];
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                luminances[i] = (byte) ((r * 299 + g * 587 + b * 114) / 1000);
            }
        }

        @Override
        public byte[] getRow(int y, byte[] row) {
            int w = getWidth();
            if (row == null || row.length < w) row = new byte[w];
            System.arraycopy(luminances, y * w, row, 0, w);
            return row;
        }

        @Override
        public byte[] getMatrix() {
            return luminances.clone();
        }
    }

    // -------------------------------------------------------------------------
    // QR encoding
    // -------------------------------------------------------------------------

    private static EncodeResult encodeAsBitmap(String txt, ErrorCorrectionLevel level) throws WriterException {
        if (txt == null) return null;

        Map<EncodeHintType, Object> hints = null;
        String encoding = guessAppropriateEncoding(txt);
        if (encoding != null) {
            hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, encoding);
        }

        QRCode qrCode;
        try {
            qrCode = Encoder.encode(txt, level, hints);
        } catch (IllegalArgumentException iae) {
            return null;
        }

        int[] patternCenters = qrCode.getVersion().getAlignmentPatternCenters();
        BitMatrix result = renderResult(qrCode, 4);

        int width = result.getWidth();
        int height = result.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
            }
        }

        BufferedImage bitmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        bitmap.setRGB(0, 0, width, height, pixels, 0, width);

        EncodeResult er = new EncodeResult();
        er.image = bitmap;
        er.patternCenters = patternCenters;
        return er;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Linear blend: w1 fraction of p1, (1-w1) fraction of p2. */
    private static int blendPixels(int p1, int p2, float w1) {
        float w2 = 1f - w1;
        int a = Math.round(((p1 >> 24) & 0xFF) * w1 + ((p2 >> 24) & 0xFF) * w2);
        int r = Math.round(((p1 >> 16) & 0xFF) * w1 + ((p2 >> 16) & 0xFF) * w2);
        int g = Math.round(((p1 >> 8) & 0xFF) * w1 + ((p2 >> 8) & 0xFF) * w2);
        int b = Math.round((p1 & 0xFF) * w1 + (p2 & 0xFF) * w2);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static BufferedImage scaleImage(BufferedImage src, int newW, int newH, boolean smooth) {
        if (newW <= 0) newW = 1;
        if (newH <= 0) newH = 1;
        BufferedImage out = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        if (smooth) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        } else {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        }
        g.drawImage(src, 0, 0, newW, newH, null);
        g.dispose();
        return out;
    }

    private static BufferedImage copyImage(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage resizeQuiteZone(BufferedImage qrBitmap, double scale) {
        int size = qrBitmap.getWidth();
        int boundary = (int) (3.5 * scale);
        int[] pixels = qrBitmap.getRGB(0, 0, size, size, null, 0, size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i < boundary || i > size - (boundary + 1) || j < boundary || j > size - (boundary + 1)) {
                    pixels[i * size + j] = TRANSPARENT;
                }
            }
        }
        qrBitmap.setRGB(0, 0, size, size, pixels, 0, size);
        return qrBitmap;
    }

    private static BufferedImage fillBoundary(BufferedImage img, int boundary, int color) {
        int size = img.getWidth();
        int r = boundary;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i < r && j < r) {
                    if (Math.pow(r - i, 2) + Math.pow(r - j, 2) > Math.pow(r, 2)) {
                        img.setRGB(i, j, color);
                    }
                } else if (i < r && j > size - (r + 1)) {
                    if (Math.pow(r - i, 2) + Math.pow(size - (r + 1) - j, 2) > Math.pow(r, 2)) {
                        img.setRGB(i, j, color);
                    }
                } else if (i > size - (r + 1) && j < r) {
                    if (Math.pow(size - (r + 1) - i, 2) + Math.pow(r - j, 2) > Math.pow(r, 2)) {
                        img.setRGB(i, j, color);
                    }
                } else if (i > size - (r + 1) && j > size - (r + 1)) {
                    if (Math.pow(size - (r + 1) - i, 2) + Math.pow(size - (r + 1) - j, 2) > Math.pow(r, 2)) {
                        img.setRGB(i, j, color);
                    }
                }
            }
        }
        return img;
    }

    private static String guessAppropriateEncoding(CharSequence contents) {
        for (int i = 0; i < contents.length(); i++) {
            if (contents.charAt(i) > 0xFF) {
                return "UTF-8";
            }
        }
        return null;
    }

    private static BitMatrix renderResult(QRCode code, int quietZone) {
        ByteMatrix input = code.getMatrix();
        if (input == null) {
            throw new IllegalStateException();
        }
        int inputWidth = input.getWidth();
        int inputHeight = input.getHeight();
        int qrWidth = inputWidth + (quietZone * 2);
        int qrHeight = inputHeight + (quietZone * 2);
        int outputWidth = Math.max(0, qrWidth);
        int outputHeight = Math.max(0, qrHeight);
        int multiple = Math.max(1, Math.min(outputWidth / qrWidth, outputHeight / qrHeight));
        int leftPadding = (outputWidth - (inputWidth * multiple)) / 2;
        int topPadding = (outputHeight - (inputHeight * multiple)) / 2;
        BitMatrix output = new BitMatrix(outputWidth, outputHeight);
        for (int inputY = 0, outputY = topPadding; inputY < inputHeight; inputY++, outputY += multiple) {
            for (int inputX = 0, outputX = leftPadding; inputX < inputWidth; inputX++, outputX += multiple) {
                if (input.get(inputX, inputY) == 1) {
                    output.setRegion(outputX, outputY, multiple, multiple);
                }
            }
        }
        return output;
    }
}
