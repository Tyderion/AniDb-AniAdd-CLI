package cache;

import cache.entities.AniDBFileData;
import lombok.extern.java.Log;
import lombok.val;
import processing.tagsystem.TagSystemTags;

import java.util.Set;

@Log
public class Hibernator {

    public void Test() {
        try (val factory = PersistenceConfiguration.getSessionFactory("test.sqlite")) {
            val repository = new AniDBFileRepository(factory);

            val file1 = AniDBFileData.builder()
                    .ed2k("fake-ed2k")
                    .size(10002)
                    .tag(TagSystemTags.AnimeId, "69")
                    .fileName("awesome-thing.mkv")
                    .folderName("awesome-folder")
                    .build();

            val set = Set.of(file1);
//
            repository.saveAniDBFileData(file1);

            val file2 = repository.getAniDBFileData("fake-ed2k", 10002);
            val file3 = repository.getByFilename("awesome-thing.mkv");

            if (set.contains(file1)) {
                log.info("file1 is in set");
            }
            if (set.contains(file2.get())) {
                log.info("file2 is in set");
            }
            if (set.contains(file3.get())) {
                log.info("file3 is in set");
            }
//
//            val file2 = repository.getAniDBFileData("fake-ed2k", 10002);
//            log.info(STR."retrieved file: \{file2}");
//
//
//
//            val file3 = repository.getByFilename("awesome-thing.mkv");
//            log.info(STR."retrieved file by name: \{file3}");
        }


//
//        log.info(STR."original file: \{file1}");
//
//        val transaction = session.beginTransaction();
//        session.persist(file1);
//        transaction.commit();


    }
}
