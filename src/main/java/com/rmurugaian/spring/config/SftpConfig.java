package com.rmurugaian.spring.config;

import com.jcraft.jsch.ChannelSftp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.file.remote.RemoteFileTemplate;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.util.Assert;

/**
 * @author rmurugaian 2019-10-17
 */
@Configuration
@ConfigurationProperties(prefix = "sftp")
public class SftpConfig {

    private static final String ENCODING_UTF_8 = "UTF-8";

    @NestedConfigurationProperty
    private final SftpContext server = new SftpContext();

    public SftpContext getServer() {
        return server;
    }

    @Bean
    public SessionFactory<ChannelSftp.LsEntry> defaultSftpSessionFactory() {
        final DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory();
        factory.setHost(server.getHost());
        factory.setPassword(server.getPassword());
        factory.setPort(server.getPort());
        factory.setUser(server.getUser());
        factory.setAllowUnknownKeys(true);
        return factory;
    }

    @Bean
    public RemoteFileTemplate<ChannelSftp.LsEntry> sftpRemoteFileTemplate(
        @Value("${sftp.server.update.remotePath}") final String sftpRemotePath) {
        Assert.hasLength(sftpRemotePath, "order remotePath is missing.");

        final SftpRemoteFileTemplate template = new SftpRemoteFileTemplate(defaultSftpSessionFactory());
        template.setCharset(ENCODING_UTF_8);
        template.setAutoCreateDirectory(false);
        template.setRemoteDirectoryExpression(new LiteralExpression(sftpRemotePath));
        template.setTemporaryFileSuffix(".writing");
        template.setUseTemporaryFileName(true);

        return template;
    }

    @Bean
    @ConditionalOnProperty(name = "sftp.server.order.enabled", havingValue = "true", matchIfMissing = true)
    public SftpHealthCheck sftpHealthCheck(
        final  RemoteFileTemplate<ChannelSftp.LsEntry> sftpRemoteFileTemplate,
        @Value("${sftp.server.update.remotePath}") final String sftpRemotePath) {
        return new SftpHealthCheck(sftpRemoteFileTemplate, sftpRemotePath);
    }
}
