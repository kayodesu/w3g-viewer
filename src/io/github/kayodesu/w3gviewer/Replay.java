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

    private long duration; // replay length in millisecond

    StringBuilder sb = new StringBuilder();

    private Player getPlayerByID(int playerID) {
        Optional<Player> optional = players.stream().filter(p -> p.playerID == playerID).findFirst();
        if (optional.isEmpty()) {
            throw new RuntimeException("Can not find player: " + playerID);
        }
        return optional.get();
    }

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

        Reader replayReader = new Reader(os.toByteArray());
        parseHeader(replayReader);
        replayReader = uncompressedData(replayReader);
        parseStaticData(replayReader);
        parseReplayData(replayReader);
    }

    private long uncompressedDataBytesCount;
    private long compressedDataBlocksCount;

    private String timeString(long timeMillisecond) {
        var second = (timeMillisecond / 1000) % 60;
        var minute = (timeMillisecond / 1000) / 60;
        return String.format("%d%s%d", minute, second >= 10 ? ":" : ":0", second);
    }

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

        duration = r.readU4();
        sb.append(duration()).append(timeString(duration)).append('\n');

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

    private Reader uncompressedData(Reader r) throws DataFormatException {
        // The last block is padded with 0 bytes up to the 8K border. These bytes can be disregarded.
        int len = (int) Math.max(uncompressedDataBytesCount, 8*1024);  // todo
        byte[] uncompressedData = new byte[len];
        int index = 0;

        for (long i = 0; i < compressedDataBlocksCount; i++) {
            // size n of compressed data block (excluding header)
            var compressedDataBytesCount = r.readU2();
            var uncompressedDataBytesCount = r.readU2(); // currently 8k
            r.readU4(); // not used

            Inflater inflater = new Inflater();
            inflater.setInput(r.bytes, r.pc, compressedDataBytesCount);
            inflater.inflate(uncompressedData, index, len - index);
            r.pc += compressedDataBytesCount;
            index += uncompressedDataBytesCount;
        }

        // 解压完毕
        return new Reader(uncompressedData);
    }

    private void parseStaticData(Reader r) {
        r.readU4(); // not used
        Player host = new Player(duration);
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
            Player player = new Player(duration);
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
                player = new Player(duration);
            } else { // human player
                player = getPlayerByID(playerID);
            }

            player.parseSlot(r);
            if (player.computerPlayer && player.existence) {
                players.add(player);
            }
        }

        // jump RandomSeed todo
        r.jump(6);
    }

    private long currTime = 0;
    private List<ChatMessage> chatMessages = new ArrayList<>();

    private void parseReplayData(Reader r) throws W3GFormatException {
        int blockID;
        while (r.hasMore() && ((blockID = r.readU1()) != 0)) {
            switch (blockID) {
                // 聊天信息
                case 0x20:
                    chatMessages.add(new ChatMessage(r));
                    break;
                // 时间段
                case 0x1E:
                case 0x1F:
                    var n = r.readU2(); // number of bytes that follow
                    // time increment (milliseconds)
                    // time increments are only correct for fast speed.
                    //   about 250 ms in battle.net
                    //   about 100 ms in LAN and single player
                    var timeIncrement = r.readU2();
                    currTime += timeIncrement;
                    // CommandData block(s) (not present if n=2)
                    n -= 2;
                    while (n > 0) {
                        // CommandData block:
                        //   1 byte  - PlayerID
                        //   1 word  - Action block length
                        //   n byte  - Action block(s) (may contain multiple actions !)
                        Player player = getPlayerByID(r.readU1());
                        var actionBlockLength = r.readU2();
                        player.parseActions(r, actionBlockLength, timeIncrement);
                        n -= (actionBlockLength + 3);
                    }
                    break;
                // 玩家离开游戏
                case 0x17:
                    // 0x01 - connection closed by remote game
                    // 0x0C - connection closed by local game
                    // 0x0E - unknown (rare) (almost like 0x01)
                    var reason = r.readU4();
                    Player player = getPlayerByID(r.readU1());
                    var result = r.readU4();
                    r.readU4(); // unknown
                    break;
                // unknown block
                case 0x1A:
                case 0x1B:
                case 0x1C:
                    r.jump(4);
                    break;
                case 0x22:
                    r.jump(5);
                    break;
                case 0x23:
                    r.jump(10);
                    break;
                case 0x2F:
                    r.jump(8);
                    break;
                default: // 无效的Block todo
                    throw new W3GFormatException("无效Block，ID:" + blockID);
            }
        }
    }

    private class ChatMessage {
        private String sender;
        private String receiver ;
        private String message;
        private String time;

        ChatMessage(Reader r) {
            sender = getPlayerByID(r.readU1()).playerName;
            var n = r.readU2(); // number of bytes that follow

            // 0x10 for delayed startup screen messages
            // 0x20 for normal messages
            var flags = r.readU1();
            if (flags != 0x10) {
                // chat mode (not present if flag = 0x10)
                var chatMode = r.readU4();
                if (chatMode == 0x00) { // for messages to all players
                    receiver = "所有人"; // todo
                } else if (chatMode == 0x01) { // for messages to allies
                    receiver = "盟友"; // todo
                } else if (chatMode == 0x02) { // for messages to observers or referees
                    receiver = "观察者和裁判"; // todo
                } else { // 0x03+N for messages to specific player N (with N = slot number)
                    receiver = "xxxxxxxxxxxxxxx"; // todo
                }
            }
            message = r.readString();
            time = timeString(currTime);
        }

        @Override
        public String toString() {
            return "[" + time + "]" +
                    sender + " 对 " +
                    receiver + " 说：" + message;
        }
    }

    @Override
    public String toString() {
        players.forEach(player -> { sb.append(player); sb.append('\n'); });
        chatMessages.forEach(chatMessage -> { sb.append(chatMessage); sb.append('\n'); });
        return sb.toString();
    }
}
