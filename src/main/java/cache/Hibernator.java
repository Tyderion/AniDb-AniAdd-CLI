package cache;

import cache.entities.AniDBFileData;
import lombok.extern.java.Log;
import lombok.val;
import processing.tagsystem.TagSystemTags;

@Log
public class Hibernator {

    public void Test() {
        try (val factory = PersistenceConfiguration.getSessionFactory()) {
            val repository = new AniDBFileRepository(factory);
            val file1 = AniDBFileData.builder()
                .ed2k("fake-ed2k")
                .size(10002)
                .tag(TagSystemTags.AnimeId, "69")
                .build();

            repository.saveAniDBFileData(file1);

            val file2 = repository.getAniDBFileData("fake-ed2k", 10002);
            log.info(STR."retrieved file: \{file2}");
        }



//
//        log.info(STR."original file: \{file1}");
//
//        val transaction = session.beginTransaction();
//        session.persist(file1);
//        transaction.commit();


    }
}
