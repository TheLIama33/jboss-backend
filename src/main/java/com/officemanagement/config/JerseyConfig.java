package com.officemanagement.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.ApplicationPath;

@ApplicationPath("/api")
public class JerseyConfig extends ResourceConfig {
    public JerseyConfig() {
        // Register resources package
        packages("com.officemanagement.resource");
        
        // Configure JSON serialization
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        register(new JacksonJsonProvider(mapper));
        
        // Register custom media types
        registerCustomMediaTypes();
    }
    
    private void registerCustomMediaTypes() {
        // This is needed for proper content negotiation
        property("jersey.config.server.mediaTypeMappings.svg", "image/svg+xml");
    }
} 