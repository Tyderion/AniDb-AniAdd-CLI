package cache;

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
                .setProperty("hibernate.connection.url", STR."jdbc:sqlite:\{dbPath.toString()}")
                .setProperty("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect")
                .setProperty("hibernate.show_sql", "false")
                .setProperty("hibernate.format_sql", "false")
                .setProperty("hibernate.hbm2ddl.auto", "update")
                .addAnnotatedClass(cache.entities.AniDBFileData.class);
    }
}
