package cache;

import kodi.anime_details.model.*;
import kodi.anime_details.model.Character;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.jetbrains.annotations.NotNull;

public class PersistenceConfiguration {
    public static SessionFactory getSessionFactory(@NotNull String dbPath) {
        return getConfiguration(dbPath).buildSessionFactory();
    }
    private static Configuration getConfiguration(String dbPath) {
        return new Configuration()
                .setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC")
                .setProperty("hibernate.connection.url", STR."jdbc:sqlite:\{dbPath}")
                .setProperty("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect")
                .setProperty("hibernate.show_sql", "true")
                .setProperty("hibernate.format_sql", "true")
                .setProperty("hibernate.hbm2ddl.auto", "update")
                .addAnnotatedClass(cache.entities.AniDBFileData.class)
                .addAnnotatedClass(Anime.class)
                .addAnnotatedClass(AnimeCreator.class)
                .addAnnotatedClass(AnimeTag.class)
                .addAnnotatedClass(Character.class)
                .addAnnotatedClass(Creator.class)
                .addAnnotatedClass(Tag.class)
                ;
    }
}
