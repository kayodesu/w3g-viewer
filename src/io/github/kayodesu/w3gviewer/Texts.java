package io.github.kayodesu.w3gviewer;

import java.util.Locale;

/**
 * Author: Yo
 */
public class Texts {
    static String language;

    static {
        Locale locale = Locale.getDefault();
        language = locale.getLanguage();
    }

    public static String singlePlayerGame() {
        if (language.equals("zh")) {
            return "单人游戏";
        } else {
            return "single player game";
        }
    }

    public static String multiPlayerGame() {
        if (language.equals("zh")) {
            return "多人游戏";
        } else {
            return "multi player game";
        }
    }

    public static String duration() {
        if (language.equals("zh")) {
            return "时长：";
        } else {
            return "duration: ";
        }
    }

    public static String version() {
        if (language.equals("zh")) {
            return "版本：";
        } else {
            return "version: ";
        }
    }

    public static String easy() {
        if (language.equals("zh")) {
            return "简单";
        } else {
            return "easy";
        }
    }

    public static String normal() {
        if (language.equals("zh")) {
            return "中等";
        } else {
            return "normal";
        }
    }

    public static String insane() {
        if (language.equals("zh")) {
            return "令人发狂的";
        } else {
            return "insane";
        }
    }

    public static String human() {
        if (language.equals("zh")) {
            return "人族";
        } else {
            return "human";
        }
    }

    public static String orc() {
        if (language.equals("zh")) {
            return "兽族";
        } else {
            return "orc";
        }
    }

    public static String nightelf() {
        if (language.equals("zh")) {
            return "暗夜精灵";
        } else {
            return "nightelf";
        }
    }

    public static String undead() {
        if (language.equals("zh")) {
            return "不死族";
        } else {
            return "undead";
        }
    }

    public static String random() {
        if (language.equals("zh")) {
            return "随机";
        } else {
            return "random";
        }
    }
}
