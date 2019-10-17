package com.rmurugaian.spring.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * @author rmurugaian 2019-10-17
 */
@Component
public class DefaultFileProcessor implements FileProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultFileProcessor.class);

    @Override
    public void handleUpdate(final File sftpFile) {
        logger.warn("File FReceived ******* {}", sftpFile.getName());
        try {
            Files.readAllLines(sftpFile.toPath())
                .forEach(logger::warn);
        } catch (final IOException e) {
            logger.error("error {}", e.getMessage());
        }
    }
}
