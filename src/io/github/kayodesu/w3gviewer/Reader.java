package io.github.kayodesu.w3gviewer;

import java.util.Arrays;

/**
 * Little endian file reader
 *
 * Author: Yo
 */
class Reader {
    byte[] bytes;
    int pc = 0; // program count

    Reader(byte[] bytes) {
        this.bytes = bytes;
    }

    boolean hasMore() {
        return pc < bytes.length;
    }

    void jump(int len) {
        pc += len;
    }

    int peekU1() {
        return bytes[pc] & 0x000000FF;
    }

    int readU1() {
        return bytes[pc++] & 0x000000FF;
    }

    int readU2() {
        int low = readU1() & 0x000000FF;
        int high = readU1() & 0x000000FF;
        return high << 8 | low;
    }

    long readU4() {
        long low = bytes[pc++]  & 0xFFL;
        long midLow = (bytes[pc++] << 8) & 0xFF00L;
        long midHigh = (bytes[pc++] << 16) & 0xFF0000L;
        long high = (bytes[pc++] << 24) & 0xFF000000L;
        return high | midHigh | midLow | low;
    }

    /**
     * Read a zero terminated string, exclude zero.
     * @return
     */
    String readString() {
        int t = pc;
        while (bytes[pc] != '\0') pc++;
        var result = new String(bytes, t, pc - t);
        pc++; // jump '\0'
        return result;
    }

    /**
     * Read a zero terminated byte array, exclude zero.
     * @return
     */
    byte[] readBytes() {
        int t = pc;
        while (bytes[pc] != '\0') pc++;
        byte[] result = Arrays.copyOfRange(bytes, t, pc);
        pc++; // jump '\0'
        return result;
    }

    /**
     * Version string is saved in little endian format in the replay file
     * @return
     */
    String readVersion() {
        byte[] tmp = new byte[4];
        tmp[0] = bytes[pc + 3];
        tmp[1] = bytes[pc + 2];
        tmp[2] = bytes[pc + 1];
        tmp[3] = bytes[pc];
        pc += 4;
        return new String(tmp);
    }
}
