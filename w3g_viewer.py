# -*- coding: utf-8 -*-
# @Author: 贾洋

import os
import sys
import zlib
from typing import List


class Reader:
    """
    Little endian file reader
    """
    def __init__(self, data: bytes):
        self.data = data
        self.pc = 0  # program counter

    def has_more(self) -> bool:
        return self.pc < self.data.__len__()

    def jump(self, len_: int):
        self.pc += len_

    def peek_u1(self):
        return self.data[self.pc] & 0x000000FF

    def read_u1(self):
        u1 = self.data[self.pc] & 0x000000FF
        self.pc += 1
        return u1

    def read_u2(self):
        low = self.read_u1() & 0x000000FF
        high = self.read_u1() & 0x000000FF
        return high << 8 | low

    def read_u4(self):
        low = self.data[self.pc] & 0xFF
        mid_low = (self.data[self.pc + 1] << 8) & 0xFF00
        mid_high = (self.data[self.pc + 2] << 16) & 0xFF0000
        high = (self.data[self.pc + 3] << 24) & 0xFF000000
        self.pc += 4
        return high | mid_high | mid_low | low

    def read_str(self) -> str:
        """
        Read a zero terminated string, exclude zero.
        :return:
        """
        t = self.pc
        while self.data[self.pc] != 0:
            self.pc += 1
        s = str(self.data[t:self.pc], encoding="utf8")
        self.pc += 1  # jump '\0'
        return s

    def read_bytes(self) -> bytes:
        """
        Read a zero terminated byte array, exclude zero.
        :return:
        """
        t = self.pc
        while self.data[self.pc] != 0:
            self.pc += 1
        result = self.data[t:self.pc]
        self.pc += 1  # jump '\0'
        return result


class Player:
    colors = ("红色", "蓝色", "青色", "紫色", "黄色", "橘黄色", "绿色",
              "粉红色", "灰色", "淡蓝色", "深绿色", "棕色", "观察者或裁判")

    def __init__(self, duration):
        """
        :param duration: 游戏总时长（毫秒）
        """
        self.duration = duration
        self.host = self.existence = self.computer_player = False
        self.player_id = self.slot_id = -1
        # map download percent: 0x64 in custom, 0xff in ladder
        self.map_download_percent = -1
        # team number:0 - 11
        # (team 12 == observer or referee)
        self.team_no = -1
        self.player_name = self.color = self.race = self.compute_ai_strength = ""
        # player handicap in percent (as displayed on start screen)
        # valid values: 0x32, 0x3C, 0x46, 0x50, 0x5A, 0x64
        self.handicap = -1  # 血量百分比

        self.__pausing = False
        self.__action_count = 0
        self.__pausing_time = 0  # 暂停的时间。(milliseconds)

    def parse_player_record(self, r: Reader):
        # 0x00 for game host
        # 0x16 for additional players
        record_id = r.read_u1()
        if record_id == 0x00:
            self.host = True

        self.player_id = r.read_u1()
        self.player_name = r.read_str()

        # size of additional data:
        #   0x01 = custom
        #   0x08 = ladder
        additional_data = r.read_u1()
        if additional_data == 0x01:
            r.read_u1()  # not used
        elif additional_data == 0x08:
            # runtime of players Warcraft.exe in milliseconds
            r.read_u4()  # todo
            # player race flags:
            #   0x01 = human
            #   0x02 = orc
            #   0x04 = nightelf
            #   0x08 = undead
            #   (0x10=daemon)
            #   0x20 = random
            #   0x40 = race selectable / fixed(see notes in section 4.11)
            race_flag = r.read_u4()  # todo
        else:
            raise Exception("error!")  # todo

    def parse_slot(self, r: Reader, slot_id: int):
        """
        :param r:
        :param slot_id: 从0开始计数
        :return:
        """
        self.slot_id = slot_id
        self.player_id = r.read_u1()
        self.map_download_percent = r.read_u1()

        # slot status:
        #   0x00 empty slot
        #   0x01 closed slot
        #   0x02 used slot
        self.existence = (r.read_u1() == 0x02)

        # computer player flag:
        #   0x00 for human player
        #   0x01 for computer player
        self.computer_player = (r.read_u1() == 0x01)
        self.team_no = r.read_u1()

        # color (0-11):
        #   value matches player colors in world editor:
        #   (red, blue, cyan, purple, yellow, orange, green,
        #   pink, gray, light blue, dark green, brown)
        #   color 12 == observer or referee
        self.color = self.colors[r.read_u1()]

        # player race flags (as selected on map screen):
        #   0x01=human
        #   0x02=orc
        #   0x04=nightelf
        #   0x08=undead
        #   0x20=random
        #   0x40=race selectable/fixed (see notes below)
        race_flag = r.read_u1()
        if race_flag == 0x01 or race_flag == 0x41:
            self.race = "人族"
        elif race_flag == 0x02 or race_flag == 0x42:
            self.race = "兽族"
        elif race_flag == 0x04 or race_flag == 0x44:
            self.race = "暗夜精灵"
        elif race_flag == 0x08 or race_flag == 0x48:
            self.race = "不死族"
        elif race_flag == 0x20 or race_flag == 0x60:
            self.race = "随机"
        else:
            pass  # todo ox40 0x80

        # computer AI strength: (only present in v1.03 or higher)
        #   0x00 for easy
        #   0x01 for normal
        #   0x02 for insane
        # for non-AI players this seems to be always 0x01
        ai = r.read_u1()
        if self.computer_player:
            if ai == 0x00:
                self.compute_ai_strength = "简单"
            if ai == 0x01:
                self.compute_ai_strength = "中等"
            if ai == 0x02:
                self.compute_ai_strength = "令人发狂的"
            else:
                pass  # todo error
        self.handicap = r.read_u1()

    def parse_actions(self, r: Reader, action_block_length: int, time_increment: int):
        if self.__pausing:
            self.__pausing_time += time_increment

        saved_pc = r.pc
        while r.pc - saved_pc < action_block_length:
            action_id = r.read_u1()
            if action_id == 0x01:  # Pause game
                self.__pausing = True
            elif action_id == 0x02:  # Resume game
                self.__pausing = False
            elif action_id == 0x03:  # Set game speed in single player game (options menu)
                # 0x00 - slow
                # 0x01 - normal
                # 0x02 - fast
                game_speed = r.read_u1()
            elif action_id == 0x04:  # Increase game speed in single player game (Num+)
                pass
            elif action_id == 0x05:  # Decrease game speed in single player game (Num-)
                pass
            elif action_id == 0x06:  # Save game
                save_game_name = r.read_str()
            elif action_id == 0x07:  # Save game finished
                # This action is supposed to signal that saving the game finished.
                # It normally follows a 0x06 action.
                r.read_u4()  # unknown (always 0x00000001 so far)
            elif action_id == 0x10:  # Unit/building ability (no additional parameters)
                r.jump(14)
                self.__action_count += 1
            elif action_id == 0x11:  # Unit/building ability (with target position)
                r.jump(21)
                self.__action_count += 1
            elif action_id == 0x12:  # Unit/building ability (with target position and target object ID)
                r.jump(29)
                self.__action_count += 1
            elif action_id == 0x13:  # Give item to Unit / Drop item on ground (with target position, object ID A and B)
                r.jump(37)
                self.__action_count += 1
            elif action_id == 0x14:  # Unit/building ability (with two target positions and two item ID's)
                r.jump(42)
                self.__action_count += 1
            elif action_id == 0x16:  # Change Selection (Unit, Building, Area)
                # 0x01 - add to selection      (select)
                # 0x02 - remove from selection (deselect)
                select_mode = r.read_u1()
                if select_mode == 0x01:
                    self.__action_count += 1
                number = r.read_u2()  # (n) of units/buildings
                r.jump(number * 8)
            elif action_id == 0x17:  # Assign Group Hotkey
                # the group number is shifted by one:
                # key '1' is group0, ... , key '9' is group8 and key '0' is group9
                group_no = r.read_u1()
                item_count = r.read_u2()  # (n) of items in selection
                r.jump(item_count * 8)
                self.__action_count += 1
            elif action_id == 0x18:  # Select Group Hotkey
                # the group number is shifted by one:
                # key '1' is group0, ... , key '9' is group8 and key '0' is group9
                group_no = r.read_u1()
                r.read_u1()  # unknown (always 0x03)
                self.__action_count += 1
            elif action_id == 0x19:  # Select Subgroup (patch version >= 1.14b)
                r.jump(12)
            elif action_id == 0x1A:  # Pre Sub-selection
                pass
            elif action_id == 0x1B:  # Unknown
                r.jump(9)                
            elif action_id == 0x1C:  # Select Ground Item
                r.jump(9)
                self.__action_count += 1                
            elif action_id == 0x1D:  # Cancel hero revival
                r.jump(8)
                self.__action_count += 1                
            elif action_id == 0x1E:  # Remove unit from building queue
                r.jump(5)
                self.__action_count += 1                
            elif action_id == 0x21:  # Unknown
                r.jump(8)                
            # 0x20, 0x22-0x32 - Single Player Cheats（单人模式作弊）
            elif action_id == 0x20:
                pass
            elif action_id in range(0x22, 0x33):
                pass
            elif action_id in (0x27, 0x28, 0x2d):
                r.jump(5)                
            elif action_id == 0x2e:
                r.jump(4)                
            elif action_id == 0x50:  # Change ally options
                r.jump(5)                
            elif action_id == 0x51:  # Transfer resources
                r.jump(9)                
            elif action_id == 0x60:  # Map trigger chat command (?)
                r.read_u4()   # unknown
                r.read_u4()   # unknown
                r.read_str()  # chat command or trigger name
            elif action_id == 0x61:  # ESC pressed
                # Notes:
                #   This action often precedes cancel build/train actions.
                #
                #   But it is also found separately (e.g. when leaving the 'choose skill'
                #   sub-dialog of heroes using ESC).
                self.__action_count += 1
            elif action_id == 0x62:  # Scenario Trigger
                r.jump(12)
            elif action_id == 0x66:  # Enter choose hero skill submenu
                self.__action_count += 1
            elif action_id == 0x67:  # Enter choose building submenu
                self.__action_count += 1
            elif action_id == 0x68:  # Minimap signal (ping)
                r.jump(12)
            elif action_id == 0x69:  # Continue Game (BlockB)
                r.jump(16)
            elif action_id == 0x6A:  # Continue Game (BlockA)
                r.jump(16)
            elif action_id == 0x75:  # Unknown
                r.jump(1)
            else:
                # todo unknown action_id
                pass
        
    def get_apm(self) -> int:
        """
        Actions Per Minute
        :return:
        """
        if self.computer_player:
            return -1
        playing_time = self.duration - self.__pausing_time
        minutes = (playing_time / 1000) / 60.0
        return int(self.__action_count / minutes)
            
    def __str__(self):
        s = "名称：" + self.player_name + "\n" \
            + "是否主机：" + ("主机" if self.host else "否") + "\n" \
            + "是否电脑玩家：" + ("是(" + self.compute_ai_strength + ")" if self.computer_player else "否") + "\n" \
            + "队伍：" + str(self.team_no) + "\n"\
            + "颜色：" + self.color + "\n" \
            + "种族：" + self.race + "\n"\
            + "障碍（血量）：" + str(self.handicap) + "\n"

        if not self.computer_player:
            s += "游戏时间：" + time_str(self.duration - self.__pausing_time) + "\n"
            s += "操作次数：" + str(self.__action_count) + "\n"
            s += "APM：" + str(self.get_apm()) + "\n"

        return s


def time_str(time_millisecond: int) -> str:
    second = (time_millisecond / 1000) % 60
    minute = (time_millisecond / 1000) / 60
    return "%d%s%d" % (minute, ":" if second >= 10 else ":0", second)


class Replay:
    """
    Replay 以 Little-Endian 存储
    """
    TITLE = "Warcraft III recorded game\u001A"

    def __init__(self, w3g_: bytes):
        self.players = []
        self.game_name = self.map_name = self.game_creator_name = self.version_info = self.game_type = ""
        self.duration = 0  # replay length in millisecond
        self.__curr_time = 0
        self.__chat_messages: List[str] = []

        reader = Reader(w3g_)
        reader = self.__parse_header(reader)
        self.parse_static_data(reader)
        self.parse_replay_data(reader)

    def get_player_by_id(self, player_id: int) -> Player:
        for player in self.players:
            if player.player_id == player_id:
                return player
        raise Exception("Can not find player by player id: %d" % player_id)

    def get_player_by_slot(self, slot_id: int) -> Player:
        for player in self.players:
            if player.slot_id == slot_id:
                return player
        raise Exception("Can not find player by slot id: %d" % slot_id)

    def __parse_header(self, r: Reader) -> Reader:
        title = r.read_str()
        if self.TITLE != title:
            raise Exception("Wrong title: " + title)

        # 0x40 for WarCraft III with patch <= v1.06
        # 0x44 for WarCraft III patch >= 1.07 and TFT replays
        header_size = r.read_u4()
        if header_size != 0x44:
            raise Exception("不支持V1.06及以下版本的录像。")

        # overall size of compressed file
        r.read_u4()

        # 0x00 for WarCraft III with patch <= 1.06
        # 0x01 for WarCraft III patch >= 1.07 and TFT replays
        header_version = r.read_u4()
        if header_version != 1:
            raise Exception("不支持V1.06及以下版本的录像。")

        # overall size of decompressed data(excluding header)
        r.read_u4()
        # number of compressed data blocks in file
        compressed_data_block_count = r.read_u4()

        # 'WAR3' for WarCraft III Classic
        # 'W3XP' for WarCraft III Expansion Set 'The Frozen Throne'
        version = ''.join(reversed(str(r.data[r.pc: r.pc+4], encoding="utf8")))
        r.jump(4)

        version_no = r.read_u4()  # 版本号（例如1.24 版本对应的值是24）
        build_no = r.read_u2()
        self.version_info = "版本：{0} 1.{1}.{2}".format(version, version_no, build_no)

        # 0x0000 for single player games
        # 0x8000 for multi player games (LAN or Battle.net)
        flags = r.read_u2()
        if flags == 0x0000:
            self.game_type = "单人游戏"
        elif flags == 0x8000:
            self.game_type = "多人游戏"
        else:
            raise Exception("wrong flags: %d" % flags)
        
        self.duration = r.read_u4()
        
        # CRC32 checksum for the header
        # (the checksum is calculated for the complete header including this field which is set to zero)
        crc32_value = r.read_u4()
        # 校验CRC32，将最后四位也就是CRC32所在的四个字节设为0后计算CRC32的值

        # CRC32 crc32 = new CRC32()
        # crc32.update(r.bytes, 0, 64)
        # crc32.update(0)
        # crc32.update(0)
        # crc32.update(0)
        # crc32.update(0)

        # todo
        # if crc32_value != zlib.crc32(r.data[:-4] + bytes("0000", encoding="utf8")):
        #     raise Exception("Header部分CRC32校验不通过。")

        # uncompress data
        
        # The last block is padded with 0 bytes up to the 8K border. These bytes can be disregarded.
        uncompressed = bytes()

        for i in range(0, compressed_data_block_count):
            # size n of compressed data block (excluding header)
            compressed_byte_count = r.read_u2()
            r.read_u2()  # uncompressed data bytes count, currently 8k
            r.read_u4()  # not used

            data = zlib.decompressobj().decompress(r.data[r.pc: r.pc + compressed_byte_count])
            uncompressed += data
            r.pc += compressed_byte_count

        return Reader(uncompressed)
    
    def parse_static_data(self, r: Reader):
        r.read_u4()  # not used
        host = Player(self.duration)
        host.parse_player_record(r)
        self.players.append(host)
        self.game_name = r.read_str()
        r.read_u1()  # not used

        # 解析特殊编码的字节串
        encoded = r.read_bytes()  # todo Encoded String
        decoded = bytearray()
        mask = 0
        encode_pos = 0
        while encode_pos < encoded.__len__():
            if encode_pos % 8 == 0:
                mask = encoded[encode_pos]
            else:
                if (mask & (0x1 << (encode_pos % 8))) == 0:
                    decoded.append(encoded[encode_pos] - 1)
                else:
                    decoded.append(encoded[encode_pos])
            encode_pos += 1

        # 直接跳过游戏设置，这部分不解析  todo
        decoded_str_reader = Reader(bytes(decoded))
        decoded_str_reader.jump(13)

        self.map_name = decoded_str_reader.read_str()
        self.game_creator_name = decoded_str_reader.read_str()  # (can be "Battle.Net" for ladder)
        decoded_str_reader.read_str()  # always empty string

        # 4 bytes - num players or num slots
        #   in Battle.net games is the exact ## of players
        #   in Custom games, is the ## of slots on the join game screen
        #   in Single Player custom games is 12
        r.read_u4()

        # Game Type:
        #    (0x00 = unknown, just in a fewpre 1.03 custom games)
        #    0x01 = Ladder -> 1on1 or FFA
        #           Custom -> Scenario (not 100% sure about this)
        #    0x09 = Custom game
        #    0x1D = Single player game
        #    0x20 = Ladder Team game (AT or RT, 2on2/3on3/4on4)
        game_type = r.read_u1()

        # 0x00 - if it is a public LAN/Battle.net game
        # 0x08 - if it is a private Battle.net game
        private_flag = r.read_u1()

        r.read_u2()  # not used
        language_id = r.read_u4()  # todo

        # PlayerList
        # The player list is an array of PlayerRecords for all additional players
        # (excluding the game host and any computer players).
        # If there is only one human player in the game it is not present at all!
        # This record is repeated as long as the first byte equals the additional player record ID (0x16).
        while r.peek_u1() == 0x16:
            player = Player(self.duration)
            player.parse_player_record(r)
            self.players.append(player)

        # GameStartRecord
        r.read_u1()  # RecordID - always 0x19
        r.read_u2()  # number of data bytes following
        slot_count = r.read_u1()
        for i in range(0, slot_count):
            # player id (0x00 for computer players)
            player_id = r.peek_u1()
            if player_id == 0x00:  # computer players
                player = Player(self.duration)
            else:  # human player
                player = self.get_player_by_id(player_id)

            player.parse_slot(r, i)
            # 此slot有玩家且是电脑玩家，则加入到 players 中，
            # 人类玩家已经在 players 中了。
            if player.computer_player and player.existence:
                self.players.append(player)

        # jump RandomSeed todo
        r.jump(6)

    def parse_replay_data(self, r: Reader):
        while r.has_more():
            block_id = r.read_u1()
            if block_id == 0:
                return
            if block_id == 0x20:  # 聊天信息
                self.__chat_messages.append(self.parse_chat_message(r))
            elif block_id == 0x1E or block_id == 0x1F:  # 时间段
                n = r.read_u2()  # number of bytes that follow
                # time increment (milliseconds)
                # time increments are only correct for fast speed.
                #   about 250 ms in battle.net
                #   about 100 ms in LAN and single player
                time_increment = r.read_u2()
                self.__curr_time += time_increment
                # CommandData block(s) (not present if n=2)
                n -= 2
                while n > 0:
                    # CommandData block:
                    #   1 byte  - PlayerID
                    #   1 word  - Action block length
                    #   n byte  - Action block(s) (may contain multiple actions !)
                    player = self.get_player_by_id(r.read_u1())
                    action_block_len = r.read_u2()
                    player.parse_actions(r, action_block_len, time_increment)
                    n -= (action_block_len + 3)
            elif block_id == 0x17:  # 玩家离开游戏
                # 0x01 - connection closed by remote game
                # 0x0C - connection closed by local game
                # 0x0E - unknown (rare) (almost like 0x01)
                reason = r.read_u4()
                player = self.get_player_by_id(r.read_u1())
                result = r.read_u4()
                r.read_u4()  # unknown
            # unknown blocks
            elif block_id in (0x1A, 0x1B, 0x1C):
                r.jump(4)
            elif block_id == 0x22:
                r.jump(5)
            elif block_id == 0x23:
                r.jump(10)
            elif block_id == 0x2F:
                r.jump(8)
            else:  # 无效的Block todo
                raise Exception("无效的Block id: %d" % block_id)

    def parse_chat_message(self, r: Reader) -> str:
        sender = self.get_player_by_id(r.read_u1()).player_name
        r.read_u2()  # number of bytes that follow

        # 0x10 for delayed startup screen messages
        # 0x20 for normal messages
        flag = r.read_u1()
        receiver = ""
        if flag != 0x10:
            # chat mode (not present if flag = 0x10)
            chat_mode = r.read_u4()
            if chat_mode == 0x00:  # for messages to all players
                receiver = "所有人"
            elif chat_mode == 0x01:  # for messages to allies
                receiver = "盟友"
            elif chat_mode == 0x02:  # for messages to observers or referees
                receiver = "观察者或裁判"
            else:  # 0x03 + N for messages to specific player N (with N = slot number)
                slot_id = chat_mode - 0x03
                receiver = self.get_player_by_slot(slot_id).player_name
        message = r.read_str()
        time = time_str(self.__curr_time)
        return "[{0}]{1} 对 {2} 说：{3}".format(time, sender, receiver, message)

    def __str__(self):
        s = self.version_info + "\n" \
            + "时长：" + time_str(self.duration) + "\n" \
            + "游戏名称：" + self.game_name + "\n" \
            + "游戏地图：" + self.map_name + "\n" \
            + "游戏创建者：" + self.game_creator_name + '\n\n'

        for i in range(len(self.players)):
            s += "---玩家{0}---\n".format(i)
            s += self.players[i].__str__()
            s += "\n"

        for msg in self.__chat_messages:
            s += (msg + "\n")

        return s


if __name__ == '__main__':
    w3g_file = sys.argv[1]

    print("Warcraft III recorded game")
    print("文件路径：" + w3g_file)
    print("文件大小：" + '%.2f' % (os.path.getsize(w3g_file)/float(1024)) + "KB")

    f = open(w3g_file, "rb")
    w3g = f.read()

    try:
        replay = Replay(w3g)
        print(replay)
    except Exception as e:
        print(e.__str__())
        raise e
