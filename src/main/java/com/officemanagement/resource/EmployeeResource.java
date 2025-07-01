package com.officemanagement.resource;

import com.officemanagement.model.Employee;
import com.officemanagement.model.Seat;
import com.officemanagement.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Add static inner class for pagination response
class PageResponse<T> {
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private int size;

    public PageResponse(List<T> content, long totalElements, int currentPage, int size) {
        this.content = content;
        this.totalElements = totalElements;
        this.currentPage = currentPage;
        this.size = size;
        this.totalPages = (int) Math.ceil(totalElements / (double) size);
    }

    // Getters and setters
    public List<T> getContent() { return content; }
    public void setContent(List<T> content) { this.content = content; }
    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}

@Path("/employees")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EmployeeResource {
    private final SessionFactory sessionFactory;

    public EmployeeResource() {
        this.sessionFactory = HibernateUtil.getSessionFactory();
    }

    @GET
    @Path("/{id}")
    public Response getEmployee(@PathParam("id") Long id) {
        try (Session session = sessionFactory.openSession()) {
            Employee employee = session.createQuery(
                "select distinct e from Employee e " +
                "left join fetch e.seats s " +
                "left join fetch s.room r " +
                "left join fetch r.floor f " +
                "left join fetch s.employees " +
                "where e.id = :id", 
                Employee.class)
                .setParameter("id", id)
                .uniqueResult();
            
            if (employee == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            
            return Response.ok(employee).build();
        }
    }

    @GET
    @Path("/{id}/seats")
    public Response getEmployeeSeats(@PathParam("id") Long id) {
        try (Session session = sessionFactory.openSession()) {
            Employee employee = session.createQuery(
                "select distinct e from Employee e " +
                "left join fetch e.seats s " +
                "left join fetch s.room r " +
                "left join fetch r.floor f " +
                "left join fetch s.employees " +
                "where e.id = :id", 
                Employee.class)
                .setParameter("id", id)
                .uniqueResult();
            
            if (employee == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            
            return Response.ok(employee.getSeats()).build();
        }
    }

    @POST
    public Response createEmployee(Employee employee) {
        // Validate input
        if (employee == null || employee.getFullName() == null || employee.getFullName().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Employee full name is required")
                .build();
        }

        if (employee.getOccupation() == null || employee.getOccupation().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Employee occupation is required")
                .build();
        }

        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            employee.setCreatedAt(LocalDateTime.now());
            session.save(employee);
            session.getTransaction().commit();
            return Response.status(Response.Status.CREATED).entity(employee).build();
        }
    }

    @PUT
    @Path("/{id}/assign-seat/{seatId}")
    public Response assignSeat(@PathParam("id") Long employeeId, @PathParam("seatId") Long seatId) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            
            Employee employee = session.get(Employee.class, employeeId);
            if (employee == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("Employee not found").build();
            }

            Seat seat = session.get(Seat.class, seatId);
            if (seat == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("Seat not found").build();
            }

            // Add seat to employee's seats
            employee.addSeat(seat);
            
            // Update both entities
            session.update(employee);
            
            session.getTransaction().commit();
            
            /* 
             * NOTE ON IMPLEMENTATION APPROACH:
             * 
             * We reload the employee entity after committing the transaction to prevent 
             * LazyInitializationException during JSON serialization. This happens because:
             * 
             * 1. After commit, the Hibernate session is closed
             * 2. When serializing the response, Seat.isOccupied() tries to access employees collection
             * 3. Since session is closed, lazy loading fails with "failed to lazily initialize collection"
             * 
             * Trade-offs of this approach:
             * - PROS: Simple to implement, no need for DTOs or repository pattern
             * - CONS: Requires an extra database query, less efficient for large object graphs
             * 
             * Alternative approaches (not implemented to keep code simple):
             * 1. Data Transfer Objects (DTOs): Create separate objects for API responses
             * 2. Repository Pattern: Separate domain models from persistence entities
             * 3. Entity Graphs: Use JPA entity graphs to specify eager loading
             * 
             * For a small to medium application, this approach offers a good balance
             * between simplicity and functionality.
             */
            Employee refreshedEmployee = session.createQuery(
                "select distinct e from Employee e " +
                "left join fetch e.seats s " +
                "left join fetch s.room r " +
                "left join fetch r.floor f " +
                "left join fetch s.employees " +
                "where e.id = :id", 
                Employee.class)
                .setParameter("id", employeeId)
                .uniqueResult();
            
            return Response.ok(refreshedEmployee).build();
        }
    }

    @DELETE
    @Path("/{employeeId}/unassign-seat/{seatId}")
    public Response unassignSeat(@PathParam("employeeId") Long employeeId, @PathParam("seatId") Long seatId) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            
            Employee employee = session.get(Employee.class, employeeId);
            if (employee == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("Employee not found").build();
            }

            Seat seat = session.get(Seat.class, seatId);
            if (seat == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("Seat not found").build();
            }

            // Check if this seat is assigned to the employee
            if (!employee.getSeats().contains(seat)) {
                return Response.status(Response.Status.BAD_REQUEST).entity("This seat is not assigned to the employee").build();
            }

            // Remove the seat from employee
            employee.removeSeat(seat);
            session.update(employee);
            
            session.getTransaction().commit();
            
            /* 
             * Same approach as in assignSeat method - we reload the entity after commit.
             * 
             * This pragmatic solution prevents lazy initialization exceptions without 
             * requiring architectural changes like introducing DTOs or repository pattern.
             * 
             * While not the most efficient approach for high-volume systems, it's adequate
             * for most use cases and keeps the codebase straightforward.
             * 
             * See the detailed explanation in the assignSeat method.
             */
            Employee refreshedEmployee = session.createQuery(
                "select distinct e from Employee e " +
                "left join fetch e.seats s " +
                "left join fetch s.room r " +
                "left join fetch r.floor f " +
                "left join fetch s.employees " +
                "where e.id = :id", 
                Employee.class)
                .setParameter("id", employeeId)
                .uniqueResult();
            
            return Response.ok(refreshedEmployee).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteEmployee(@PathParam("id") Long id) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            
            // Check if employee exists
            Employee employee = session.get(Employee.class, id);
            if (employee == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("Employee not found")
                    .build();
            }
            
            // Get all seats associated with this employee
            Set<Seat> seats = new HashSet<>(employee.getSeats());
            
            // Remove the employee from all associated seats
            for (Seat seat : seats) {
                seat.getEmployees().remove(employee);
                session.update(seat);
            }
            
            // Clear the employee's seats collection
            employee.getSeats().clear();
            session.update(employee);
            
            // Delete the employee
            session.delete(employee);
            session.getTransaction().commit();
            
            return Response.status(Response.Status.NO_CONTENT).build();
        }
    }

    @GET
    @Path("/search")
    public Response searchEmployees(
            @QueryParam("search") @DefaultValue("") String searchTerm,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size) {
        
        // Validate pagination parameters
        if (page < 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Page number cannot be negative")
                .build();
        }

        if (size <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Page size must be positive")
                .build();
        }

        // Set a reasonable maximum page size to prevent performance issues
        final int MAX_PAGE_SIZE = 100;
        if (size > MAX_PAGE_SIZE) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Page size cannot exceed " + MAX_PAGE_SIZE)
                .build();
        }

        try (Session session = sessionFactory.openSession()) {
            // Create the base query for total count
            String countQuery = "select count(distinct e) from Employee e " +
                    "where lower(e.fullName) like lower(:searchTerm) " +
                    "or lower(e.occupation) like lower(:searchTerm)";
            
            Long totalElements = session.createQuery(countQuery, Long.class)
                    .setParameter("searchTerm", "%" + searchTerm + "%")
                    .uniqueResult();

            // Create the main query with pagination
            String query = "select distinct e from Employee e " +
                    "left join fetch e.seats s " +
                    "left join fetch s.room r " +
                    "left join fetch s.employees " +
                    "where lower(e.fullName) like lower(:searchTerm) " +
                    "or lower(e.occupation) like lower(:searchTerm)";

            List<Employee> employees = session.createQuery(query, Employee.class)
                    .setParameter("searchTerm", "%" + searchTerm + "%")
                    .setFirstResult(page * size)
                    .setMaxResults(size)
                    .list();

            PageResponse<Employee> pageResponse = new PageResponse<>(
                employees, totalElements, page, size
            );

            return Response.ok(pageResponse).build();
        }
    }
}
