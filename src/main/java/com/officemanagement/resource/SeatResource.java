package com.officemanagement.resource;

import com.officemanagement.model.Seat;
import com.officemanagement.model.OfficeRoom;
import com.officemanagement.model.Employee;
import com.officemanagement.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Path("/seats")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SeatResource {
    private final SessionFactory sessionFactory;

    public SeatResource() {
        this.sessionFactory = HibernateUtil.getSessionFactory();
    }

    @GET
    @Path("/{id}")
    public Response getSeat(@PathParam("id") Long id) {
        try (Session session = sessionFactory.openSession()) {
            // Using join fetch to eagerly load the employees collection
            Seat seat = session.createQuery(
                "select distinct s from Seat s " +
                "left join fetch s.employees " +
                "left join fetch s.room " +
                "where s.id = :id", Seat.class)
                .setParameter("id", id)
                .uniqueResult();
                
            if (seat == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            
            return Response.ok(seat).build();
        }
    }

    @POST
    public Response createSeat(Seat seat) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            
            // Validate seat number
            if (seat.getSeatNumber() == null || seat.getSeatNumber().trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Seat number is required")
                    .build();
            }
            
            // Validate that room is provided
            if (seat.getRoom() == null || seat.getRoom().getId() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Room reference is required")
                    .build();
            }
            
            // Load the referenced room
            OfficeRoom room = session.get(OfficeRoom.class, seat.getRoom().getId());
            if (room == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Referenced room does not exist")
                    .build();
            }

            // Check for duplicate seat number in the same room
            Long count = session.createQuery(
                "SELECT COUNT(s) FROM Seat s WHERE s.room.id = :roomId AND s.seatNumber = :seatNumber",
                Long.class)
                .setParameter("roomId", room.getId())
                .setParameter("seatNumber", seat.getSeatNumber().trim())
                .getSingleResult();

            if (count > 0) {
                return Response.status(Response.Status.CONFLICT)
                    .entity("A seat with number " + seat.getSeatNumber() + " already exists in this room")
                    .build();
            }
            
            // Set the room and creation timestamp
            seat.setRoom(room);
            if (seat.getCreatedAt() == null) {
                seat.setCreatedAt(LocalDateTime.now());
            }
            seat.setSeatNumber(seat.getSeatNumber().trim());
            
            // Initialize the employees set if null
            if (seat.getEmployees() == null) {
                seat.setEmployees(new HashSet<>());
            }
            
            // Save the seat
            session.save(seat);
            session.getTransaction().commit();
            
            return Response.status(Response.Status.CREATED)
                .entity(seat)
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Failed to create seat: " + e.getMessage())
                .build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response updateSeat(@PathParam("id") Long id, Seat updatedSeat) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            
            // Check if seat exists
            Seat existingSeat = session.get(Seat.class, id);
            if (existingSeat == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("Seat not found")
                    .build();
            }
            
            // Validate that room is provided
            if (updatedSeat.getRoom() == null || updatedSeat.getRoom().getId() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Room reference is required")
                    .build();
            }
            
            // Load the referenced room
            OfficeRoom room = session.get(OfficeRoom.class, updatedSeat.getRoom().getId());
            if (room == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Referenced room does not exist")
                    .build();
            }

            // Check for duplicate seat number in the same room (excluding current seat)
            String hql = "FROM Seat s WHERE s.room.id = :roomId AND s.seatNumber = :seatNumber AND s.id != :seatId";
            Long count = session.createQuery(hql, Seat.class)
                .setParameter("roomId", room.getId())
                .setParameter("seatNumber", updatedSeat.getSeatNumber())
                .setParameter("seatId", id)
                .stream()
                .count();

            if (count > 0) {
                return Response.status(Response.Status.CONFLICT)
                    .entity("A seat with number " + updatedSeat.getSeatNumber() + " already exists in this room")
                    .build();
            }
            
            // Update the seat properties
            existingSeat.setSeatNumber(updatedSeat.getSeatNumber());
            existingSeat.setRoom(room);
            
            // Save the changes
            session.update(existingSeat);
            session.getTransaction().commit();
            
            return Response.ok(existingSeat).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteSeat(@PathParam("id") Long id) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            
            // Check if seat exists
            Seat seat = session.get(Seat.class, id);
            if (seat == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("Seat not found")
                    .build();
            }
            
            // Get all employees associated with this seat
            Set<Employee> employees = new HashSet<>(seat.getEmployees());
            
            // Remove the seat from all associated employees
            for (Employee employee : employees) {
                employee.getSeats().remove(seat);
                session.update(employee);
            }
            
            // Clear the seat's employees collection
            seat.getEmployees().clear();
            session.update(seat);
            
            // Delete the seat
            session.delete(seat);
            session.getTransaction().commit();
            
            return Response.status(Response.Status.NO_CONTENT).build();
        }
    }
} 