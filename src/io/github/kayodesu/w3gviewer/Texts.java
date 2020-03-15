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
}
