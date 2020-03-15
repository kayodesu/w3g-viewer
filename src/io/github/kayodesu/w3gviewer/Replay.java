package io.github.kayodesu.w3gviewer;

import java.io.*;
import java.util.zip.CRC32;

import static io.github.kayodesu.w3gviewer.Texts.*;

/**
 * Replay 以 Little-Endian 存储
 *
 * Author: Yo
 */
public class Replay {

    // replay file固定头
    private static final String TITLE = "Warcraft III recorded game\u001A\0";

    private byte[] replayBytes;
    private int pc = 0; // program count

    StringBuilder sb = new StringBuilder();

    public Replay(File w3gFile) throws IOException, W3GFormatException {
        FileInputStream is = new FileInputStream(w3gFile);
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int n;
        try {
            while((n = is.read(buffer)) != -1) {
                os.write(buffer, 0, n);
            }
        } finally {
            is.close();
        }

        replayBytes = os.toByteArray();
        parse();
    }

    private void parse() throws W3GFormatException {
        parseHeader();
    }

    private void parseHeader() throws W3GFormatException {
        String title = readString();
        if (!TITLE.equals(title)) {
            throw new W3GFormatException("Wrong title: " + title);
        }
        sb.append(title).append("\n");

        // 0x40 for WarCraft III with patch <= v1.06
        // 0x44 for WarCraft III patch >= 1.07 and TFT replays
        var headerSize = readU4();
        if (headerSize != 0x44) {
            throw new W3GFormatException("不支持V1.06及以下版本的录像。");
        }

        // overall size of compressed file
        var compressedDataSize = readU4();

        // 0x00 for WarCraft III with patch <= 1.06
        // 0x01 for WarCraft III patch >= 1.07 and TFT replays
        var headerVersion = readU4();
        if (headerVersion != 1) {
            throw new W3GFormatException("不支持V1.06及以下版本的录像。");
        }

        // overall size of decompressed data (excluding header)
        var uncompressedDataSize = readU4();
        // number of compressed data blocks in file
        var compressedDataBlockCount = readU4();

        // 'WAR3' for WarCraft III Classic
        // 'W3XP' for WarCraft III Expansion Set 'The Frozen Throne'
        String version = readVersion();
        // 版本号（例如1.24版本对应的值是24）
        var versionNumber = readU4();
        var buildNumber = readU2();
        sb.append(version()).append(version).append(" 1.").append(versionNumber).append(".").append(buildNumber).append('\n'); //  todo

        // 0x0000 for single player games
        // 0x8000 for multi player games (LAN or Battle.net)
        var flags = readU2();
        if (flags == 0x0000) {
            sb.append(singlePlayerGame()).append('\n');
        } else if (flags == 0x8000) {
            sb.append(multiPlayerGame()).append('\n');
        } else {
            throw new W3GFormatException("wrong flags: " + flags); //  todo
        }

        // replay length in millisecond
        var duration = readU4();
        var second = (duration / 1000) % 60;
        var minute = (duration / 1000) / 60;
        sb.append(String.format("%s%d%s%d", duration(), minute, second >= 10 ? ":" : ":0", second));

        // CRC32 checksum for the header
        // (the checksum is calculated for the complete header including this field which is set to zero)
        var crc32Value = readU4();
        // 校验CRC32，将最后四位也就是CRC32所在的四个字节设为0后计算CRC32的值
        CRC32 crc32 = new CRC32();
        crc32.update(replayBytes, 0, 64);
        crc32.update(0);
        crc32.update(0);
        crc32.update(0);
        crc32.update(0);
        if (crc32Value != crc32.getValue()) {
            throw new W3GFormatException("Header部分CRC32校验不通过。");
        }
    }

    private int readU1() {
        return replayBytes[pc++];
    }

    private int readU2() {
        int low = readU1() & 0x000000FF;
        int high = readU1() & 0x000000FF;
        return high << 8 | low;
    }

    private long readU4() {
        long low = replayBytes[pc++]  & 0xFFL;
        long midLow = (replayBytes[pc++] << 8) & 0xFF00L;
        long midHigh = (replayBytes[pc++] << 16) & 0xFF0000L;
        long high = (replayBytes[pc++] << 24) & 0xFF000000L;
        return high | midHigh | midLow | low;
    }

    /**
     * read a zero terminated string from 'replayBytes', include zero.
     * @return
     */
    private String readString() {
        int t = pc;
        while (replayBytes[pc] != '\0') pc++;
        pc++; // jump '\0'
        return new String(replayBytes, t, pc - t);
    }

    /**
     * Version string is saved in little endian format in the replay file
     * @return
     */
    private String readVersion() {
        byte[] bytes = new byte[4];
        bytes[0] = replayBytes[pc + 3];
        bytes[1] = replayBytes[pc + 2];
        bytes[2] = replayBytes[pc + 1];
        bytes[3] = replayBytes[pc];
        pc += 4;
        return new String(bytes);
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
