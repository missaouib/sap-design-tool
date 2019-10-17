package com.rmurugaian.spring;

import org.apache.commons.io.IOUtils;
import org.apache.sshd.common.Session;
import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.file.FileSystemView;
import org.apache.sshd.common.file.SshFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryFileSystemViewFactory implements FileSystemFactory {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryFileSystemViewFactory.class);

    private HashMap<String, Object> fileSystemHashMap = new HashMap<>();
    private HashMap<String, String> userDirMap = new HashMap<>();
    private HashMap<Object, Long> lastModifiedMap = new HashMap<>();

    public InMemoryFileSystemViewFactory() {
    }

    public void resetFileSystem() {
        fileSystemHashMap = new HashMap<>();
        userDirMap = new HashMap<>();
        lastModifiedMap = new HashMap<>();
    }

    public void loadResourceIntoFileSystem(final String resourcePath, final String fileSystemPath) {
        try {
            final File f = new File(getClass().getResource(resourcePath).getPath());
            if (f.isDirectory()) {
                for (final File resource : f.listFiles()) {
                    copyInputStreamToFileSystem(
                        new FileInputStream(resource),
                        fileSystemPath + "/" + resource.getName());
                }
            } else {
                logger.info("Loading [" + f.getName() + "] into [" + fileSystemPath + "]");
                copyInputStreamToFileSystem(new FileInputStream(f), fileSystemPath);
            }
        } catch (final FileNotFoundException e) {
            logger.info("Unable to load resource into fileSystem: " + e.getMessage());
        }
    }

    public void copyInputStreamToFileSystem(final InputStream resourceStream, final String fileSystemPath) {
        if (resourceStream == null) {
            throw new RuntimeException("copyFileFromResourcesToFileSystem(): resourceStream is null");
        }

        try {
            createFile(fileSystemPath, IOUtils.toByteArray(resourceStream));
            resourceStream.close();
        } catch (final IOException e) {
            throw new RuntimeException("copyFileFromResourcesToFileSystem(): Unable to load resourceStream");
        }
    }

    @Override
    public FileSystemView createFileSystemView(final Session session) {
        return new InMemoryFileSystemView(this, session.getUsername());
    }

    // gets a directory, creating directory/subdirectories as necessary
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> getDir(final String path) {
        if (!path.startsWith("/")) {
            throw new RuntimeException("getDir(): path must start with /");
        }

        final String[] pathParts = path.split("/");
        HashMap<String, Object> current = fileSystemHashMap;
        for (int i = 1; i < pathParts.length; i++) {
            if (pathParts[i].isEmpty()) {
                continue;
            }

            final Object nextPart = current.get(pathParts[i]);
            if (nextPart == null) {
                final HashMap<String, Object> next = new HashMap<>();
                current.put(pathParts[i], next);
                current = next;
                setLastModified(next);
                setLastModified(current);
            } else if (nextPart instanceof HashMap) {
                current = (HashMap<String, Object>) nextPart;
            } else {
                throw new RuntimeException("Non-directory ["
                    + pathParts[i]
                    + "] encountered as part of path ["
                    + path
                    + "]");
            }
        }

        return current;
    }

    public boolean fileExists(final String path) {
        return (getFileOrDirectory(path) instanceof byte[]);
    }

    public void createFile(final String path, final byte[] contents) {
        if (!path.startsWith("/")) {
            throw new RuntimeException("createFile(): path must start with /");
        }

        final int i = path.lastIndexOf("/");
        final String pathPart = path.substring(0, i + 1);
        final String filePart = path.substring(i + 1);
        if (filePart.length() == 0) {
            throw new RuntimeException("createFile(): path must end with a filename");
        }
        final HashMap<String, Object> dir = getDir(pathPart);
        final Object o = dir.get(filePart);
        if ((o != null) && (o instanceof HashMap)) {
            throw new RuntimeException("createFile(): can't create file because dir already exists there");
        }
        if (contents != null) {
            final byte[] copyOfContents = contents.clone();
            dir.put(filePart, copyOfContents);
            setLastModified(copyOfContents);
        } else {
            dir.remove(filePart);
            setLastModified(dir);
        }
    }

    public void removeFile(final String path) {
        createFile(path, null);
    }

    public void printFileSystem() {
        printDir(null, null);
    }

    public void printDir(HashMap<String, Object> o, String part) {
        if (o == null) {
            o = getDir("/");
            part = "";
        }
        for (final String key : o.keySet()) {
            final Object value = o.get(key);
            if (value instanceof byte[]) {
                logger.info(part + "/" + key + ": " + ((byte[]) value).length + " bytes");
            } else {
                if (((HashMap<String, Object>) value).keySet().size() == 0) {
                    logger.info(part + "/" + key);
                } else {
                    printDir((HashMap<String, Object>) value, part + "/" + key);
                }
            }
        }
    }

    public long getLastModified(final Object o) {

        final Long l = lastModifiedMap.get(o);
        if (l != null) {
            return l.longValue();
        } else {
            return 0;
        }
    }

    public void setLastModified(final Object o) {
        setLastModified(o, System.currentTimeMillis());
    }

    public void setLastModified(final Object o, final long l) {
        lastModifiedMap.put(o, new Long(l));
    }

    public byte[] getFile(final String path) {
        if (!path.startsWith("/")) {
            throw new RuntimeException("getFile(): path must start with /");
        }

        final int i = path.lastIndexOf("/");
        final String pathPart = path.substring(0, i + 1);
        final String filePart = path.substring(i + 1);
        if (filePart.length() == 0) {
            throw new RuntimeException("getFile(): path must end with a filename");
        }
        final HashMap<String, Object> dir = getDir(pathPart);
        final Object o = dir.get(filePart);
        if ((o != null) && (o instanceof byte[])) {
            return (byte[]) o;
        }

        return null;
    }

    public Object getFileOrDirectory(final String path) {
        if (!path.startsWith("/")) {
            throw new RuntimeException("getFileOrDirectory(): path must start with /");
        }

        final String[] pathParts = path.split("/");
        HashMap<String, Object> current = fileSystemHashMap;

        if (pathParts.length == 0) {
            return current;
        }

        for (int i = 1; i < (pathParts.length - 1); i++) {
            if (pathParts[i].length() == 0) {
                continue;
            }

            final Object nextPart = current.get(pathParts[i]);
            if (nextPart instanceof HashMap) {
                current = (HashMap<String, Object>) nextPart;
            } else {
                return null;
            }
        }

        return current.get(pathParts[pathParts.length - 1]);
    }

    public String getBaseDir() {
        return "/";
    }

    public String getUserDir(final String username) {
        final String userDir = userDirMap.get(username);
        if (userDir != null) {
            return userDirMap.get(username);
        } else {
            return getBaseDir();
        }
    }

    public void setUserDir(final String username, final String path) {
        getDir(path);
        getUserDirMap().put(username, path);
    }

    public HashMap<String, String> getUserDirMap() {
        return userDirMap;
    }

    public void setUserDirMap(final HashMap<String, String> userDirMap) {
        this.userDirMap = userDirMap;
    }

    public class InMemoryFileSystemView implements FileSystemView {

        private final InMemoryFileSystemViewFactory inMemoryFileSystemViewFactory;
        private final String currentDir;
        private final String username;

        public InMemoryFileSystemView(
            final InMemoryFileSystemViewFactory inMemoryFileSystemViewFactory,
            final String username) {
            this.inMemoryFileSystemViewFactory = inMemoryFileSystemViewFactory;
            this.username = username;
            this.currentDir = inMemoryFileSystemViewFactory.getUserDir(username);
        }

        public String getBaseDir() {
            return inMemoryFileSystemViewFactory.getBaseDir();
        }

        public String getUserDir() {
            return inMemoryFileSystemViewFactory.getUserDir(username);
        }

        public String getCurrentDir() {
            return currentDir;
        }

        public String getUsername() {
            return username;
        }

        public String getCanonicalPath(final String originalPath) {
            String path = originalPath;
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            final String[] pathParts = path.split("/");

            final String[] newPathParts = new String[pathParts.length];
            int j = -1;
            for (int i = 1; i < pathParts.length; i++) {
                if (pathParts[i].equals("src/main")) {
                    j--;
                    if (j < -1) {
                        throw new RuntimeException("[" + originalPath + "] is not valid");
                    }
                } else if (!pathParts[i].equals(".") && !(pathParts[i].length() == 0)) {
                    newPathParts[++j] = pathParts[i];
                }
            }

            final StringBuilder newPath = new StringBuilder();
            if (j < 0) {
                newPath.append("/");
            } else {
                for (int i = 0; i <= j; i++) {
                    newPath.append("/").append(newPathParts[i]);
                }
            }

            return newPath.toString();
        }

        public HashMap<String, Object> getDir(final String path) {
            return inMemoryFileSystemViewFactory.getDir(path);
        }

        public Object getFileOrDirectory(final String path) {
            return inMemoryFileSystemViewFactory.getFileOrDirectory(path);
        }

        public void createFile(final String path, final byte[] contents) {
            inMemoryFileSystemViewFactory.createFile(path, contents);
        }

        public void removeFile(final String path) {
            inMemoryFileSystemViewFactory.removeFile(path);
        }

        public long getLastModified(final Object o) {
            return inMemoryFileSystemViewFactory.getLastModified(o);
        }

        public void setLastModified(final Object o) {
            inMemoryFileSystemViewFactory.setLastModified(o);
        }

        public void setLastModified(final Object o, final long l) {
            inMemoryFileSystemViewFactory.setLastModified(o, l);
        }

        @Override
        public SshFile getFile(final String filename) {
            String path;
            if (filename.equals("")) {
                path = getUserDir();
            } else if (filename.startsWith("~")) {
                path = getUserDir() + filename.substring(1);
            } else if (filename.startsWith("/")) {
                path = filename;
            } else {
                path = getCurrentDir() + "/" + filename;
            }

            path = getCanonicalPath(path);
            if (path == null) {
                logger.info("User [" + username + "] requests file [" + filename + "] that can't be mapped.");
                return null; // probably a better way to handle this
            }

            logger.info("User [" + username + "] requests file [" + filename + "] Mapped to [" + path + "]");
            return new InMemorySshFile(this, path);
        }

        @Override
        public SshFile getFile(final SshFile sshFile, final String filename) {
            logger.info("Alternate getFile *****");
            String path = ((InMemorySshFile) sshFile).getPath() + "/" + filename;

            path = getCanonicalPath(path);
            if (path == null) {
                logger.info("User [" + username + "] requests file [" + filename + "] that can't be mapped.");
                return null; // probably a better way to handle this
            }

            logger.info("User [" + username + "] requests file [" + filename + "] Mapped to [" + path + "]");
            return new InMemorySshFile(this, path);
        }

        @Override
        public FileSystemView getNormalizedView() {
            logger.info("getNormalizedView() called but not implemented yet");
            // no idea what this is even for at this point
            return this;
        }
    }

    public class InMemorySshFile implements SshFile {
        InMemoryFileSystemView inMemoryFileSystemView;
        String path;
        Object o;
        ByteArrayInputStream inputStream;
        ByteArrayOutputStream outputStream;

        public InMemorySshFile(final InMemoryFileSystemView inMemoryFileSystemView, final String path) {
            this.inMemoryFileSystemView = inMemoryFileSystemView;
            this.path = path;
            this.o = inMemoryFileSystemView.getFileOrDirectory(path);
            this.inputStream = null;
            this.outputStream = null;
        }

        public String getUsername() {
            return inMemoryFileSystemView.getUsername();
        }

        public String getPath() {
            return path;
        }

        public void setPath(final String path) {
            this.path = path;
        }

        public Object getObject() {
            return o;
        }

        public String getVirtualPath() {
            return path.substring(inMemoryFileSystemView.getBaseDir().length());
        }

        @Override
        public String getAbsolutePath() {
            final String absolutePath = "/" + getVirtualPath();
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] getAbsolutePath(): " + absolutePath);
            return absolutePath;
        }

        @Override
        public String getName() {
            String name = getVirtualPath();
            if (name.lastIndexOf('/') > 0) {
                name = name.substring(name.lastIndexOf('/') + 1);
            }
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] getName(): " + name);
            return name;
        }

        @Override
        public Map<Attribute, Object> getAttributes(final boolean b) throws IOException {
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] getAttributes()");
            return null;
        }

        @Override
        public void setAttributes(final Map<Attribute, Object> attributeObjectMap) throws IOException {
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] setAttributes()");
        }

        @Override
        public Object getAttribute(final Attribute attribute, final boolean b) throws IOException {
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] getAttribute()");
            return null;
        }

        @Override
        public void setAttribute(final Attribute attribute, final Object o) throws IOException {
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] setAttribute()");
        }

        @Override
        public String readSymbolicLink() throws IOException {
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] readSymbolicLink()");
            return null;
        }

        @Override
        public void createSymbolicLink(final SshFile sshFile) throws IOException {
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] createSymbolicLink()");
        }

        @Override
        public String getOwner() {
            final String owner = getUsername();
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] getOwner(): " + owner);
            return owner;
        }

        @Override
        public boolean isDirectory() {
            final boolean isDirectory = (o instanceof HashMap);
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] isDirectory(): " + isDirectory);
            return isDirectory;
        }

        @Override
        public boolean isFile() {
            final boolean isFile = (o instanceof byte[]);
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] isFile(): " + isFile);
            return isFile;
        }

        @Override
        public boolean doesExist() {
            final boolean doesExist = (o != null);
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] doesExist(): " + doesExist);
            return doesExist;
        }

        @Override
        public boolean isReadable() {
            final boolean isReadable = doesExist();
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] isReadable(): " + isReadable);
            return isReadable;
        }

        @Override
        public boolean isWritable() {
            final boolean isWriteable = true;
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] isWriteable(): " + isWriteable);
            return isWriteable;
        }

        @Override
        public boolean isExecutable() {
            final boolean isExecutable = false;
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] isExecutable(): " + isExecutable);
            return isExecutable;
        }

        @Override
        public boolean isRemovable() {
            final boolean isRemovable = doesExist();
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] isRemovable(): " + isRemovable);
            return isRemovable;
        }

        @Override
        public SshFile getParentFile() {
            final String parent = path.substring(0, path.lastIndexOf("/"));
            final SshFile parentSshFile = new InMemorySshFile(inMemoryFileSystemView, parent);
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] getParentFile(): " + parent);
            return parentSshFile;
        }

        @Override
        public long getLastModified() {
            final long lastModified = inMemoryFileSystemView.getLastModified(o);
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] getLastModified(): " + lastModified);
            return lastModified;
        }

        @Override
        public boolean setLastModified(final long l) {
            inMemoryFileSystemView.setLastModified(o, l);
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] setLastModified()");
            return true;
        }

        @Override
        public long getSize() {
            long size = 0;
            if (isFile()) {
                size = ((byte[]) o).length;
            }
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] getSize(): " + size);
            return size;
        }

        @Override
        public boolean mkdir() {
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] mkdir()");
            if (inMemoryFileSystemView.getFileOrDirectory(path) == null) {
                inMemoryFileSystemView.getDir(path);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean delete() {
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] delete()");
            if (doesExist()) {
                try {
                    handleClose();
                } catch (final IOException e) {
                    logger.info("delete(): " + e.getMessage());
                }
                inMemoryFileSystemView.removeFile(path);
                o = null;
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean create() throws IOException {
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] create()");
            final byte[] contents = new byte[0];
            inMemoryFileSystemView.createFile(path, contents);
            return true;
        }

        @Override
        public void truncate() throws IOException {
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] truncate()");
            final byte[] contents = new byte[0];
            inMemoryFileSystemView.createFile(path, contents);
        }

        @Override
        public boolean move(final SshFile sshFile) {
            logger.debug("User ["
                + getUsername()
                + "]["
                + getVirtualPath()
                + "] move(): destination="
                + sshFile.getAbsolutePath());
            try {
                final SshFile sourceFile = inMemoryFileSystemView.getFile(getVirtualPath());
                if (sourceFile.doesExist()) {
                    final Object o = ((InMemorySshFile) sourceFile).getObject();
                    String oPath = ((InMemorySshFile) sourceFile).getPath();
                    String oName = oPath;
                    if (oPath.lastIndexOf("/") > -1) {
                        oPath = oName.substring(0, oPath.lastIndexOf("/"));
                        oName = oName.substring(oName.lastIndexOf("/") + 1);
                    } else {
                        oPath = "/";
                    }
                    String nPath = ((InMemorySshFile) sshFile).getPath();
                    String nName = nPath;
                    if (nPath.lastIndexOf("/") > -1) {
                        nPath = nName.substring(0, nPath.lastIndexOf("/"));
                        nName = nName.substring(nName.lastIndexOf("/") + 1);
                    } else {
                        nPath = "/";
                    }
                    final HashMap<String, Object> oldDir = getDir(oPath);
                    final HashMap<String, Object> newDir = getDir(nPath);
                    oldDir.remove(oName);
                    newDir.put(nName, o);
                    ((InMemorySshFile) sshFile).setPath(nPath + "/" + nName);
                    inMemoryFileSystemView.setLastModified(oldDir);
                    inMemoryFileSystemView.setLastModified(newDir);
                }
            } catch (final Exception e) {
                logger.info(e.getMessage());
            }
            return true;
        }

        @Override
        public List<SshFile> listSshFiles() {
            final List<SshFile> fileList = new ArrayList<>();

            if (isDirectory()) {
                for (final String dirFile : ((HashMap<String, Object>) o).keySet()) {
                    final InMemorySshFile dirSshFile = new InMemorySshFile(
                        inMemoryFileSystemView,
                        path + "/" + dirFile);
                    fileList.add(dirSshFile);
                    logger.debug("User ["
                        + getUsername()
                        + "]["
                        + getVirtualPath()
                        + "] listSshFiles(): "
                        + "/"
                        + dirSshFile.getVirtualPath());
                }
                logger.debug("User ["
                    + getUsername()
                    + "]["
                    + getVirtualPath()
                    + "] listSshFiles(): "
                    + fileList.size()
                    + " files.");
            }
            return fileList;
        }

        @Override
        public OutputStream createOutputStream(final long l) throws IOException {
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] createOutputStream()");
            outputStream = new ByteArrayOutputStream();
            return outputStream;
        }

        @Override
        public InputStream createInputStream(final long l) throws IOException {
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] createInputStream()");
            if (o instanceof byte[]) {
                inputStream = new ByteArrayInputStream((byte[]) o);
                return inputStream;
            }
            return null;
        }

        @Override
        public void handleClose() throws IOException {
            logger.debug("User [" + getUsername() + "][" + getVirtualPath() + "] handleClose()");
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                final byte[] contents = outputStream.toByteArray();
                outputStream.close();
                inMemoryFileSystemView.createFile(path, contents);
            }
        }
    }
}
