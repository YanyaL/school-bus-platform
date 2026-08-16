package com.schoolbus.transportquery.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ReadOnlyMapperXmlTest {

    @Test
    void mapperXmlMustContainOnlySelectStatements() throws IOException {
        Path mapperDir = Path.of("src/main/resources/mapper");
        assertThat(mapperDir).isDirectory();

        try (Stream<Path> files = Files.list(mapperDir)) {
            files.filter(path -> path.toString().endsWith(".xml"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path).toLowerCase(Locale.ROOT);
                            assertThat(content)
                                    .as(path.toString())
                                    .doesNotContain("<insert")
                                    .doesNotContain("<update")
                                    .doesNotContain("<delete");
                            assertThat(content).contains("<select");
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
        }
    }
}
