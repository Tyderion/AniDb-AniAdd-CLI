package cache;

import cache.entities.Command;
import cache.entities.CommandParameter;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.Persistence;
import lombok.extern.java.Log;
import lombok.val;
import org.hibernate.Session;
import org.hibernate.cfg.Configuration;
import org.hibernate.jpa.HibernatePersistenceProvider;

import java.util.UUID;
import java.util.stream.Collectors;

@Log
public class Hibernator {

    public void Test() {
        val configuration = new Configuration()
                .setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC")
                .setProperty("hibernate.connection.url", "jdbc:sqlite:test.sqlite")
                .setProperty("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect")
                .setProperty("hibernate.show_sql", "true")
                .setProperty("hibernate.format_sql", "true")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .addAnnotatedClass(cache.entities.Command.class)
                .addAnnotatedClass(cache.entities.CommandParameter.class)
//                .addAnnotatedClass(cache.entities.CommandReply.class);
                ;
        val session = configuration.buildSessionFactory().openSession();

        session.setFlushMode(FlushModeType.COMMIT);
        val command = Command.builder()
                .action("mladd")
                .parameter(CommandParameter.builder().key("bubu").value("bubuv").build())
                .build();

            val transaction = session.beginTransaction();
            session.persist(command);
            transaction.commit();
        log.info(STR."Stored: \{command}");


        val test = session.get(Command.class, command.getId());

        log.info(STR."Got: \{test}");


//        session.createTab



    }
}
