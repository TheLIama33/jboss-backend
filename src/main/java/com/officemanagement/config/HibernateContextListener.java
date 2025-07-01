package com.officemanagement.config;

import com.officemanagement.util.HibernateUtil;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.annotation.WebListener;

@WebListener
public class HibernateContextListener implements ServletContextListener {
    
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Initialize Hibernate SessionFactory
        HibernateUtil.getSessionFactory();
    }
    
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Clean up Hibernate SessionFactory
        HibernateUtil.shutdown();
    }
} 