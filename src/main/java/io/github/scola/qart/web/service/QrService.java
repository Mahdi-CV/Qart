package io.github.scola.qart.web.service;

import io.github.scola.qart.web.engine.CuteR;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class QrService {

    public record GenerateResult(byte[] png, String decodedText) {}

    public GenerateResult generate(String text, String mode, boolean colorful, String hexColor,
                                   MultipartFile image, MultipartFile bgImage,
                                   int embedX, int embedY,
                                   float bgScale, int bgOffsetX, int bgOffsetY,
                                   int outputScale) throws IOException {

        int color = parseHexColor(hexColor);
        BufferedImage result;

        switch (mode.toUpperCase()) {
            case "PICTURE" -> {
                BufferedImage img = readImage(image);
                result = CuteR.Product(text, img, colorful, color, bgScale, bgOffsetX, bgOffsetY);
            }
            case "LOGO" -> {
                BufferedImage img = readImage(image);
                result = CuteR.ProductLogo(img, text, colorful, color);
            }
            case "EMBED" -> {
                BufferedImage img = readImage(image);
                BufferedImage bg = readImage(bgImage);
                result = CuteR.ProductEmbed(text, img, colorful, color, embedX, embedY, bg, bgScale, bgOffsetX, bgOffsetY);
            }
            default -> result = CuteR.ProductNormal(text, colorful, color);
        }

        if (result == null) {
            throw new IllegalStateException("QR generation failed");
        }

        // Verify before upscaling (smaller image decodes faster and more reliably)
        String decoded = CuteR.decode(result);

        // Upscale for print using nearest-neighbor to keep QR module edges sharp
        if (outputScale > 1) {
            int newW = result.getWidth() * outputScale;
            int newH = result.getHeight() * outputScale;
            BufferedImage upscaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = upscaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(result, 0, 0, newW, newH, null);
            g.dispose();
            result = upscaled;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(result, "PNG", baos);
        return new GenerateResult(baos.toByteArray(), decoded);
    }

    private BufferedImage readImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required for this mode");
        }
        BufferedImage img = ImageIO.read(file.getInputStream());
        if (img == null) {
            throw new IllegalArgumentException("Could not read image file: " + file.getOriginalFilename());
        }
        return img;
    }

    private int parseHexColor(String hex) {
        if (hex == null || hex.isEmpty()) return 0xFF000000;
        try {
            int rgb = Integer.parseInt(hex.replace("#", ""), 16);
            return 0xFF000000 | rgb;
        } catch (NumberFormatException e) {
            return 0xFF000000;
        }
    }
}
