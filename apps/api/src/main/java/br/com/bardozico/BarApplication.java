package br.com.bardozico;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;

@SpringBootApplication(exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
public class BarApplication {
    public static void main(String[] args) { SpringApplication.run(BarApplication.class, args); }
    @Bean SimpleModule moneyStrings() {
        var module = new SimpleModule();
        module.addSerializer(BigDecimal.class, ToStringSerializer.instance);
        return module;
    }
    @Bean org.springframework.beans.factory.InitializingBean schemaCheck(JdbcTemplate db,@Value("${bar.training}") boolean training) {
        return () -> {
            String database=db.queryForObject("SELECT current_database()",String.class);
            boolean trainingDatabase=database.startsWith("bar_training_");
            if(trainingDatabase!=training && !database.startsWith("bar_app_test_"))
                throw new IllegalStateException("Modo da aplicação incompatível com o banco. Confira a configuração sem alterar os dados.");
            // JDBC usa o schema SQL como fonte de verdade e nunca altera o DDL.
            db.queryForObject("SELECT count(*) FROM bar.app_sessions WHERE false", Integer.class);
            db.queryForObject("SELECT count(*) FROM bar.account_totals WHERE false", Integer.class);
        };
    }
}
