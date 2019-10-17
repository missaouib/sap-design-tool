package com.rmurugaian.spring;

import org.apache.sshd.SshServer;
import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.keyprovider.ResourceKeyPairProvider;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.sftp.subsystem.SftpSubsystem;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.Security;
import java.util.Collections;

public class EmbeddedSftpServer {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedSftpServer.class);

    private static final String DEFAULT_SERVER_KEY_FILE = "sftp/default-sftp-server-key.asc";

    private SshServer sshd;
    private FileSystemFactory fileSystemFactory;
    private String serverKeyFilePath = DEFAULT_SERVER_KEY_FILE;

    public EmbeddedSftpServer(final int port) {
        Security.addProvider(new BouncyCastleProvider());

        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(port);
        sshd.setPasswordAuthenticator(getDefaultPasswordAuthenticator());

        sshd.setSessionFactory(new EmbeddedSftpSessionFactory());

        final NamedFactory<Command> factory = new SftpSubsystem.Factory();
        sshd.setSubsystemFactories(Collections.singletonList(factory));
    }

    public int getPort() {
        return sshd.getPort();
    }

    public void setServerKeyFilePath(final String resourcePath) {
        this.serverKeyFilePath = resourcePath;
    }

    public KeyPairProvider getDefaultKeyPairProvider() {
        return new ResourceKeyPairProvider(new String[]{serverKeyFilePath}, null, this.getClass().getClassLoader());
    }

    private PasswordAuthenticator getDefaultPasswordAuthenticator() {
        return (arg0, arg1, arg2) -> {
            return true; // tighten this up later as needed...this accepts any username/password
        };
    }

    public FileSystemFactory getFileSystemFactory() {
        if (fileSystemFactory == null) {
            fileSystemFactory = getDefaultFileSystemFactory();
            sshd.setFileSystemFactory(fileSystemFactory);
        }

        return fileSystemFactory;
    }

    public FileSystemFactory getDefaultFileSystemFactory() {
        return new InMemoryFileSystemViewFactory();
    }

    public void setFileSystemFactory(final FileSystemFactory fileSystemFactory) {
        this.fileSystemFactory = fileSystemFactory;
        sshd.setFileSystemFactory(fileSystemFactory);
    }

    public void start() {
        if (sshd.getKeyPairProvider() == null) {
            sshd.setKeyPairProvider(getDefaultKeyPairProvider());
        }
        if (fileSystemFactory == null) {
            fileSystemFactory = getDefaultFileSystemFactory();
            sshd.setFileSystemFactory(fileSystemFactory);
        }
        try {
            logger.info("sftp started.");
            sshd.start();
        } catch (final IOException e) {
            logger.error("START FAILED WITH ERROR MESSAGE {} ", e.getMessage());
        }
    }

    public void stop() {
        try {
            sshd.stop();
        } catch (final InterruptedException e) {
            logger.error("STOP FAILED WITH ERROR MESSAGE {}", e.getMessage());
        }
        sshd = null;
    }
}