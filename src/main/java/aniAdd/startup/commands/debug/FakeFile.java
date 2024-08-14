package aniAdd.startup.commands.debug;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;

public class FakeFile extends File {
    private final Path path;
    private final int size;
    private final FakeFile parent;

    public FakeFile(@NotNull Path path, int size, boolean setUpParent) {
        super(path.toString());
        this.path = path;
        this.size = size;
        if (setUpParent) {
            parent = new FakeFile(path.getParent(), 0, false);
        } else {
            parent = null;
        }
    }

    @Override
    public File getParentFile() {
        return parent;
    }

    @NotNull
    @Override
    public String getName() {
        return path.getFileName().toString();
    }

    @Override
    public long length() {
        return size;
    }
}
