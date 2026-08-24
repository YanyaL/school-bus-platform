package com.schoolbus.bookingservice.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BookingServiceMustNotTouchPaymentRecordTest {

    @Test
    void mapperXmlAndJavaAnnotationsMustNotReferencePaymentRecord()
            throws IOException {
        Path root = Path.of("src/main");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".java")
                                || name.endsWith(".xml");
                    })
                    .forEach(path -> {
                        try {
                            String content = Files.readString(
                                    path,
                                    StandardCharsets.UTF_8
                            );
                            if (content.contains("payment_record")
                                    || content.contains("PaymentRefundLookupMapper")) {
                                offenders.add(path.toString());
                            }
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
        }
        assertThat(offenders)
                .as("booking-service must not reference payment_record")
                .isEmpty();
    }
}
