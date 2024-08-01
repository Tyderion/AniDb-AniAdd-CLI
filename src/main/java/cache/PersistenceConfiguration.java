package cache;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class PersistenceConfiguration {
    public static SessionFactory getSessionFactory() {
        return getConfiguration().buildSessionFactory();
    }
    private static Configuration getConfiguration() {
        return new Configuration()
                .setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC")
                .setProperty("hibernate.connection.url", "jdbc:sqlite:test.sqlite")
                .setProperty("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect")
                .setProperty("hibernate.show_sql", "true")
                .setProperty("hibernate.format_sql", "true")
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .addAnnotatedClass(cache.entities.AniDBFileData.class);
    }
}
