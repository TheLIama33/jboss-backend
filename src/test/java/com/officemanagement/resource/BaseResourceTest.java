package com.officemanagement.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.officemanagement.config.JerseyConfig;
import com.officemanagement.util.HibernateUtil;
import io.restassured.RestAssured;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ExtendWith(MockitoExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ApplicationPath("/api")
public abstract class BaseResourceTest extends JerseyTest {
    private static boolean initialized = false;
    protected static ObjectMapper objectMapper;
    protected static SessionFactory sessionFactory;
    protected Session session;
    protected Transaction transaction;

    @BeforeAll
    public static void setupClass() {
        if (!initialized) {
            // Configure ObjectMapper
            objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            
            // Configure RestAssured
            RestAssured.baseURI = "http://localhost";
            RestAssured.config = RestAssuredConfig.config().objectMapperConfig(
                ObjectMapperConfig.objectMapperConfig().jackson2ObjectMapperFactory(
                    (type, s) -> objectMapper
                )
            );
            
            // Initialize Hibernate
            sessionFactory = HibernateUtil.getSessionFactory();
            
            initialized = true;
        }
    }

    @AfterAll
    public static void tearDownClass() {
        // Remove the sessionFactory.close() call from here
        // The SessionFactory will be managed by the application lifecycle
    }

    @Override
    protected Application configure() {
        enable(TestProperties.LOG_TRAFFIC);
        enable(TestProperties.DUMP_ENTITY);
        
        ResourceConfig config = new ResourceConfig();
        
        // Register resources
        config.register(EmployeeResource.class);
        config.register(FloorResource.class);
        config.register(RoomResource.class);
        config.register(SeatResource.class);
        config.register(StatsResource.class);
        
        // Register JSON provider
        JacksonJsonProvider jsonProvider = new JacksonJsonProvider(objectMapper);
        config.register(jsonProvider);
        
        // Register other providers
        config.register(JerseyConfig.class);
        
        // Set the application path
        config.setApplicationName("api");
        
        return config;
    }

    @BeforeEach
    public void setup() throws Exception {
        super.setUp();
        RestAssured.port = getPort();
        RestAssured.basePath = "/";
        
        // Configure REST Assured logging
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        
        // Start a new transaction
        session = sessionFactory.openSession();
        transaction = session.beginTransaction();

        // Clean the database before each test
        cleanDatabase();
    }

    private void cleanDatabase() {
        // Disable foreign key checks temporarily
        session.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
        
        // Truncate all tables
        session.createNativeQuery("TRUNCATE TABLE employee_seat_assignments").executeUpdate();
        session.createNativeQuery("TRUNCATE TABLE seats").executeUpdate();
        session.createNativeQuery("TRUNCATE TABLE employees").executeUpdate();
        session.createNativeQuery("TRUNCATE TABLE office_rooms").executeUpdate();
        session.createNativeQuery("TRUNCATE TABLE floors").executeUpdate();
        session.createNativeQuery("TRUNCATE TABLE floor_planimetry").executeUpdate();
        
        // Reset sequences
        session.createNativeQuery("ALTER SEQUENCE seat_seq RESTART WITH 1").executeUpdate();
        session.createNativeQuery("ALTER SEQUENCE employee_seq RESTART WITH 1").executeUpdate();
        session.createNativeQuery("ALTER SEQUENCE office_room_seq RESTART WITH 1").executeUpdate();
        session.createNativeQuery("ALTER SEQUENCE floor_seq RESTART WITH 1").executeUpdate();
        
        // Re-enable foreign key checks
        session.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
        
        // Commit the changes
        transaction.commit();
        transaction = session.beginTransaction();
    }

    @AfterEach
    public void cleanup() throws Exception {
        if (transaction != null && transaction.isActive()) {
            try {
                transaction.commit();
            } catch (Exception e) {
                transaction.rollback();
                throw e;
            }
        }
        if (session != null && session.isOpen()) {
            session.close();
        }
        super.tearDown();
    }

    protected void flushAndClear() {
        session.flush();
        session.clear();
    }

    protected void commitAndStartNewTransaction() {
        transaction.commit();
        transaction = session.beginTransaction();
    }

    protected String getApiPath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }
} 