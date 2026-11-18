package br.edu.atitus.productapi.storage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageTypeTest {

    @Test
    void detectsSupportedTypesByMagicBytes() {
        assertEquals(Optional.of(ImageType.JPEG), ImageType.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}));
        assertEquals(Optional.of(ImageType.PNG), ImageType.detect(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D}));
        assertEquals(Optional.of(ImageType.WEBP),
                ImageType.detect("RIFF\u0001\u0002\u0003\u0004WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void rejectsUnknownOrTruncatedContent() {
        assertEquals(Optional.empty(), ImageType.detect("GIF89a".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(Optional.empty(), ImageType.detect("RIFF0000WAVE".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(Optional.empty(), ImageType.detect(new byte[]{(byte) 0xFF, (byte) 0xD8}));
        assertEquals(Optional.empty(), ImageType.detect(new byte[0]));
    }
}
