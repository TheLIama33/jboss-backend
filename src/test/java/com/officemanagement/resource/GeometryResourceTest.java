package com.officemanagement.resource;

import com.officemanagement.model.Floor;
import com.officemanagement.model.OfficeRoom;
import com.officemanagement.model.Seat;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class GeometryResourceTest extends BaseResourceTest {

    @Test
    public void testUpdateRoomGeometry() {
        // Create test data
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
        // Default geometry values
        room.setX(0f);
        room.setY(0f);
        room.setWidth(300f);
        room.setHeight(200f);
        session.save(room);
        
        commitAndStartNewTransaction();

        // Create geometry update payload
        Map<String, Object> geometryData = new HashMap<>();
        geometryData.put("x", 150);
        geometryData.put("y", 200);
        geometryData.put("width", 350);
        geometryData.put("height", 250);
        
        // Update room geometry
        given()
            .contentType(ContentType.JSON)
            .body(geometryData)
        .when()
            .patch(getApiPath("/rooms/" + room.getId() + "/geometry"))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("id", equalTo(room.getId().intValue()))
            .body("x", equalTo(150.0f))
            .body("y", equalTo(200.0f))
            .body("width", equalTo(350.0f))
            .body("height", equalTo(250.0f));
            
        // Verify database was updated correctly
        session.clear();
        OfficeRoom updatedRoom = session.get(OfficeRoom.class, room.getId());
        assert updatedRoom.getX() == 150.0f;
        assert updatedRoom.getY() == 200.0f;
        assert updatedRoom.getWidth() == 350.0f;
        assert updatedRoom.getHeight() == 250.0f;
    }
    
    @Test
    public void testUpdateRoomAndSeatGeometries() {
        // Create test data
        Floor floor = new Floor();
        floor.setName("Test Floor");
        floor.setFloorNumber(1);
        floor.setCreatedAt(LocalDateTime.now());
        session.save(floor);

        OfficeRoom room = new OfficeRoom();
        room.setName("Conference Room");
        room.setRoomNumber("101");
        room.setFloor(floor);
        room.setCreatedAt(LocalDateTime.now());
        session.save(room);

        Seat seat1 = new Seat();
        seat1.setSeatNumber("101-A1");
        seat1.setRoom(room);
        seat1.setCreatedAt(LocalDateTime.now());
        session.save(seat1);
        
        Seat seat2 = new Seat();
        seat2.setSeatNumber("101-A2");
        seat2.setRoom(room);
        seat2.setCreatedAt(LocalDateTime.now());
        session.save(seat2);
        
        commitAndStartNewTransaction();
        
        // Create geometry update payload for room and seats
        Map<String, Object> seatGeometry1 = new HashMap<>();
        seatGeometry1.put("x", 170);
        seatGeometry1.put("y", 220);
        seatGeometry1.put("rotation", 45);
        
        Map<String, Object> seatGeometry2 = new HashMap<>();
        seatGeometry2.put("x", 260);
        seatGeometry2.put("y", 220);
        seatGeometry2.put("width", 80);
        seatGeometry2.put("height", 80);
        
        Map<String, Object> seatsMap = new HashMap<>();
        seatsMap.put(seat1.getId().toString(), seatGeometry1);
        seatsMap.put(seat2.getId().toString(), seatGeometry2);
        
        Map<String, Object> geometryData = new HashMap<>();
        geometryData.put("x", 150);
        geometryData.put("y", 200);
        geometryData.put("width", 350);
        geometryData.put("height", 250);
        geometryData.put("seats", seatsMap);
        
        // Update room and seat geometries
        given()
            .contentType(ContentType.JSON)
            .body(geometryData)
        .when()
            .patch(getApiPath("/rooms/" + room.getId() + "/geometry"))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("id", equalTo(room.getId().intValue()))
            .body("x", equalTo(150.0f))
            .body("y", equalTo(200.0f))
            .body("width", equalTo(350.0f))
            .body("height", equalTo(250.0f))
            .body("seats", equalTo(2));
            
        // Verify database was updated correctly
        session.clear();
        OfficeRoom updatedRoom = session.get(OfficeRoom.class, room.getId());
        assert updatedRoom.getX() == 150.0f;
        assert updatedRoom.getY() == 200.0f;
        assert updatedRoom.getWidth() == 350.0f;
        assert updatedRoom.getHeight() == 250.0f;
        
        Seat updatedSeat1 = session.get(Seat.class, seat1.getId());
        assert updatedSeat1.getX() == 170.0f;
        assert updatedSeat1.getY() == 220.0f;
        assert updatedSeat1.getRotation() == 45.0f;
        
        Seat updatedSeat2 = session.get(Seat.class, seat2.getId());
        assert updatedSeat2.getX() == 260.0f;
        assert updatedSeat2.getY() == 220.0f;
        assert updatedSeat2.getWidth() == 80.0f;
        assert updatedSeat2.getHeight() == 80.0f;
    }
    
    @Test
    public void testUpdateNonExistentRoom() {
        Map<String, Object> geometryData = new HashMap<>();
        geometryData.put("x", 150);
        geometryData.put("y", 200);
        
        // Try to update non-existent room
        given()
            .contentType(ContentType.JSON)
            .body(geometryData)
        .when()
            .patch(getApiPath("/rooms/99999/geometry"))
        .then()
            .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }
    
    @Test
    public void testPartialGeometryUpdate() {
        // Create test data
        Floor floor = new Floor();
        floor.setName("Test Floor");
        floor.setFloorNumber(1);
        floor.setCreatedAt(LocalDateTime.now());
        session.save(floor);

        OfficeRoom room = new OfficeRoom();
        room.setName("Test Room");
        room.setRoomNumber("101");
        room.setFloor(floor);
        room.setX(10f);
        room.setY(20f);
        room.setWidth(300f);
        room.setHeight(200f);
        room.setCreatedAt(LocalDateTime.now());
        session.save(room);
        
        commitAndStartNewTransaction();

        // Create partial update - only updating x and width
        Map<String, Object> geometryData = new HashMap<>();
        geometryData.put("x", 50);
        geometryData.put("width", 400);
        
        // Update room geometry
        given()
            .contentType(ContentType.JSON)
            .body(geometryData)
        .when()
            .patch(getApiPath("/rooms/" + room.getId() + "/geometry"))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("id", equalTo(room.getId().intValue()))
            .body("x", equalTo(50.0f))
            .body("y", equalTo(20.0f)) // Unchanged
            .body("width", equalTo(400.0f))
            .body("height", equalTo(200.0f)); // Unchanged
            
        // Verify database was updated correctly
        session.clear();
        OfficeRoom updatedRoom = session.get(OfficeRoom.class, room.getId());
        assert updatedRoom.getX() == 50.0f;
        assert updatedRoom.getY() == 20.0f; // Should remain unchanged
        assert updatedRoom.getWidth() == 400.0f;
        assert updatedRoom.getHeight() == 200.0f; // Should remain unchanged
    }
} 