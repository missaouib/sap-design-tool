package com.rmurugaian.spring.config;

import com.jcraft.jsch.ChannelSftp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.integration.file.remote.RemoteFileTemplate;

public class SftpHealthCheck implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(SftpHealthCheck.class);

    private final RemoteFileTemplate<ChannelSftp.LsEntry> sftpFileTemplate;

    private final String sftpRemotePath;

    public SftpHealthCheck(
        final RemoteFileTemplate<ChannelSftp.LsEntry> sftpRemoteFileTemplate,
        final String sftpRemotePath) {

        this.sftpFileTemplate = sftpRemoteFileTemplate;
        this.sftpRemotePath = sftpRemotePath;
    }

    @Override
    public Health health() {

        Health.Builder builder = new Health.Builder().withDetail("remotePath", sftpRemotePath);
        try {
            final boolean exists = sftpFileTemplate.exists(sftpRemotePath);
            builder = builder.withDetail("remotePathExists", exists);
            return exists ? builder.up().build() : builder.down().build();
        } catch (final RuntimeException e) {
            logger.error("FTP health check failed: {}", e.getMessage());
            return builder.down(e).build();
        }
    }
}