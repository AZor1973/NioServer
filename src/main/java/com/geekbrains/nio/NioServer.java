package com.geekbrains.nio;


import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

// ls -> список файлов в текущей папке +
// cat file -> вывести на экран содержание файла +
// cd path -> перейти в папку
// touch file -> создать пустой файл
public class NioServer {

    private final ServerSocketChannel server;
    private final Selector selector;
    private final ByteBuffer buffer;
    private Path path = Paths.get("./");

    public NioServer() throws Exception {
        buffer = ByteBuffer.allocate(256);
        server = ServerSocketChannel.open(); // accept -> SocketChannel
        server.bind(new InetSocketAddress(8189));
        selector = Selector.open();
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);

        while (server.isOpen()) {
            selector.select();
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept();
                }
                if (key.isReadable()) {
                    handleRead(key);
                }
                iterator.remove();
            }
        }
    }

    private void handleRead(SelectionKey key) throws Exception {

        SocketChannel channel = (SocketChannel) key.channel();

        StringBuilder sb = new StringBuilder();

        while (true) {
            int read = channel.read(buffer);
            if (read == -1) {
                channel.close();
                return;
            }
            if (read == 0) {
                break;
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                sb.append((char) buffer.get());
            }
            buffer.clear();
        }
        String str = sb.toString().trim();
        String result = "[From server]: " + sb;
        File dir = new File(String.valueOf(path));
        String[] files = dir.list();
        String trim = "";
        if (!str.isBlank() && str.contains(" "))
        trim = str.substring(str.indexOf(" ")).trim();
        if (str.startsWith("cat ")) {
            catFile(channel, Path.of(trim), trim);
            getCurrentPass(channel);
        } else if (str.equals("ls")) {
            listDir(channel, files);
            getCurrentPass(channel);
        } else if (str.startsWith("touch ")) {
            touch(channel, path, trim);
            getCurrentPass(channel);
        } else if (str.startsWith("cd ")) {
            checkDir(trim);
            getCurrentPass(channel);
        } else if (str.isEmpty()) {
            getCurrentPass(channel);
        } else {
            System.out.println(result);
            if (!result.contains("�") || !result.contains("\uFFFF"))
            channel.write(ByteBuffer.wrap(result.getBytes(StandardCharsets.UTF_8)));
            getCurrentPass(channel);
        }
    }

    private void listDir(SocketChannel channel, String[] files) throws IOException {
        assert files != null;
        channel.write(ByteBuffer.wrap(String.join("\n\r", files).concat("\n\r").getBytes()));
    }

    private void touch(SocketChannel channel, Path path, String fileName) throws IOException {
        if (!Files.exists(path.resolve(fileName))) {
            Files.createFile(path.resolve(fileName));
            channel.write(ByteBuffer.wrap("Done\n\r".getBytes(StandardCharsets.UTF_8)));
        } else {
            channel.write(ByteBuffer.wrap("File is already exists\n\r".getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void getCurrentPass(SocketChannel channel) throws IOException {
        channel.write(ByteBuffer.wrap(path.toAbsolutePath().toString().concat(">").getBytes(StandardCharsets.UTF_8)));
    }

    private void checkDir(String toPath) {
        if (Files.isDirectory(Path.of(toPath))) {
            path = Paths.get(toPath);
        }
    }

    private void handleAccept() throws Exception {
        SocketChannel channel = server.accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ, "Hello world!");
    }

    private void catFile(SocketChannel channel, Path pathFile, String fileName) throws IOException {
        System.out.println(pathFile);
        if (pathFile.toString().equals(fileName)){
            pathFile = Path.of(path.toString(), fileName);
        }
        if (Files.isRegularFile(pathFile) && Files.isReadable(pathFile)) {
            channel.write(ByteBuffer.wrap(Files.readAllBytes(pathFile)));
            channel.write(ByteBuffer.wrap("\n\r".getBytes()));
        }
    }

    public static void main(String[] args) throws Exception {
        new NioServer();
    }
}

