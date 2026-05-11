package io.github.scola.qart.web.controller;

import io.github.scola.qart.web.service.QrService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/qr")
public class QrController {

    private final QrService qrService;

    public QrController(QrService qrService) {
        this.qrService = qrService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(
            @RequestParam String text,
            @RequestParam(defaultValue = "NORMAL") String mode,
            @RequestParam(defaultValue = "false") boolean colorful,
            @RequestParam(defaultValue = "#000000") String color,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) MultipartFile bgImage,
            @RequestParam(defaultValue = "0") int embedX,
            @RequestParam(defaultValue = "0") int embedY,
            @RequestParam(defaultValue = "1.0") float bgScale,
            @RequestParam(defaultValue = "0") int bgOffsetX,
            @RequestParam(defaultValue = "0") int bgOffsetY,
            @RequestParam(defaultValue = "1") int outputScale) {

        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body("Text cannot be empty");
        }

        try {
            QrService.GenerateResult gr = qrService.generate(text, mode, colorful, color, image, bgImage, embedX, embedY, bgScale, bgOffsetX, bgOffsetY, Math.min(Math.max(outputScale, 1), 4));
            boolean verified = gr.decodedText() != null && gr.decodedText().equals(text);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"qart.png\"")
                    .header("X-QR-Verified", String.valueOf(verified))
                    .header("X-QR-Decoded", gr.decodedText() != null ? gr.decodedText() : "")
                    .contentType(MediaType.IMAGE_PNG)
                    .body(gr.png());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException | IllegalStateException e) {
            return ResponseEntity.internalServerError().body("Generation failed: " + e.getMessage());
        }
    }
}
