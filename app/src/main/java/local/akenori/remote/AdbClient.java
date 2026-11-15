package local.akenori.remote;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import javax.crypto.Cipher;
import javax.net.SocketFactory;

/** A single, explicitly selected classic ADB/TCP connection. No discovery or arbitrary shell UI. */
public final class AdbClient implements Closeable {
    static final int CNXN = command("CNXN"), AUTH = command("AUTH"), OPEN = command("OPEN");
    static final int OKAY = command("OKAY"), WRTE = command("WRTE"), CLSE = command("CLSE");
    private volatile Socket socket;
    private volatile boolean closed;
    private InputStream in;
    private OutputStream out;
    private int sequence = 1;

    public interface Status { void update(String message); }

    public String connect(String host, int port, SocketFactory factory, KeyPair key, Status status) throws Exception {
        Socket connection = factory.createSocket();
        socket = connection;
        if (closed) { connection.close(); throw new IOException("Подключение отменено"); }
        try {
            connection.connect(new InetSocketAddress(host, port), 4000);
            connection.setTcpNoDelay(true);
            connection.setSoTimeout(45000);
            in = connection.getInputStream(); out = connection.getOutputStream();
            send(CNXN, 0x01000000, 4096, utf8("host::\0"));
            boolean signatureSent = false, publicKeySent = false;
            for (int step = 0; step < 8; step++) {
                Packet packet = read(in);
                if (packet.command == CNXN) {
                    connection.setSoTimeout(12000);
                    return new String(packet.data, StandardCharsets.UTF_8).replace("\0", "");
                }
                if (packet.command != AUTH || packet.arg0 != 1 || packet.data.length != 20)
                    throw new IOException("Этот порт не поддерживает классическое подключение ADB");
                if (!signatureSent) {
                    send(AUTH, 2, 0, signToken(key, packet.data));
                    signatureSent = true;
                } else if (!publicKeySent) {
                    send(AUTH, 3, 0, utf8(Base64.getEncoder().encodeToString(encodePublicKey(key)) + " projector-remote@phone\0"));
                    publicKeySent = true;
                    status.update("Разреши подключение «projector-remote@phone» на экране проектора. Ожидание до 45 секунд…");
                } else {
                    throw new IOException("Проектор не разрешил подключение. Подтверди запрос на его экране и повтори.");
                }
            }
            throw new IOException("Не удалось завершить подключение ADB");
        } catch (Exception e) { close(); throw e; }
    }

    public String shell(String command) throws IOException {
        if (closed || socket == null) throw new IOException("Нет подключения");
        int local = sequence++, remote = 0;
        boolean opened = false;
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        send(OPEN, local, 0, utf8("shell:" + command + "\0"));
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < deadline) {
            Packet p = read(in);
            // adbd may echo the host's close after the next sequential stream has opened.
            if (p.command == CLSE && p.arg1 > 0 && p.arg1 < local) continue;
            if (p.arg1 != local) throw new IOException("Неверный идентификатор канала ADB");
            if (p.command == OKAY && !opened) { remote = p.arg0; opened = true; }
            else if (p.command == WRTE && opened && p.arg0 == remote) {
                if (response.size() + p.data.length > 65536) throw new IOException("Слишком большой ответ проектора");
                response.write(p.data);
                send(OKAY, local, remote, new byte[0]);
            } else if (p.command == CLSE) {
                send(CLSE, local, p.arg0, new byte[0]);
                if (!opened) throw new IOException("Проектор запретил команды управления");
                return new String(response.toByteArray(), StandardCharsets.UTF_8).trim();
            } else throw new IOException("Неожиданный ответ ADB");
        }
        throw new SocketTimeoutException("Проектор не завершил команду");
    }

    public void key(int code) throws IOException {
        Set<Integer> allowed = new HashSet<>(Arrays.asList(3,4,19,20,21,22,23,24,25,82,85,164,176,223));
        if (!allowed.contains(code)) throw new IOException("Неизвестная кнопка");
        String result = shell("input keyevent " + code + "; echo __AR_EXIT:$?");
        if (!result.endsWith("__AR_EXIT:0") || result.contains("Exception") || result.contains("Error"))
            throw new IOException("Android не выполнил команду: " + result.substring(0, Math.min(220, result.length())));
    }

    private void send(int command, int arg0, int arg1, byte[] data) throws IOException {
        out.write(encode(command, arg0, arg1, data)); out.flush();
    }

    static int command(String value) {
        return ByteBuffer.wrap(value.getBytes(StandardCharsets.US_ASCII)).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }
    static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    static int checksum(byte[] value) { int sum = 0; for (byte b : value) sum += b & 255; return sum; }
    static byte[] encode(int command, int arg0, int arg1, byte[] data) {
        return ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command).putInt(arg0).putInt(arg1).putInt(data.length)
            .putInt(checksum(data)).putInt(command ^ -1).put(data).array();
    }
    static Packet read(InputStream in) throws IOException {
        byte[] header = new byte[24]; new DataInputStream(in).readFully(header);
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int command = buffer.getInt(), arg0 = buffer.getInt(), arg1 = buffer.getInt();
        int size = buffer.getInt(), checksum = buffer.getInt(), magic = buffer.getInt();
        if (magic != (command ^ -1) || size < 0 || size > 1024 * 1024)
            throw new IOException("Ответ не соответствует протоколу ADB");
        byte[] data = new byte[size]; new DataInputStream(in).readFully(data);
        if (checksum(data) != checksum) throw new IOException("Повреждённый ответ ADB");
        return new Packet(command, arg0, arg1, data);
    }
    static final class Packet {
        final int command, arg0, arg1; final byte[] data;
        Packet(int command, int arg0, int arg1, byte[] data) {
            this.command = command; this.arg0 = arg0; this.arg1 = arg1; this.data = data;
        }
    }
    static byte[] signToken(KeyPair key, byte[] token) throws GeneralSecurityException {
        if (token.length != 20) throw new GeneralSecurityException("Invalid ADB token");
        byte[] prefix = {0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,0x04,0x14};
        byte[] digest = new byte[prefix.length + token.length];
        System.arraycopy(prefix,0,digest,0,prefix.length); System.arraycopy(token,0,digest,prefix.length,token.length);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key.getPrivate());
        return cipher.doFinal(digest);
    }
    static byte[] encodePublicKey(KeyPair key) {
        RSAPublicKey rsa = (RSAPublicKey) key.getPublic();
        BigInteger n = rsa.getModulus(), word = BigInteger.ONE.shiftLeft(32);
        if (n.bitLength() != 2048) throw new IllegalArgumentException("ADB requires RSA 2048");
        int n0inv = n.mod(word).modInverse(word).negate().mod(word).intValue();
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
        return ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN).putInt(64).putInt(n0inv)
            .put(littleEndian(n,256)).put(littleEndian(rr,256)).putInt(rsa.getPublicExponent().intValue()).array();
    }
    private static byte[] littleEndian(BigInteger n, int size) {
        byte[] big = n.toByteArray(), little = new byte[size];
        for (int i = 0; i < size && i < big.length; i++) little[i] = big[big.length - 1 - i];
        return little;
    }
    public static boolean isPrivateIpv4(String ip) {
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) return false;
        int[] n = new int[4];
        try { for(int i = 0; i < 4; i++) {
            if (!parts[i].matches("[0-9]{1,3}")) return false;
            n[i] = Integer.parseInt(parts[i]); if(n[i] > 255) return false;
        }} catch(NumberFormatException e) { return false; }
        return (n[0] == 10 || (n[0] == 172 && n[1] >= 16 && n[1] <= 31) || (n[0] == 192 && n[1] == 168))
            && n[3] != 255;
    }
    @Override public void close() {
        closed = true; Socket s = socket;
        if (s != null) try { s.close(); } catch (IOException ignored) {}
    }
}
