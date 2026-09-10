package processing;

import ed2kHasher.Edonkey;
import lombok.RequiredArgsConstructor;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

@RequiredArgsConstructor
public class FileParser implements Runnable {

    private final File file;
    private final Integer tag;
    private final OnHashComputed onHashComputed;
    private final Termination termination;

    @Override
    public void run() {
        String hash = null;
        String crc = null;

        if (file.isDirectory()) {
            // We don't hash directories
            return;
        }

        try {
            Edonkey ed2k = new Edonkey();
            // CRC32 rides along in the same read: AniDB's CRC describes the release it knows, so a
            // locally produced file needs its own, and a second pass over the file would be wasteful.
            CRC32 crc32 = new CRC32();
            byte[] b = new byte[1024 * 1024 * 4];

            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                int numRead;
                while ((numRead = bis.read(b)) != -1 && !termination.shouldTerminate()) {
                    ed2k.update(b, 0, numRead);
                    crc32.update(b, 0, numRead);
                }
                hash = ed2k.getHexValue();
                crc = String.format("%08x", crc32.getValue());
            }

        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }


        if (!termination.shouldTerminate()) {
            onHashComputed.onHashComputed(tag, hash, crc);
        }
    }

    public interface OnHashComputed {
        void onHashComputed(Integer tag, String ed2kHash, String crc32);
    }

    public interface Termination {
        boolean shouldTerminate();
    }
}
