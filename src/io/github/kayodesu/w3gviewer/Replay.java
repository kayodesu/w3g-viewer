package io.github.kayodesu.w3gviewer;

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static io.github.kayodesu.w3gviewer.Texts.*;

/**
 * Replay 以 Little-Endian 存储
 *
 * Author: Yo
 */
public class Replay {

    // replay file固定头
    private static final String TITLE = "Warcraft III recorded game\u001A";

    private List<Player> players = new ArrayList<>();
    private String gameName;
    private String mapName;
    private String gameCreatorName;

    StringBuilder sb = new StringBuilder();

    public Replay(File w3gFile) throws IOException, W3GFormatException, DataFormatException {
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

        parse(os.toByteArray());
    }

    private void parse(byte[] replayBytes) throws W3GFormatException, DataFormatException {
        Reader replayReader = new Reader(replayBytes);
        parseHeader(replayReader);
        parseData(replayReader);
    }

    private long uncompressedDataBytesCount;
    private long compressedDataBlocksCount;

    private void parseHeader(Reader r) throws W3GFormatException {
        String title = r.readString();
        if (!TITLE.equals(title)) {
            throw new W3GFormatException("Wrong title: " + title);
        }
        sb.append(title).append("\n");

        // 0x40 for WarCraft III with patch <= v1.06
        // 0x44 for WarCraft III patch >= 1.07 and TFT replays
        var headerSize = r.readU4();
        if (headerSize != 0x44) {
            throw new W3GFormatException("不支持V1.06及以下版本的录像。");
        }

        // overall size of compressed file
        var compressedDataSize = r.readU4();

        // 0x00 for WarCraft III with patch <= 1.06
        // 0x01 for WarCraft III patch >= 1.07 and TFT replays
        var headerVersion = r.readU4();
        if (headerVersion != 1) {
            throw new W3GFormatException("不支持V1.06及以下版本的录像。");
        }

        // overall size of decompressed data (excluding header)
        uncompressedDataBytesCount = r.readU4();
        // number of compressed data blocks in file
        compressedDataBlocksCount = r.readU4();

        // 'WAR3' for WarCraft III Classic
        // 'W3XP' for WarCraft III Expansion Set 'The Frozen Throne'
        String version = r.readVersion();
        // 版本号（例如1.24版本对应的值是24）
        var versionNumber = r.readU4();
        var buildNumber = r.readU2();
        sb.append(version()).append(version).append(" 1.").append(versionNumber).append(".").append(buildNumber).append('\n'); //  todo

        // 0x0000 for single player games
        // 0x8000 for multi player games (LAN or Battle.net)
        var flags = r.readU2();
        if (flags == 0x0000) {
            sb.append(singlePlayerGame()).append('\n');
        } else if (flags == 0x8000) {
            sb.append(multiPlayerGame()).append('\n');
        } else {
            throw new W3GFormatException("wrong flags: " + flags); //  todo
        }

        // replay length in millisecond
        var duration = r.readU4();
        var second = (duration / 1000) % 60;
        var minute = (duration / 1000) / 60;
        sb.append(String.format("%s%d%s%d", duration(), minute, second >= 10 ? ":" : ":0", second)).append('\n');

        // CRC32 checksum for the header
        // (the checksum is calculated for the complete header including this field which is set to zero)
        var crc32Value = r.readU4();
        // 校验CRC32，将最后四位也就是CRC32所在的四个字节设为0后计算CRC32的值
        CRC32 crc32 = new CRC32();
        crc32.update(r.bytes, 0, 64);
        crc32.update(0);
        crc32.update(0);
        crc32.update(0);
        crc32.update(0);
        if (crc32Value != crc32.getValue()) {
            throw new W3GFormatException("Header部分CRC32校验不通过。");
        }
    }

    private void parseData(Reader replayReader) throws W3GFormatException, DataFormatException {
        // The last block is padded with 0 bytes up to the 8K border. These bytes can be disregarded.
        int len = (int) Math.max(uncompressedDataBytesCount, 8*1024);  // todo
        byte[] uncompressedData = new byte[len];
        int index = 0;

        for (long i = 0; i < compressedDataBlocksCount; i++) {
            // size n of compressed data block (excluding header)
            var compressedDataBytesCount = replayReader.readU2();
            var uncompressedDataBytesCount = replayReader.readU2(); // currently 8k
            replayReader.readU4(); // not used

            Inflater inflater = new Inflater();
            inflater.setInput(replayReader.bytes, replayReader.pc, compressedDataBytesCount);
            if(inflater.inflate(uncompressedData, index, len - index) != uncompressedDataBytesCount) {
                throw new W3GFormatException("解压缩数据异常");
            }
            replayReader.pc += compressedDataBytesCount;
            index += uncompressedDataBytesCount;
        }

        /* 解压完毕，开始解析数据 */
        Reader r = new Reader(uncompressedData);
        r.readU4(); // not used
        Player host = new Player();
        host.parsePlayerRecord(r);
        players.add(host);
        gameName = r.readString();
        sb.append(gameName).append('\n');
        r.readU1(); // not used

        /* 解析特殊编码的字节串 */
        byte[] encoded = r.readBytes(); // todo Encoded String
        byte[] decoded = new byte[encoded.length]; // big enough
        byte mask = 0;
        int decodePos = 0;
        int encodePos = 0;
        while (encodePos < encoded.length) {
            if (encodePos % 8 == 0) {
                mask = encoded[encodePos];
            } else {
                if ((mask & (0x1 << (encodePos % 8))) == 0) {
                    decoded[decodePos++] = (byte) (encoded[encodePos] - 1);
                } else {
                    decoded[decodePos++] = encoded[encodePos];
                }
            }
            encodePos++;
        }

        // 直接跳过游戏设置，这部分不解析
        Reader decodedStringReader = new Reader(decoded);
        decodedStringReader.jump(13);

        mapName = decodedStringReader.readString();
        gameCreatorName  = decodedStringReader.readString(); // (can be "Battle.Net" for ladder)
        decodedStringReader.readString(); // always empty string
        sb.append(mapName).append('\n');
        sb.append(gameCreatorName).append('\n');

        // 4 bytes - num players or num slots
        //   in Battle.net games is the exact ## of players
        //   in Custom games, is the ## of slots on the join game screen
        //   in Single Player custom games is 12
        var playerCount = r.readU4();

//        // Game Type:
//        //       |           | (0x00 = unknown, just in a few  pre 1.03 custom games)
//        //       |           |  0x01 =   Ladder -> 1on1 or FFA
//        //                               Custom -> Scenario  (not 100% sure about this)
//        //       |           |  0x09 = Custom game
//        //       |           |  0x1D = Single player game
//        //       |           |  0x20 = Ladder Team game (AT or RT, 2on2/3on3/4on4)
//        var gameType = r.readU1();
//        // 0x00 - if it is a public LAN/Battle.net game
//        //       |           |  0x08 - if it is a private Battle.net game
//        var privateFlag = r.readU1();
//        r.readU2(); // not used
        r.readU4(); // todo GameType

        var languageID = r.readU4(); // todo

        // PlayerList
        // The player list is an array of PlayerRecords for all additional players
        // (excluding the game host and any computer players).
        // If there is only one human player in the game it is not present at all!
        // This record is repeated as long as the first byte equals the additional player record ID (0x16).
        while (r.peekU1() == 0x16) {
            Player player = new Player();
            player.parsePlayerRecord(r);
            players.add(player);
        }

        // GameStartRecord
        r.readU1(); // RecordID - always 0x19
        r.readU2(); // number of data bytes following
        var slotCount = r.readU1();
        for (int i = 0; i < slotCount; i++) {
            // player id (0x00 for computer players)
            var playerID = r.peekU1();
            Player player;
            if (playerID == 0x00) { // computer players
                player = new Player();
            } else { // human player
                Optional<Player> optional = players.stream().filter(p -> p.playerID == playerID).findFirst();
                if (optional.isEmpty()) {
                    // todo error
                    throw new W3GFormatException("");
                }
                player = optional.get();
            }

            player.parseSlot(r);
            if (player.computerPlayer && player.existence) {
                players.add(player);
            }
        }
    }

    private static class Player {
        boolean host = false;
        int playerID;
        String playerName;
        boolean existence = false;

        // map download percent: 0x64 in custom, 0xff in ladder
        int mapDownloadPercent;
        boolean computerPlayer;

        // team number:0 - 11
        // (team 12 == observer or referee)
        int teamNo;

        String color;
        String race;

        // computer AI strength: (only present in v1.03 or higher)
        //   0x00 for easy
        //   0x01 for normal
        //   0x02 for insane
        // for non-AI players this seems to be always 0x01
        int computeAIStrength;

        // player handicap in percent (as displayed on start screen)
        // valid values: 0x32, 0x3C, 0x46, 0x50, 0x5A, 0x64
        int handicap; // 血量百分比

        private static final String[] colors = { "red", "blue", "cyan", "purple", "yellow", "orange", "green",
                                "pink", "gray", "light blue", "dark green", "brown", "observer or referee" };

        /**
         * Parse human player.
         * @param r
         */
        void parsePlayerRecord(Reader r) {
            // 0x00 for game host
            // 0x16 for additional players
            var recordID = r.readU1();
            if (recordID == 0x00) {
                host = true;
            }

            playerID = r.readU1();
            playerName = r.readString();

            // size of additional data:
            // 0x01 = custom
            // 0x08 = ladder
            var additionalData = r.readU1();
            if (additionalData == 0x01) {
                r.readU1(); // not used
            } else if (additionalData == 0x08) {
                // runtime of players Warcraft.exe in milliseconds
                var xx = r.readU4(); // todo
                // player race flags:
                // 0x01=human
                // 0x02=orc
                // 0x04=nightelf
                // 0x08=undead
                // (0x10=daemon)
                // 0x20=random
                // 0x40=race selectable/fixed (see notes in section 4.11)
                var raceFlag = r.readU4(); // todo
            } else {
                // todo error
            }
        }

        /**
         * Create computer player
         */
        void parseSlot(Reader r) {
            playerID = r.readU1();
            mapDownloadPercent = r.readU1();

            // slot status
            // 0x00 empty slot
            // 0x01 closed slot
            // 0x02 used slot
            if (r.readU1() == 0x02) {
                existence = true;
            }

            // computer player flag:
            //   0x00 for human player
            //   0x01 for computer player
            computerPlayer = r.readU1() == 0x01;
            teamNo = r.readU1();

            // color (0-11):
            //   value matches player colors in world editor:
            //   (red, blue, cyan, purple, yellow, orange, green,
            //   pink, gray, light blue, dark green, brown)
            //   color 12 == observer or referee
            color = colors[r.readU1()];

            // player race flags (as selected on map screen):
            //   0x01=human
            //   0x02=orc
            //   0x04=nightelf
            //   0x08=undead
            //   0x20=random
            //   0x40=race selectable/fixed (see notes below)
            var raceFlag = r.readU1();
            switch(raceFlag) {
                case 0x01:
                case 0x41:
                    race = "human";
                    break;
                case 0x02:
                case 0x42:
                    race = "orc";
                    break;
                case 0x04:
                case 0x44:
                    race = "nightelf";
                    break;
                case 0x08:
                case 0x48:
                    race = "undead";
                    break;
                case 0x20:
                case 0x60:
                    race = "random";
                    break;
                default:
                    // todo ox40 0x80
                    break;
            }
            computeAIStrength = r.readU1();
            handicap = r.readU1();
        }

        @Override
        public String toString() {
            return "Player{" +
                    "host=" + host +
                    ", playerID=" + playerID +
                    ", playerName='" + playerName + '\'' +
                    ", mapDownloadPercent=" + mapDownloadPercent +
                    ", computerPlayer=" + computerPlayer +
                    ", teamNo=" + teamNo +
                    ", color='" + color + '\'' +
                    ", race='" + race + '\'' +
                    ", computeAIStrength=" + computeAIStrength +
                    ", handicap=" + handicap +
                    '}';
        }
    }

    @Override
    public String toString() {
        players.forEach(player -> { sb.append(player); sb.append('\n'); });
        return sb.toString();
    }

    private static class Reader {
        byte[] bytes;
        int pc = 0; // program count

        Reader(byte[] bytes) {
            this.bytes = bytes;
        }

        void jump(int len) {
            pc += len;
        }

        int peekU1() {
            return bytes[pc];
        }

        int readU1() {
            return bytes[pc++];
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
}
