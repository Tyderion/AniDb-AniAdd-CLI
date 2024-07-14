package processing;

import ed2kHasher.Edonkey;
import lombok.RequiredArgsConstructor;

import java.io.*;
import java.security.NoSuchAlgorithmException;

@RequiredArgsConstructor
public class FileParser implements Runnable {

    private final File file;
    private final Integer tag;
    private final OnHashComputed onHashComputed;
    private final Termination termination;

    @Override
    public void run() {
        String hash = null;

        try {
            Edonkey ed2k = new Edonkey();
            byte[] b = new byte[1024 * 1024 * 4];

            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                int numRead;
                while ((numRead = bis.read(b)) != -1 && !termination.shouldTerminate()) {
                    ed2k.update(b, 0, numRead);
                }
                hash = ed2k.getHexValue();
            }

        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }


        if (!termination.shouldTerminate()) {
            onHashComputed.onHashComputed(tag, hash);
        }
    }

    public interface OnHashComputed {
        void onHashComputed(Integer tag, String hash);
    }

    public interface Termination {
        boolean shouldTerminate();
    }
}
