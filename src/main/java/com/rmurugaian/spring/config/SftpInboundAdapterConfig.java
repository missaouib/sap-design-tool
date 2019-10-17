package com.rmurugaian.spring.config;

import com.jcraft.jsch.ChannelSftp;
import com.rmurugaian.spring.service.FileProcessor;
import com.rmurugaian.spring.util.FileUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.dsl.Sftp;

import java.io.File;
import java.io.IOException;

/**
 * @author rmurugaian 2019-10-17
 */
@Configuration
@ConfigurationProperties(prefix = "sftp.server")
public class SftpInboundAdapterConfig {

    @NestedConfigurationProperty
    private final SftpAdapterConfig update = new SftpAdapterConfig();

    public SftpAdapterConfig getUpdate() {
        return update;
    }

    private final SessionFactory<ChannelSftp.LsEntry> sftpSessionFactory;
    private final FileProcessor fileProcessor;

    public SftpInboundAdapterConfig(
        final SessionFactory<ChannelSftp.LsEntry> sftpSessionFactory,
        final FileProcessor fileProcessor) {

        this.sftpSessionFactory = sftpSessionFactory;
        this.fileProcessor = fileProcessor;
    }

    @Bean
    public IntegrationFlow sftpInboundFlow() throws IOException {

        if (update.isClearLocalDir) {
            FileUtils.clearLocalDir(update.localPath);
        }

        return IntegrationFlows
            .from(
                Sftp.inboundAdapter(sftpSessionFactory)
                    .preserveTimestamp(true)
                    .deleteRemoteFiles(true)
                    .regexFilter(update.filterExpr)
                    .localDirectory(new File(update.localPath))
                    .autoCreateLocalDirectory(true)
                    .remoteDirectory(update.remotePath),
                e -> {
                    e.id("sftpInboundAdapter")
                        .autoStartup(true)
                        .poller(Pollers.fixedRate(update.pollingFrequency)
                            .maxMessagesPerPoll(1));
                })
            .channel(updateFileChannel())
            .get();
    }

    @Bean
    public DirectChannel updateFileChannel() {
        return new DirectChannel();
    }

    @Bean
    public IntegrationFlow orderUpdateFlow() {
        return IntegrationFlows.from(updateFileChannel())
            .handle(fileProcessor, "handleUpdate")
            .get();
    }

    private static class SftpAdapterConfig {
        private String remotePath;
        private int pollingFrequency;
        private String localPath;
        private String filterExpr;
        private boolean isClearLocalDir = true;

        public String getRemotePath() {
            return remotePath;
        }

        public void setRemotePath(final String remotePath) {
            this.remotePath = remotePath;
        }

        public int getPollingFrequency() {
            return pollingFrequency;
        }

        public void setPollingFrequency(final int pollingFrequency) {
            this.pollingFrequency = pollingFrequency;
        }

        public String getLocalPath() {
            return localPath;
        }

        public void setLocalPath(final String localPath) {
            this.localPath = localPath;
        }

        public String getFilterExpr() {
            return filterExpr;
        }

        public void setFilterExpr(final String filterExpr) {
            this.filterExpr = filterExpr;
        }

        public boolean isClearLocalDir() {
            return isClearLocalDir;
        }

        public void setClearLocalDir(final boolean clearLocalDir) {
            isClearLocalDir = clearLocalDir;
        }
    }
}
