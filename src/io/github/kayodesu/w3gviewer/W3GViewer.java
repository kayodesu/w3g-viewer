package io.github.kayodesu.w3gviewer;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.zip.DataFormatException;

/**
 * Author: Yo
 */
public class W3GViewer {

    public static void main(String[] args) {
        String mapPath = args[0];
        File file = new File(mapPath);

        System.out.println(Replay.TITLE);
        System.out.println("文件路径：" + mapPath);
        System.out.println("文件大小：" + new DecimalFormat("0.00").format(file.length()/1024.0) + "KB");

        try {
            Replay replay = new Replay(file);
            System.out.println(replay);
        } catch (IOException | W3GFormatException | DataFormatException e) {
            e.printStackTrace();
        }
    }
}
