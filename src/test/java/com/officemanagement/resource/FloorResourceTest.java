package com.officemanagement.resource;

import com.officemanagement.model.Floor;
import com.officemanagement.model.FloorPlanimetry;
import com.officemanagement.model.OfficeRoom;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class FloorResourceTest extends BaseResourceTest {

    @Test
    public void testCreateFloor() {
        Floor floor = new Floor();
        floor.setName("First Floor");
        floor.setFloorNumber(1);
        floor.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(floor)
        .when()
            .post(getApiPath("/floors"))
        .then()
            .statusCode(Response.Status.CREATED.getStatusCode())
            .body("id", notNullValue())
            .body("name", equalTo("First Floor"))
            .body("floorNumber", equalTo(1));
    }

    @Test
    public void testGetAllFloors() {
        // Create test floors
        createTestFloor("Ground Floor", 0);
        createTestFloor("First Floor", 1);
        createTestFloor("Second Floor", 2);

        given()
        .when()
            .get(getApiPath("/floors"))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("$", hasSize(greaterThanOrEqualTo(3)))
            .body("find { it.name == 'Ground Floor' }.floorNumber", equalTo(0))
            .body("find { it.name == 'First Floor' }.floorNumber", equalTo(1))
            .body("find { it.name == 'Second Floor' }.floorNumber", equalTo(2));
    }

    @Test
    public void testGetFloor() {
        // First create a floor
        Floor floor = new Floor();
        floor.setName("Test Floor");
        floor.setFloorNumber(3);
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

        // Then get the floor
        given()
        .when()
            .get(getApiPath("/floors/" + floorId))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("name", equalTo("Test Floor"))
            .body("floorNumber", equalTo(3))
            .body("rooms", notNullValue());
    }

    @Test
    public void testUpdateFloor() {
        // First create a floor
        Floor floor = new Floor();
        floor.setName("Original Name");
        floor.setFloorNumber(4);
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

        // Update the floor
        floor.setName("Updated Name");
        
        given()
            .contentType(ContentType.JSON)
            .body(floor)
        .when()
            .put(getApiPath("/floors/" + floorId))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("name", equalTo("Updated Name"))
            .body("floorNumber", equalTo(4));
    }

    @Test
    public void testDeleteFloor() {
        // First create a floor
        Floor floor = new Floor();
        floor.setName("Floor to Delete");
        floor.setFloorNumber(5);
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

        // Delete the floor
        given()
        .when()
            .delete(getApiPath("/floors/" + floorId))
        .then()
            .statusCode(Response.Status.NO_CONTENT.getStatusCode());

        // Verify floor is deleted
        given()
        .when()
            .get(getApiPath("/floors/" + floorId))
        .then()
            .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testCreateFloorWithInvalidData() {
        // Test with empty floor
        Floor emptyFloor = new Floor();
        emptyFloor.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(emptyFloor)
        .when()
            .post(getApiPath("/floors"))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // Test with null values
        Floor nullFloor = new Floor();
        nullFloor.setName(null);
        nullFloor.setFloorNumber(null);
        nullFloor.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(nullFloor)
        .when()
            .post(getApiPath("/floors"))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // Test with invalid floor number
        Floor invalidFloor = new Floor();
        invalidFloor.setName("Test Floor");
        invalidFloor.setFloorNumber(-1);  // Negative floor number
        invalidFloor.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(invalidFloor)
        .when()
            .post(getApiPath("/floors"))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // Test with invalid content type
        given()
            .contentType(ContentType.TEXT)
            .body("Invalid data")
        .when()
            .post(getApiPath("/floors"))
        .then()
            .statusCode(Response.Status.UNSUPPORTED_MEDIA_TYPE.getStatusCode());
    }

    @Test
    public void testUpdateNonExistentFloor() {
        Floor floor = new Floor();
        floor.setName("Test Floor");
        floor.setFloorNumber(1);
        floor.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(floor)
        .when()
            .put(getApiPath("/floors/99999"))
        .then()
            .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testDeleteFloorWithRooms() {
        // Create a floor
        Floor floor = new Floor();
        floor.setName("Test Floor");
        floor.setFloorNumber(1);
        floor.setCreatedAt(LocalDateTime.now());
        session.save(floor);

        // Create a room in the floor
        OfficeRoom room = new OfficeRoom();
        room.setName("Test Room");
        room.setRoomNumber("101");
        room.setFloor(floor);
        room.setCreatedAt(LocalDateTime.now());
        session.save(room);
        
        commitAndStartNewTransaction();

        // Attempt to delete floor with rooms (should fail with BAD_REQUEST)
        given()
        .when()
            .delete(getApiPath("/floors/" + floor.getId()))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    public void testDuplicateFloorNumber() {
        // Create first floor
        Floor floor1 = new Floor();
        floor1.setName("First Floor");
        floor1.setFloorNumber(1);
        floor1.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(floor1)
        .when()
            .post(getApiPath("/floors"))
        .then()
            .statusCode(Response.Status.CREATED.getStatusCode());

        // Try to create another floor with same floor number
        Floor floor2 = new Floor();
        floor2.setName("Another First Floor");
        floor2.setFloorNumber(1);  // Same floor number
        floor2.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(floor2)
        .when()
            .post(getApiPath("/floors"))
        .then()
            .statusCode(Response.Status.CONFLICT.getStatusCode());
    }

    @Test
    public void testGetFloorPlan() {
        // First create a floor
        Floor floor = new Floor();
        floor.setName("Floor with SVG");
        floor.setFloorNumber(10);
        floor.setCreatedAt(LocalDateTime.now());
        
        // Save the floor and get its ID
        session.save(floor);
        commitAndStartNewTransaction();
        Long floorId = floor.getId();
        
        // Create and save a planimetry for the floor
        String svgContent = "<svg width=\"100\" height=\"100\"><rect width=\"100\" height=\"100\" style=\"fill:blue\"/></svg>";
        FloorPlanimetry planimetry = new FloorPlanimetry(floor, svgContent);
        session.save(planimetry);
        commitAndStartNewTransaction();
        
        // Test getting the floor planimetry
        given()
        .when()
            .get(getApiPath("/floors/" + floorId + "/svg"))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .contentType("image/svg+xml")
            .header("Content-Disposition", containsString("inline; filename=floor" + floorId + ".svg"))
            .body(equalTo(svgContent));
            
        // Test getting non-existent floor planimetry
        given()
        .when()
            .get(getApiPath("/floors/9999/svg"))
        .then()
            .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testUpdateFloorPlan() {
        // First create a floor
        Floor floor = new Floor();
        floor.setName("Floor with SVG");
        floor.setFloorNumber(11);
        floor.setCreatedAt(LocalDateTime.now());
        session.save(floor);
        commitAndStartNewTransaction();
        Long floorId = floor.getId();
        
        // Create the SVG content
        String svgContent = "<svg width=\"100\" height=\"100\"><rect width=\"100\" height=\"100\" style=\"fill:red\"/></svg>";
        
        // Update the floor plan
        given()
            .contentType("text/plain")
            .body(svgContent)
        .when()
            .put(getApiPath("/floors/" + floorId + "/svg"))
        .then()
            .statusCode(Response.Status.OK.getStatusCode());
        
        // Verify the floor plan was updated
        given()
        .when()
            .get(getApiPath("/floors/" + floorId + "/svg"))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .contentType("image/svg+xml")
            .body(equalTo(svgContent));
    }

    private void createTestFloor(String name, int floorNumber) {
        Floor floor = new Floor();
        floor.setName(name);
        floor.setFloorNumber(floorNumber);
        floor.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(floor)
        .when()
            .post(getApiPath("/floors"))
        .then()
            .statusCode(Response.Status.CREATED.getStatusCode());
    }
} 