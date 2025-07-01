package com.officemanagement.resource;

import com.officemanagement.model.Floor;
import com.officemanagement.model.FloorPlanimetry;
import com.officemanagement.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.List;

@Path("/floors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FloorResource {
    private final SessionFactory sessionFactory;

    public FloorResource() {
        this.sessionFactory = HibernateUtil.getSessionFactory();
    }

    @GET
    public Response getAllFloors() {
        try (Session session = sessionFactory.openSession()) {
            List<Floor> floors = session.createQuery(
                    "select new Floor(f.id, f.name, f.floorNumber) from Floor f",
                    Floor.class).list();
            return Response.ok(floors).build();
        }
    }

    @GET
    @Path("/{id}")
    public Response getFloor(@PathParam("id") Long id) {
        try (Session session = sessionFactory.openSession()) {
            // Using criteria to fetch the floor and its associations
            Floor floor = session.createQuery(
                    "select distinct f from Floor f " +
                            "left join fetch f.rooms r " +
                            "left join fetch r.seats s " +
                            "left join fetch s.employees " +
                            "where f.id = :id",
                    Floor.class)
                    .setParameter("id", id)
                    .uniqueResult();

            if (floor == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            return Response.ok(floor).build();
        }
    }

    @GET
    @Path("/{id}/svg")
    @Produces("image/svg+xml")
    public Response getFloorPlan(@PathParam("id") Long id) {
        try (Session session = sessionFactory.openSession()) {
            // Query the floor_planimetry table directly
            String planimetry = session.createQuery(
                    "select fp.planimetry from FloorPlanimetry fp where fp.floorId = :id", String.class)
                    .setParameter("id", id)
                    .uniqueResult();

            if (planimetry == null || planimetry.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("No floor plan found for floor ID: " + id)
                        .type(MediaType.TEXT_PLAIN)
                        .build();
            }

            return Response.ok(planimetry)
                    .header(HttpHeaders.CONTENT_TYPE, "image/svg+xml")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=floor" + id + ".svg")
                    .build();
        }
    }

    @POST
    public Response createFloor(Floor floor) {
        // Validate input
        if (floor == null || floor.getName() == null || floor.getName().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Floor name is required")
                    .build();
        }

        if (floor.getFloorNumber() == null || floor.getFloorNumber() < 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Valid floor number is required (must be 0 or greater)")
                    .build();
        }

        try (Session session = sessionFactory.openSession()) {
            // Check for duplicate floor number
            Long count = session.createQuery(
                    "SELECT COUNT(f) FROM Floor f WHERE f.floorNumber = :floorNumber", Long.class)
                    .setParameter("floorNumber", floor.getFloorNumber())
                    .uniqueResult();

            if (count > 0) {
                return Response.status(Response.Status.CONFLICT)
                        .entity("A floor with number " + floor.getFloorNumber() + " already exists")
                        .build();
            }

            session.beginTransaction();
            floor.setCreatedAt(LocalDateTime.now());
            session.save(floor);
            session.getTransaction().commit();
            return Response.status(Response.Status.CREATED).entity(floor).build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response updateFloor(@PathParam("id") Long id, Floor floor) {
        // Validate input
        if (floor == null || floor.getName() == null || floor.getName().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Floor name is required")
                    .build();
        }

        if (floor.getFloorNumber() == null || floor.getFloorNumber() < 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Valid floor number is required (must be 0 or greater)")
                    .build();
        }

        try (Session session = sessionFactory.openSession()) {
            Floor existingFloor = session.get(Floor.class, id);
            if (existingFloor == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Floor not found")
                        .build();
            }

            // Check for duplicate floor number (excluding current floor)
            Long count = session.createQuery(
                    "SELECT COUNT(f) FROM Floor f WHERE f.floorNumber = :floorNumber AND f.id != :id", Long.class)
                    .setParameter("floorNumber", floor.getFloorNumber())
                    .setParameter("id", id)
                    .uniqueResult();

            if (count > 0) {
                return Response.status(Response.Status.CONFLICT)
                        .entity("A floor with number " + floor.getFloorNumber() + " already exists")
                        .build();
            }

            session.beginTransaction();
            existingFloor.setName(floor.getName());
            existingFloor.setFloorNumber(floor.getFloorNumber());
            session.update(existingFloor);
            
            // Fetch the floor with all associations BEFORE committing the transaction
            // This prevents LazyInitializationException during JSON serialization
            Floor updatedFloor = session.createQuery(
                    "select distinct f from Floor f " +
                            "left join fetch f.rooms r " +
                            "left join fetch r.seats s " +
                            "left join fetch s.employees " +
                            "where f.id = :id",
                    Floor.class)
                    .setParameter("id", id)
                    .uniqueResult();
            
            session.getTransaction().commit();
            return Response.ok(updatedFloor).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteFloor(@PathParam("id") Long id) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            Floor floor = session.get(Floor.class, id);

            if (floor == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Floor not found")
                        .build();
            }

            // Check if floor has rooms
            Long roomCount = session.createQuery(
                    "SELECT COUNT(r) FROM OfficeRoom r WHERE r.floor.id = :floorId", Long.class)
                    .setParameter("floorId", id)
                    .uniqueResult();

            if (roomCount > 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Cannot delete floor that has rooms")
                        .build();
            }

            // Delete associated planimetry data if exists
            session.createQuery("DELETE FROM FloorPlanimetry fp WHERE fp.floorId = :floorId")
                    .setParameter("floorId", id)
                    .executeUpdate();

            session.delete(floor);
            session.getTransaction().commit();
            return Response.noContent().build();
        }
    }

    @PUT
    @Path("/{id}/svg")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response updateFloorPlan(@PathParam("id") Long id, String svgData) {
        if (svgData == null || svgData.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("SVG data cannot be empty")
                    .build();
        }

        try (Session session = sessionFactory.openSession()) {
            Floor floor = session.get(Floor.class, id);
            if (floor == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Floor not found")
                        .build();
            }

            session.beginTransaction();
            
            // Check if planimetry data exists
            FloorPlanimetry planimetry = session.get(FloorPlanimetry.class, id);
            
            if (planimetry == null) {
                // Create new planimetry entity
                planimetry = new FloorPlanimetry();
                planimetry.setFloor(floor);
                planimetry.setPlanimetry(svgData);
                planimetry.setLastUpdated(LocalDateTime.now());
                session.save(planimetry);
            } else {
                // Update existing planimetry
                planimetry.setPlanimetry(svgData);
                planimetry.setLastUpdated(LocalDateTime.now());
                session.update(planimetry);
            }

            session.getTransaction().commit();
            return Response.ok().build();
        }
    }
}