package com.officemanagement.util;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class HibernateUtil {
    private static final Logger logger = LoggerFactory.getLogger(HibernateUtil.class);
    private static final SessionFactory sessionFactory;
    private static final Properties hibernateProperties = new Properties();
    private static final Properties hikariProperties = new Properties();

    static {
        try {
            // Load properties
            loadProperties();
            
            // Create configuration
            Configuration configuration = createConfiguration();
            
            // Build SessionFactory
            sessionFactory = configuration.buildSessionFactory();
            
            // Log successful initialization
            logger.info("Hibernate SessionFactory initialized successfully");
        } catch (Throwable ex) {
            logger.error("Initial SessionFactory creation failed", ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static void loadProperties() {
        // Load Hibernate custom properties
        loadPropertiesFromFile("hibernate-custom.properties", hibernateProperties, "custom Hibernate");
        
        // Load HikariCP properties
        loadPropertiesFromFile("hikari.properties", hikariProperties, "HikariCP");
    }

    private static void loadPropertiesFromFile(String filename, Properties properties, String description) {
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(filename)) {
            if (input != null) {
                properties.load(input);
                logger.info("Loaded {} properties from {}", description, filename);
                
                // Log all properties at debug level
                if (logger.isDebugEnabled()) {
                    properties.forEach((key, value) -> 
                        logger.debug("{} property: {} = {}", description, key, value));
                }
            } else {
                logger.warn("{} properties file not found: {}", description, filename);
            }
        } catch (IOException e) {
            logger.warn("Could not load {} properties from {}: {}", description, filename, e.getMessage());
        }
    }

    private static Configuration createConfiguration() {
        Configuration configuration = new Configuration().configure();
        
        // Set the connection provider class first
        configuration.setProperty("hibernate.connection.provider_class", 
            "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
        
        // Apply HikariCP properties
        hikariProperties.forEach((key, value) -> {
            String propertyKey = key.toString();
            String propertyValue = value.toString();
            configuration.setProperty(propertyKey, propertyValue);
            logger.debug("Setting property: {} = {}", propertyKey, propertyValue);
        });
        
        // Apply any custom Hibernate properties
        hibernateProperties.forEach((key, value) -> {
            String propertyKey = key.toString();
            String propertyValue = value.toString();
            configuration.setProperty(propertyKey, propertyValue);
            logger.debug("Setting custom property: {} = {}", propertyKey, propertyValue);
        });
        
        return configuration;
    }

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            throw new IllegalStateException("SessionFactory has not been initialized");
        }
        return sessionFactory;
    }

    public static void shutdown() {
        try {
            if (sessionFactory != null && !sessionFactory.isClosed()) {
                logger.info("Closing Hibernate SessionFactory");
                sessionFactory.close();
            }
        } catch (Exception e) {
            logger.error("Error closing SessionFactory", e);
        }
    }

    public static Properties getHibernateProperties() {
        Properties combined = new Properties();
        combined.putAll(hikariProperties);
        combined.putAll(hibernateProperties); // Override with custom properties
        return combined;
    }
} 