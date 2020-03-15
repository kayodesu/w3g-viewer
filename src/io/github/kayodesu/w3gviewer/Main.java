package io.github.kayodesu.w3gviewer;

import java.io.File;
import java.io.IOException;
import java.util.zip.DataFormatException;

/**
 * Author: Yo
 */
public class Main {

    public static void main(String[] args) {
        try {
            Replay replay = new Replay(new File("D:\\war3\\replay\\2.w3g"));
            System.out.println(replay);
        } catch (IOException | W3GFormatException | DataFormatException e) {
            e.printStackTrace();
        }
    }
}
