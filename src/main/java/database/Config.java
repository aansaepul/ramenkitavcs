package database;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
@PropertySource("classpath:application.properties")
public class Config {
    @Autowired
    Environment mEnv;

    @Bean
    public DriverManagerDataSource getDataSource() {
        String dbUrl = System.getenv("JDBC_DATABASE_URL");
        String username = System.getenv("JDBC_DATABASE_USERNAME");
        String password = System.getenv("JDBC_DATABASE_PASSWORD");

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername(username);
        ds.setPassword(password);

        return ds;
    }

    @Bean(name = "com.linecorp.channel_secret")
    public String getChannelSecret() {
        return mEnv.getProperty("com.linecorp.channel_secret");
    }

    @Bean(name = "com.linecorp.channel_access_token")
    public String getChannelAccessToken() {
        return mEnv.getProperty("com.linecorp.channel_access_token");
    }

    @Bean
    public Dao getPersonDao() {
        return new DaoImpl(getDataSource());
    }
};