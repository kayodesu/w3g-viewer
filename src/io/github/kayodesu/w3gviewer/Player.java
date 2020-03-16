package io.github.kayodesu.w3gviewer;

import static io.github.kayodesu.w3gviewer.Texts.*;

/**
 * Author: Yo
 */
class Player {
    boolean host = false;
    int playerId;
    int slotId;
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
    String computeAIStrength;

    // player handicap in percent (as displayed on start screen)
    // valid values: 0x32, 0x3C, 0x46, 0x50, 0x5A, 0x64
    int handicap; // 血量百分比

    // 游戏总时长（毫秒）
    private long duration;

    Player(long duration) {
        this.duration = duration;
    }

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

        playerId = r.readU1();
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
     *
     * @param r
     * @param slotId 从0开始计数
     */
    void parseSlot(Reader r, int slotId) {
        this.slotId = slotId;
        playerId = r.readU1();
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
        color = getColor(r.readU1());

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
                race = human();
                break;
            case 0x02:
            case 0x42:
                race = orc();
                break;
            case 0x04:
            case 0x44:
                race = nightelf();
                break;
            case 0x08:
            case 0x48:
                race = undead();
                break;
            case 0x20:
            case 0x60:
                race = random();
                break;
            default:
                // todo ox40 0x80
                break;
        }

        // computer AI strength: (only present in v1.03 or higher)
        //   0x00 for easy
        //   0x01 for normal
        //   0x02 for insane
        // for non-AI players this seems to be always 0x01
        var ai = r.readU1();
        if (computerPlayer) {
            if (ai == 0x00) {
                computeAIStrength = easy();
            } else if (ai == 0x01) {
                computeAIStrength = normal();
            } else if (ai == 0x02) {
                computeAIStrength = insane();
            } else {
                // todo error
            }
        }
        handicap = r.readU1();
    }

    private boolean pausing = false;
    private int actionsCount = 0;
    private int pausingTime = 0; // 暂停的时间。(milliseconds)

    void parseActions(Reader r, int actionBlockLength, int timeIncrement) throws W3GFormatException {
        if (pausing) {
            pausingTime += timeIncrement;
        }

        int savedPC = r.pc;
        while (r.pc - savedPC < actionBlockLength) {
            var actionId = r.readU1();
            switch (actionId) {
                case 0x01: // Pause game
                    pausing = true;
                    break;
                case 0x02: // Resume game
                    pausing = false;
                    break;
                case 0x03: // Set game speed in single player game (options menu)
                    // 0x00 - slow
                    // 0x01 - normal
                    // 0x02 - fast
                    var gameSpeed = r.readU1();
                    break;
                case 0x04: // Increase game speed in single player game (Num+)
                    break;
                case 0x05: // Decrease game speed in single player game (Num-)
                    break;
                case 0x06: // Save game
                    var saveGameName = r.readString();
                    break;
                case 0x07: // Save game finished
                    // This action is supposed to signal that saving the game finished.
                    // It normally follows a 0x06 action.
                    r.readU4(); // unknown (always 0x00000001 so far)
                    break;
                case 0x10: // Unit/building ability (no additional parameters)
                    r.jump(14);
                    actionsCount++;
                    break;
                case 0x11: // Unit/building ability (with target position)
                    r.jump(21);
                    actionsCount++;
                    break;
                case 0x12: // Unit/building ability (with target position and target object ID)
                    r.jump(29);
                    actionsCount++;
                    break;
                case 0x13: // Give item to Unit / Drop item on ground (with target position, object ID A and B)
                    r.jump(37);
                    actionsCount++;
                    break;
                case 0x14: // Unit/building ability (with two target positions and two item ID's)
                    r.jump(42);
                    actionsCount++;
                    break;
                case 0x16: // Change Selection (Unit, Building, Area)
                    // 0x01 - add to selection      (select)
                    // 0x02 - remove from selection (deselect)
                    var selectMode = r.readU1();
                    if (selectMode == 0x01) {
                        actionsCount++;
                    }
                    var number = r.readU2(); // (n) of units/buildings
                    r.jump(number * 8);
                    break;
                case 0x17: // Assign Group Hotkey
                    // the group number is shifted by one:
                    // key '1' is group0, ... , key '9' is group8 and key '0' is group9
                    var groupNumber = r.readU1();
                    var itemsCount = r.readU2(); // (n) of items in selection
                    r.jump(itemsCount * 8);
                    actionsCount++;
                    break;
                case 0x18: // Select Group Hotkey
                    // the group number is shifted by one:
                    // key '1' is group0, ... , key '9' is group8 and key '0' is group9
                    groupNumber = r.readU1();
                    r.readU1(); // unknown (always 0x03)
                    actionsCount++;
                    break;
                case 0x19: // Select Subgroup (patch version >= 1.14b)
                    r.jump(12);
                    break;
                case 0x1A: // Pre Sub-selection
                    break;
                case 0x1B: // Unknown
                    r.jump(9);
                    break;
                case 0x1C: // Select Ground Item
                    r.jump(9);
                    actionsCount++;
                    break;
                case 0x1D: // Cancel hero revival
                    r.jump(8);
                    actionsCount++;
                    break;
                case 0x1E: // Remove unit from building queue
                    r.jump(5);
                    actionsCount++;
                    break;
                case 0x21: // Unknown
                    r.jump(8);
                    break;
                // 0x20, 0x22-0x32 - Single Player Cheats（单人模式作弊）
                case 0x20:
                case 0x22:
                case 0x23:
                case 0x24:
                case 0x25:
                case 0x26:
                case 0x29:
                case 0x2a:
                case 0x2b:
                case 0x2c:
                case 0x2f:
                case 0x30:
                case 0x31:
                case 0x32:
                    break;
                case 0x27:
                case 0x28:
                case 0x2d:
                    r.jump(5);
                    break;
                case 0x2e:
                    r.jump(4);
                    break;
                case 0x50: // Change ally options
                    r.jump(5);
                    break;
                case 0x51: // Transfer resources
                    r.jump(9);
                    break;
                case 0x60: // Map trigger chat command (?)
                    r.readU4(); // unknown
                    r.readU4(); // unknown
                    r.readString(); // chat command or trigger name
                    break;
                case 0x61: // ESC pressed
                    //  Notes:
                    //    This action often precedes cancel build/train actions.
                    //
                    //    But it is also found separately (e.g. when leaving the 'choose skill'
                    //    sub-dialog of heroes using ESC).
                    actionsCount++;
                    break;
                case 0x62: // Scenario Trigger
                    r.jump(12);
                    break;
                case 0x66: // Enter choose hero skill submenu
                case 0x67: // Enter choose building submenu
                    actionsCount++;
                    break;
                case 0x68: // Minimap signal (ping)
                    r.jump(12);
                    break;
                case 0x69: // Continue Game (BlockB)
                case 0x6A: // Continue Game (BlockA)
                    r.jump(16);
                    break;
                case 0x75: // Unknown
                    r.jump(1);
                    break;
                default:
                    break;  //  todo
                    //throw new W3GFormatException("unknown actionId: " + actionId);
            }
        }
    }

    /**
     * Actions Per Minute
     */
    int getAPM() {
        if (computerPlayer)
            return -1;

        var playingTime = duration - pausingTime;
        var minutes = (playingTime/1000)/60.0;
        return (int) (actionsCount/minutes);
    }

    @Override
    public String toString() {
        String s =  "名称：" + playerName + '\n' +
               "是否电脑玩家：" + (computerPlayer ? "是(" + computeAIStrength + ")" : "否") + '\n' +
               "队伍：" + teamNo + '\n' +
               "颜色：" + color + '\n' +
               "种族：" + race + '\n' +
               "障碍（血量）：" + handicap + '\n';
        if (!computerPlayer)
            s += "APM：" + getAPM() + '\n';
        return s;
    }
}
