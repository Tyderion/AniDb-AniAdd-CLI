package kodi.nfo;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;

@SuperBuilder
@Getter
@EqualsAndHashCode
@ToString
public class RootTag {

    Path filePath;
    public String getFileExtension() {
        val fileName = filePath.getFileName().toString();
        return fileName.substring(fileName.lastIndexOf("."));
    }

    public String getFileNameWithoutExtension() {
        val fileName = filePath.getFileName().toString();
        return fileName.substring(0, fileName.lastIndexOf("."));
    }

    public String getFileName() {
        return filePath.getFileName().toString();
    }
}
