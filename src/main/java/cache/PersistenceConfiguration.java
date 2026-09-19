package cache;

import kodi.anime_details.model.*;
import kodi.anime_details.model.Character;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class PersistenceConfiguration {
    public static SessionFactory getSessionFactory(@NotNull Path dbPath) {
        return getConfiguration(dbPath).buildSessionFactory();
    }
    private static Configuration getConfiguration(Path dbPath) {
        return new Configuration()
                .setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC")
                // Two processes share this file (the pipeline queues transcode jobs, the transcoder takes them).
                // WAL lets readers work while one writes, and busy_timeout makes a writer wait for the lock
                // instead of failing straight away with SQLITE_BUSY.
                .setProperty("hibernate.connection.url", STR."jdbc:sqlite:\{dbPath.toString()}?journal_mode=WAL&busy_timeout=30000")
                .setProperty("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect")
                .setProperty("hibernate.show_sql", "false")
                .setProperty("hibernate.format_sql", "false")
                .setProperty("hibernate.hbm2ddl.auto", "update")
                .addAnnotatedClass(cache.entities.AniDBFileData.class)
                .addAnnotatedClass(cache.entities.AnimeXml.class)
                .addAnnotatedClass(cache.entities.AnimeMappingXml.class)
                .addAnnotatedClass(cache.entities.FileHashMapping.class)
                .addAnnotatedClass(cache.entities.TranscodeJob.class);
    }
}
