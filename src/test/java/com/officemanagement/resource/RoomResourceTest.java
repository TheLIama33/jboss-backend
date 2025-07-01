package com.officemanagement.resource;

import com.officemanagement.model.Floor;
import com.officemanagement.model.OfficeRoom;
import com.officemanagement.model.Seat;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;


public class RoomResourceTest extends BaseResourceTest {

    @Test
    public void testCreateRoomWithInvalidData() {
        // Test with empty room
        OfficeRoom emptyRoom = new OfficeRoom();
        emptyRoom.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(emptyRoom)
        .when()
            .post(getApiPath("/rooms"))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // Test with null values
        OfficeRoom nullRoom = new OfficeRoom();
        nullRoom.setName(null);
        nullRoom.setRoomNumber(null);
        nullRoom.setFloor(null);
        nullRoom.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(nullRoom)
        .when()
            .post(getApiPath("/rooms"))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // Test with non-existent floor
        Floor nonExistentFloor = new Floor();
        nonExistentFloor.setId(99999L);

        OfficeRoom roomWithInvalidFloor = new OfficeRoom();
        roomWithInvalidFloor.setName("Test Room");
        roomWithInvalidFloor.setRoomNumber("101");
        roomWithInvalidFloor.setFloor(nonExistentFloor);
        roomWithInvalidFloor.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(roomWithInvalidFloor)
        .when()
            .post(getApiPath("/rooms"))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // Test with invalid content type
        given()
            .contentType(ContentType.TEXT)
            .body("Invalid data")
        .when()
            .post(getApiPath("/rooms"))
        .then()
            .statusCode(Response.Status.UNSUPPORTED_MEDIA_TYPE.getStatusCode());
    }

    @Test
    public void testDuplicateRoomNumber() {
        // Create a floor first
        Floor floor = new Floor();
        floor.setName("Test Floor");
        floor.setFloorNumber(1);
        floor.setCreatedAt(LocalDateTime.now());
        session.save(floor);
        commitAndStartNewTransaction();

        // Create first room
        OfficeRoom room1 = new OfficeRoom();
        room1.setName("First Room");
        room1.setRoomNumber("101");
        room1.setFloor(floor);
        room1.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(room1)
        .when()
            .post(getApiPath("/rooms"))
        .then()
            .statusCode(Response.Status.CREATED.getStatusCode());

        // Try to create another room with same number on same floor
        OfficeRoom room2 = new OfficeRoom();
        room2.setName("Another Room");
        room2.setRoomNumber("101");  // Same room number
        room2.setFloor(floor);
        room2.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(room2)
        .when()
            .post(getApiPath("/rooms"))
        .then()
            .statusCode(Response.Status.CONFLICT.getStatusCode());
    }

    @Test
    public void testDeleteRoomWithSeats() {
        // Create necessary test data
        Floor floor = new Floor();
        floor.setName("Test Floor");
        floor.setFloorNumber(1);
        floor.setCreatedAt(LocalDateTime.now());
        session.save(floor);

        OfficeRoom room = new OfficeRoom();
        room.setName("Test Room");
        room.setRoomNumber("101");
        room.setFloor(floor);
        room.setCreatedAt(LocalDateTime.now());
        session.save(room);

        Seat seat = new Seat();
        seat.setSeatNumber("A1");
        seat.setRoom(room);
        seat.setCreatedAt(LocalDateTime.now());
        session.save(seat);
        
        commitAndStartNewTransaction();

        // Attempt to delete room with seats (should fail)
        given()
        .when()
            .delete(getApiPath("/rooms/" + room.getId()))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    public void testGetRoomDoesNotIncludePlanimetry() {
        // Create test data
        Floor floor = new Floor();
        floor.setName("Test Floor");
        floor.setFloorNumber(1);
        floor.setCreatedAt(LocalDateTime.now());
        session.save(floor);
        
        // Commit transaction to ensure floor is persisted before adding planimetry
        commitAndStartNewTransaction();
        
        // Add planimetry data to the floor
        String planimetryData = "{\"width\":800,\"height\":600,\"background\":\"#f5f5f5\"}";
        session.createNativeQuery(
            "INSERT INTO floor_planimetry (floor_id, last_updated, planimetry) VALUES (?, ?, ?)")
            .setParameter(1, floor.getId())
            .setParameter(2, LocalDateTime.now())
            .setParameter(3, planimetryData)
            .executeUpdate();

        OfficeRoom room = new OfficeRoom();
        room.setName("Test Room");
        room.setRoomNumber("101");
        room.setFloor(floor);
        room.setCreatedAt(LocalDateTime.now());
        session.save(room);
        
        Seat seat = new Seat();
        seat.setSeatNumber("A1");
        seat.setRoom(room);
        seat.setCreatedAt(LocalDateTime.now());
        session.save(seat);
        
        commitAndStartNewTransaction();

        // Get the room and verify that it doesn't include planimetry data
        given()
            .contentType(ContentType.JSON)
        .when()
            .get(getApiPath("/rooms/" + room.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("id", equalTo(room.getId().intValue()))
            .body("name", equalTo(room.getName()))
            .body("roomNumber", equalTo(room.getRoomNumber()))
            .body("floor.id", equalTo(floor.getId().intValue()))
            .body("floor.name", equalTo(floor.getName()))
            .body("floor.floorNumber", equalTo(floor.getFloorNumber()))
            // Verify that planimetry is not included
            .body("floor.planimetry", nullValue());
    }

    @Test
    public void testGetRoomSeatsDoesNotIncludePlanimetry() {
        // Create test data
        Floor floor = new Floor();
        floor.setName("Test Floor");
        floor.setFloorNumber(1);
        floor.setCreatedAt(LocalDateTime.now());
        session.save(floor);
        
        // Commit transaction to ensure floor is persisted before adding planimetry
        commitAndStartNewTransaction();
        
        // Add planimetry data to the floor
        String planimetryData = "{\"width\":800,\"height\":600,\"background\":\"#f5f5f5\"}";
        session.createNativeQuery(
            "INSERT INTO floor_planimetry (floor_id, last_updated, planimetry) VALUES (?, ?, ?)")
            .setParameter(1, floor.getId())
            .setParameter(2, LocalDateTime.now())
            .setParameter(3, planimetryData)
            .executeUpdate();

        OfficeRoom room = new OfficeRoom();
        room.setName("Test Room");
        room.setRoomNumber("101");
        room.setFloor(floor);
        room.setCreatedAt(LocalDateTime.now());
        session.save(room);
        
        Seat seat = new Seat();
        seat.setSeatNumber("A1");
        seat.setRoom(room);
        seat.setCreatedAt(LocalDateTime.now());
        session.save(seat);
        
        commitAndStartNewTransaction();

        // Get the room seats and verify the response format
        given()
            .contentType(ContentType.JSON)
        .when()
            .get(getApiPath("/rooms/" + room.getId() + "/seats"))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("[0].id", equalTo(seat.getId().intValue()))
            .body("[0].seatNumber", equalTo(seat.getSeatNumber()))
            .body("[0].roomId", equalTo(room.getId().intValue()))
            // Verify that no planimetry or room object is included
            .body("[0].room", nullValue())
            .body("[0].planimetry", nullValue());
    }
} 