package com.officemanagement.resource;

import com.officemanagement.model.Employee;
import com.officemanagement.model.Floor;
import com.officemanagement.model.OfficeRoom;
import com.officemanagement.model.Seat;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.HashSet;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class StatsResourceTest extends BaseResourceTest {

    @BeforeEach
    public void setupTestData() {
        // Create test employees
        createTestEmployee("Test Employee 1", "Developer");
        createTestEmployee("Test Employee 2", "Designer");

        // Create test floors
        Floor floor = createTestFloor("Test Floor", 1);

        // Create test rooms
        OfficeRoom room = createTestRoom(floor, "Test Room", "101");

        // Create test seats
        createTestSeat(room, "A1");
        createTestSeat(room, "A2");
    }

    @Test
    public void testGetStats() {
        given()
        .when()
            .get(getApiPath("/stats"))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("totalEmployees", is(2))
            .body("totalFloors", is(1))
            .body("totalOffices", is(1))
            .body("totalSeats", is(2));
    }

    private void createTestEmployee(String fullName, String occupation) {
        Employee employee = new Employee();
        employee.setFullName(fullName);
        employee.setOccupation(occupation);
        employee.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(employee)
        .when()
            .post(getApiPath("/employees"))
        .then()
            .statusCode(Response.Status.CREATED.getStatusCode());
    }

    private Floor createTestFloor(String name, int floorNumber) {
        Floor floor = new Floor();
        floor.setName(name);
        floor.setFloorNumber(floorNumber);
        floor.setCreatedAt(LocalDateTime.now());

        Integer floorIdInt = given()
            .contentType(ContentType.JSON)
            .body(floor)
        .when()
            .post(getApiPath("/floors"))
        .then()
            .statusCode(Response.Status.CREATED.getStatusCode())
            .extract()
            .path("id");
            
        Long floorId = floorIdInt.longValue();
        floor.setId(floorId);
        return floor;
    }

    private OfficeRoom createTestRoom(Floor floor, String name, String roomNumber) {
        OfficeRoom room = new OfficeRoom();
        room.setName(name);
        room.setRoomNumber(roomNumber);
        room.setCreatedAt(LocalDateTime.now());
        
        // Create a new floor object with just the ID to avoid serialization issues
        Floor floorRef = new Floor();
        floorRef.setId(floor.getId());
        room.setFloor(floorRef);

        Integer roomIdInt = given()
            .contentType(ContentType.JSON)
            .body(room)
        .when()
            .post(getApiPath("/rooms"))
        .then()
            .statusCode(Response.Status.CREATED.getStatusCode())
            .extract()
            .path("id");
            
        Long roomId = roomIdInt.longValue();
        room.setId(roomId);
        return room;
    }

    private void createTestSeat(OfficeRoom room, String seatNumber) {
        Seat seat = new Seat();
        seat.setSeatNumber(seatNumber);
        seat.setCreatedAt(LocalDateTime.now());
        seat.setEmployees(new HashSet<>());
        
        // Create a new room object with just the ID to avoid serialization issues
        OfficeRoom roomRef = new OfficeRoom();
        roomRef.setId(room.getId());
        seat.setRoom(roomRef);

        // Make the request and validate the response
        io.restassured.response.Response response = given()
            .contentType(ContentType.JSON)
            .body(seat)
        .when()
            .post(getApiPath("/seats"))
        .then()
            .extract().response();

        if (response.getStatusCode() != 201) {
            throw new RuntimeException("Failed to create seat: " + response.getBody().asString());
        }
    }
} 