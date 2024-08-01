package cache;

import cache.entities.Command;
import cache.entities.CommandParameter;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import lombok.val;
import org.hibernate.Session;
import org.hibernate.cfg.Configuration;
import org.hibernate.jpa.HibernatePersistenceProvider;

public class Hibernator {
    Session session;
    public void Test() {
        val x = new org.hibernate.community.dialect.SQLiteDialect();
        val configuration  = new Configuration()
                .configure("hibernate.xml")
//                .setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC")
//                .setProperty("hibernate.connection.url", "jdbc:sqlite:test.sqlite")
//                .setProperty("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect")
//                .setProperty("hibernate.show_sql", "true")
//                .setProperty("hibernate.format_sql", "true")
//                .setProperty("hibernate.hdm2ddl.auto", "create-drop")
                .addAnnotatedClass(cache.entities.Command.class)

                .addAnnotatedClass(cache.entities.CommandParameter.class)
//                .addAnnotatedClass(cache.entities.CommandReply.class);
;
        session = configuration.buildSessionFactory().openSession();



//        session.createTab

        val command = Command.builder()
                .action("mladd")
//                .parameter(CommandParameter.builder().key("bubu").value("bubuv").build())
                        .build();

        session.beginTransaction();
        session.persist(command);
        session.flush();
    }
}
