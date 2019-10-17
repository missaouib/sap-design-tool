package com.rmurugaian.spring;

import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.server.session.SessionFactory;

public class EmbeddedSftpSessionFactory extends SessionFactory {
    @Override
    public void exceptionCaught(final IoSession ioSession, final Throwable cause) throws Exception {
        // silently eat exceptions caused by client closing connection
        if (!"An established connection was aborted by the software in your host machine".equals(cause.getMessage())) {
            super.exceptionCaught(ioSession, cause);
        }
    }
}
