package com.rmurugaian.spring

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate
import org.springframework.test.context.ContextConfiguration
import spock.lang.Shared
import spock.lang.Specification

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

/**
 * @author rmurugaian 2019-10-17
 */
@ContextConfiguration
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = [SftpPollingApplication])
@Slf4j
class FileProcessingSpec extends Specification {

    @Shared
    def EmbeddedSftpServer server

    @Shared
    def InMemoryFileSystemViewFactory fileSystemFactory

    @Autowired
    DefaultSftpSessionFactory sftpSessionFactory

    @Autowired
    SftpRemoteFileTemplate sftpTemplate

    @Value('${sftp.server.update.localPath}')
    String sftpLocalPath

    @Value('${sftp.server.update.remotePath}')
    String sftpRemotePath

    def setupSpec() {
        server = new EmbeddedSftpServer(0)
        fileSystemFactory = (InMemoryFileSystemViewFactory) server.fileSystemFactory // default is in memory
        fileSystemFactory.getDir("/incoming")
        fileSystemFactory.setUserDir("b2s", "/")
        server.start()
    }

    def cleanupSpec() {
        server.stop()
    }

    def setup() {
        sftpSessionFactory.port = server.port
    }

    def 'Test wireup'() {
        expect:
        server
        fileSystemFactory
        sftpSessionFactory
        sftpTemplate
    }

    def "placing file and verify Inbound Flow"() {
        when:
        log.info("Uploading sample update file to SFTP")
        fileSystemFactory.copyInputStreamToFileSystem(new ClassPathResource('/testdata/updateFile1.dat').inputStream, "${sftpRemotePath}/updateFile1.dat")

        then:
        Thread.sleep(500000)
        server
    }
}