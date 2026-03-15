package org.dcsim;

import java.io.IOException;

public final class DcSimApp {
    public static void main(String[] args) {
        try {
            new AppRunner().run(args);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}