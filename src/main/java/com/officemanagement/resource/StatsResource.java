package com.officemanagement.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import com.officemanagement.util.HibernateUtil;
import com.fasterxml.jackson.annotation.JsonProperty;

@Path("/stats") // Base path for all stats-related endpoints
public class StatsResource {

    private final SessionFactory sessionFactory;

    public StatsResource() {
        this.sessionFactory = HibernateUtil.getSessionFactory();
    }

    // DTO for stats response
    public static class StatsDTO {
        @JsonProperty("totalEmployees")
        private final long totalEmployees;
        
        @JsonProperty("totalFloors")
        private final long totalFloors;
        
        @JsonProperty("totalOffices")
        private final long totalOffices;
        
        @JsonProperty("totalSeats")
        private final long totalSeats;
        
        @JsonProperty("occupiedSeats")
        private final long occupiedSeats;
        
        @JsonProperty("occupancyRate")
        private final double occupancyRate;

        public StatsDTO(long totalEmployees, long totalFloors, long totalOffices, long totalSeats, long occupiedSeats, double occupancyRate) {
            this.totalEmployees = totalEmployees;
            this.totalFloors = totalFloors;
            this.totalOffices = totalOffices;
            this.totalSeats = totalSeats;
            this.occupiedSeats = occupiedSeats;
            this.occupancyRate = occupancyRate;
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON) // Return JSON response
    public Response getStats() {
        Session session = null;
        try {
            session = sessionFactory.openSession();
            // Get counts using getSingleResult instead of uniqueResult
            Long totalEmployees = session.createQuery("SELECT COUNT(e) FROM Employee e", Long.class)
                                      .getSingleResult();

            Long totalFloors = session.createQuery("SELECT COUNT(f) FROM Floor f", Long.class)
                                   .getSingleResult();

            Long totalOffices = session.createQuery("SELECT COUNT(o) FROM OfficeRoom o", Long.class)
                                    .getSingleResult();

            Long totalSeats = session.createQuery("SELECT COUNT(s) FROM Seat s", Long.class)
                                  .getSingleResult();

            // Calculate occupied seats (seats that have at least one employee assigned)
            Long occupiedSeats = session.createQuery("SELECT COUNT(DISTINCT s) FROM Seat s JOIN s.employees e", Long.class)
                                     .getSingleResult();

            // Calculate occupancy rate as percentage
            double occupancyRate = totalSeats > 0 ? (occupiedSeats.doubleValue() / totalSeats.doubleValue()) * 100.0 : 0.0;

            StatsDTO stats = new StatsDTO(totalEmployees, totalFloors, totalOffices, totalSeats, occupiedSeats, occupancyRate);
            return Response.ok(stats).build();
        } catch (Exception e) {
            // Handle errors and return an appropriate response
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to retrieve stats: " + e.getMessage()))
                    .build();
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    // Error response class
    private static class ErrorResponse {
        @JsonProperty("message")
        private final String message;

        public ErrorResponse(String message) {
            this.message = message;
        }
    }
}