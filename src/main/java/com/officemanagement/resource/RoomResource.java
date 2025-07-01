package com.officemanagement.resource;

import com.officemanagement.model.OfficeRoom;
import com.officemanagement.model.Seat;
import com.officemanagement.model.Floor;
import com.officemanagement.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.List;

@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoomResource {
    private final SessionFactory sessionFactory;

    public RoomResource() {
        this.sessionFactory = HibernateUtil.getSessionFactory();
    }

    @GET
    public Response getAllRooms() {
        try (Session session = sessionFactory.openSession()) {
            List<OfficeRoom> rooms = session.createQuery(
                    "select distinct r from OfficeRoom r " +
                            "left join fetch r.floor f " +
                            "left join fetch r.seats s " +
                            "left join fetch s.employees",
                    OfficeRoom.class).list();
            return Response.ok(rooms).build();
        }
    }

    @POST
    public Response createRoom(OfficeRoom room) {
        // Validate input
        if (room == null || room.getName() == null || room.getName().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Room name is required")
                .build();
        }

        if (room.getRoomNumber() == null || room.getRoomNumber().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Room number is required")
                .build();
        }

        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            
            // Validate that floor is provided
            if (room.getFloor() == null || room.getFloor().getId() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Floor reference is required")
                    .build();
            }
            
            // Load the referenced floor
            Floor floor = session.get(Floor.class, room.getFloor().getId());
            if (floor == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Referenced floor does not exist")
                    .build();
            }

            // Check for duplicate room number in the same floor
            Long count = session.createQuery(
                "SELECT COUNT(r) FROM OfficeRoom r WHERE r.floor.id = :floorId AND r.roomNumber = :roomNumber", Long.class)
                .setParameter("floorId", floor.getId())
                .setParameter("roomNumber", room.getRoomNumber())
                .uniqueResult();

            if (count > 0) {
                return Response.status(Response.Status.CONFLICT)
                    .entity("A room with number " + room.getRoomNumber() + " already exists on this floor")
                    .build();
            }

            room.setFloor(floor);
            room.setCreatedAt(LocalDateTime.now());
            
            session.save(room);
            session.getTransaction().commit();
            
            return Response.status(Response.Status.CREATED)
                .entity(room)
                .build();
        }
    }

    @GET
    @Path("/{id}")
    public Response getRoom(@PathParam("id") Long id) {
        try (Session session = sessionFactory.openSession()) {
            OfficeRoom room = session.createQuery(
                "select distinct r from OfficeRoom r " +
                "left join fetch r.seats s " +
                "left join fetch s.employees " +
                "where r.id = :id", OfficeRoom.class)
                .setParameter("id", id)
                .uniqueResult();
                
            if (room == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            
            // Create a custom response that excludes the floor's planimetry data
            Map<String, Object> response = new HashMap<>();
            response.put("id", room.getId());
            response.put("name", room.getName());
            response.put("roomNumber", room.getRoomNumber());
            response.put("x", room.getX());
            response.put("y", room.getY());
            response.put("width", room.getWidth());
            response.put("height", room.getHeight());
            response.put("createdAt", room.getCreatedAt());
            
            // Include floor details but exclude planimetry
            if (room.getFloor() != null) {
                Map<String, Object> floorInfo = new HashMap<>();
                floorInfo.put("id", room.getFloor().getId());
                floorInfo.put("name", room.getFloor().getName());
                floorInfo.put("floorNumber", room.getFloor().getFloorNumber());
                response.put("floor", floorInfo);
            }
            
            // Include seats
            if (room.getSeats() != null && !room.getSeats().isEmpty()) {
                Set<Map<String, Object>> seatsList = new java.util.HashSet<>();
                for (Seat seat : room.getSeats()) {
                    Map<String, Object> seatInfo = new HashMap<>();
                    seatInfo.put("id", seat.getId());
                    seatInfo.put("seatNumber", seat.getSeatNumber());
                    seatInfo.put("x", seat.getX());
                    seatInfo.put("y", seat.getY());
                    seatInfo.put("width", seat.getWidth());
                    seatInfo.put("height", seat.getHeight());
                    seatInfo.put("rotation", seat.getRotation());
                    
                    // Include employee IDs if any are assigned to this seat
                    if (seat.getEmployees() != null && !seat.getEmployees().isEmpty()) {
                        Set<Long> employeeIds = seat.getEmployees().stream()
                            .map(employee -> employee.getId())
                            .collect(java.util.stream.Collectors.toSet());
                        seatInfo.put("employeeIds", employeeIds);
                    }
                    
                    seatsList.add(seatInfo);
                }
                response.put("seats", seatsList);
            }
            
            return Response.ok(response).build();
        }
    }

    @GET
    @Path("/{id}/seats")
    public Response getRoomSeats(@PathParam("id") Long id) {
        try (Session session = sessionFactory.openSession()) {
            OfficeRoom room = session.createQuery(
                "select distinct r from OfficeRoom r " +
                "left join fetch r.seats " +
                "where r.id = :id", OfficeRoom.class)
                .setParameter("id", id)
                .uniqueResult();
                
            if (room == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            
            Set<Seat> seats = room.getSeats();
            
            // Create a custom response with just the seat data
            Set<Map<String, Object>> seatsList = new java.util.HashSet<>();
            if (seats != null) {
                for (Seat seat : seats) {
                    Map<String, Object> seatInfo = new HashMap<>();
                    seatInfo.put("id", seat.getId());
                    seatInfo.put("seatNumber", seat.getSeatNumber());
                    seatInfo.put("x", seat.getX());
                    seatInfo.put("y", seat.getY());
                    seatInfo.put("width", seat.getWidth());
                    seatInfo.put("height", seat.getHeight());
                    seatInfo.put("rotation", seat.getRotation());
                    seatInfo.put("roomId", id);
                    
                    seatsList.add(seatInfo);
                }
            }
            
            return Response.ok(seatsList).build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response updateRoom(@PathParam("id") Long id, OfficeRoom room) {
        // Validate input
        if (room == null || room.getName() == null || room.getName().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Room name is required")
                .build();
        }

        if (room.getRoomNumber() == null || room.getRoomNumber().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Room number is required")
                .build();
        }

        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            
            // Check if room exists
            OfficeRoom existingRoom = session.get(OfficeRoom.class, id);
            if (existingRoom == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("Room not found")
                    .build();
            }
            
            // Validate that floor is provided
            if (room.getFloor() == null || room.getFloor().getId() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Floor reference is required")
                    .build();
            }
            
            // Load the referenced floor
            Floor floor = session.get(Floor.class, room.getFloor().getId());
            if (floor == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Referenced floor does not exist")
                    .build();
            }

            // Check for duplicate room number in the same floor (excluding current room)
            Long count = session.createQuery(
                "SELECT COUNT(r) FROM OfficeRoom r WHERE r.floor.id = :floorId AND r.roomNumber = :roomNumber AND r.id != :roomId", Long.class)
                .setParameter("floorId", floor.getId())
                .setParameter("roomNumber", room.getRoomNumber())
                .setParameter("roomId", id)
                .uniqueResult();

            if (count > 0) {
                return Response.status(Response.Status.CONFLICT)
                    .entity("A room with number " + room.getRoomNumber() + " already exists on this floor")
                    .build();
            }

            existingRoom.setName(room.getName());
            existingRoom.setRoomNumber(room.getRoomNumber());
            existingRoom.setFloor(floor);
            
            session.update(existingRoom);
            session.getTransaction().commit();
            
            return Response.ok(existingRoom).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteRoom(@PathParam("id") Long id) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            
            // Check if room exists
            OfficeRoom room = session.get(OfficeRoom.class, id);
            if (room == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("Room not found")
                    .build();
            }

            // Check if room has seats
            Long seatCount = session.createQuery(
                "SELECT COUNT(s) FROM Seat s WHERE s.room.id = :roomId", Long.class)
                .setParameter("roomId", id)
                .uniqueResult();

            if (seatCount > 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Cannot delete room that has seats")
                    .build();
            }
            
            session.delete(room);
            session.getTransaction().commit();
            
            return Response.noContent().build();
        }
    }

    @PATCH
    @Path("/{id}/geometry")
    public Response updateRoomGeometry(@PathParam("id") Long id, Map<String, Object> geometryData) {
        Session session = null;
        Transaction transaction = null;
        
        try {
            session = sessionFactory.openSession();
            transaction = session.beginTransaction();
            
            // Check if room exists
            OfficeRoom room = session.get(OfficeRoom.class, id);
            if (room == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("Room not found")
                    .build();
            }
            
            // Update room geometry
            updateGeometryProperties(room, geometryData);
            session.update(room);
            
            // Check if seat geometries were provided
            if (geometryData.containsKey("seats") && geometryData.get("seats") instanceof Map) {
                Map<?, ?> seatsMap = (Map<?, ?>) geometryData.get("seats");
                
                // Process each seat
                for (Map.Entry<?, ?> entry : seatsMap.entrySet()) {
                    if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Map)) {
                        continue; // Skip invalid entries
                    }
                    
                    String seatIdStr = (String) entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> seatGeometry = (Map<String, Object>) entry.getValue();
                    
                    Long seatId;
                    try {
                        seatId = Long.parseLong(seatIdStr);
                    } catch (NumberFormatException e) {
                        continue; // Skip invalid seat IDs
                    }
                    
                    // Find the seat and verify it belongs to this room
                    Seat seat = session.get(Seat.class, seatId);
                    if (seat != null && seat.getRoom().getId().equals(id)) {
                        // Update seat geometry
                        updateGeometryProperties(seat, seatGeometry);
                        session.update(seat);
                    }
                }
            }
            
            transaction.commit();
            
            // Query the room again to return fresh data
            OfficeRoom updatedRoom = session.createQuery(
                "SELECT r FROM OfficeRoom r WHERE r.id = :id", OfficeRoom.class)
                .setParameter("id", id)
                .uniqueResult();
                
            // Create a simplified response object to:
            // 1. Prevent serialization cycles in bidirectional relationships
            // 2. Control the exact shape of the API response
            Map<String, Object> response = new HashMap<>();
            response.put("id", updatedRoom.getId());
            response.put("name", updatedRoom.getName());
            response.put("roomNumber", updatedRoom.getRoomNumber());
            response.put("x", updatedRoom.getX());
            response.put("y", updatedRoom.getY());
            response.put("width", updatedRoom.getWidth());
            response.put("height", updatedRoom.getHeight());
            
            // Query the seats separately to ensure all data is loaded before session closes
            // This prevents LazyInitializationException when accessing the collection later
            Set<Seat> seats = session.createQuery(
                "SELECT s FROM Seat s WHERE s.room.id = :roomId", Seat.class)
                .setParameter("roomId", id)
                .getResultList()
                .stream()
                .collect(java.util.stream.Collectors.toSet());
                
            if (seats != null && !seats.isEmpty()) {
                // Add seat IDs to the response
                response.put("seats", seats.size());
            }
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Error updating geometry: " + e.getMessage())
                .build();
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }
    
    /**
     * Helper method to extract a float value from a geometry data map
     */
    private Float getFloatValue(Map<String, Object> geometryData, String key) {
        if (!geometryData.containsKey(key)) {
            return null;
        }
        
        Object value = geometryData.get(key);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return null;
    }
    
    /**
     * Update geometry properties for a room
     */
    private void updateGeometryProperties(OfficeRoom room, Map<String, Object> geometryData) {
        Float x = getFloatValue(geometryData, "x");
        if (x != null) room.setX(x);
        
        Float y = getFloatValue(geometryData, "y");
        if (y != null) room.setY(y);
        
        Float width = getFloatValue(geometryData, "width");
        if (width != null) room.setWidth(width);
        
        Float height = getFloatValue(geometryData, "height");
        if (height != null) room.setHeight(height);
    }
    
    /**
     * Update geometry properties for a seat
     */
    private void updateGeometryProperties(Seat seat, Map<String, Object> geometryData) {
        Float x = getFloatValue(geometryData, "x");
        if (x != null) seat.setX(x);
        
        Float y = getFloatValue(geometryData, "y");
        if (y != null) seat.setY(y);
        
        Float width = getFloatValue(geometryData, "width");
        if (width != null) seat.setWidth(width);
        
        Float height = getFloatValue(geometryData, "height");
        if (height != null) seat.setHeight(height);
        
        Float rotation = getFloatValue(geometryData, "rotation");
        if (rotation != null) seat.setRotation(rotation);
    }
} 