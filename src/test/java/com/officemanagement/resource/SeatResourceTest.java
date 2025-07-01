package com.officemanagement.resource;

import com.officemanagement.model.Floor;
import com.officemanagement.model.OfficeRoom;
import com.officemanagement.model.Seat;
import com.officemanagement.model.Employee;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;


public class SeatResourceTest extends BaseResourceTest {

    @Test
    public void testCreateSeatWithInvalidData() {
        // Test with empty seat
        Seat emptySeat = new Seat();
        emptySeat.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(emptySeat)
        .when()
            .post(getApiPath("/seats"))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // Test with null values
        Seat nullSeat = new Seat();
        nullSeat.setSeatNumber(null);
        nullSeat.setRoom(null);
        nullSeat.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(nullSeat)
        .when()
            .post(getApiPath("/seats"))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // Test with non-existent room
        OfficeRoom nonExistentRoom = new OfficeRoom();
        nonExistentRoom.setId(99999L);

        Seat seatWithInvalidRoom = new Seat();
        seatWithInvalidRoom.setSeatNumber("A1");
        seatWithInvalidRoom.setRoom(nonExistentRoom);
        seatWithInvalidRoom.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(seatWithInvalidRoom)
        .when()
            .post(getApiPath("/seats"))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // Test with invalid content type
        given()
            .contentType(ContentType.TEXT)
            .body("Invalid data")
        .when()
            .post(getApiPath("/seats"))
        .then()
            .statusCode(Response.Status.UNSUPPORTED_MEDIA_TYPE.getStatusCode());
    }

    @Test
    public void testDuplicateSeatNumber() {
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
        
        commitAndStartNewTransaction();

        // Create first seat
        Seat seat1 = new Seat();
        seat1.setSeatNumber("A1");
        seat1.setRoom(room);
        seat1.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(seat1)
        .when()
            .post(getApiPath("/seats"))
        .then()
            .statusCode(Response.Status.CREATED.getStatusCode());

        // Try to create another seat with same number in same room
        Seat seat2 = new Seat();
        seat2.setSeatNumber("A1");  // Same seat number
        seat2.setRoom(room);
        seat2.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(seat2)
        .when()
            .post(getApiPath("/seats"))
        .then()
            .statusCode(Response.Status.CONFLICT.getStatusCode());
    }

    @Test
    public void testDeleteSeatWithAssignedEmployee() {
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

        Employee employee = new Employee();
        employee.setFullName("Test Employee");
        employee.setOccupation("Tester");
        employee.setCreatedAt(LocalDateTime.now());
        session.save(employee);

        // Assign employee to seat
        employee.addSeat(seat);
        session.flush();
        commitAndStartNewTransaction();

        // Delete seat with assigned employee (should succeed now)
        given()
        .when()
            .delete(getApiPath("/seats/" + seat.getId()))
        .then()
            .statusCode(Response.Status.NO_CONTENT.getStatusCode());

        // Verify the seat is deleted
        session.clear();
        Seat deletedSeat = session.get(Seat.class, seat.getId());
        assertFalse(deletedSeat != null, "Seat should be deleted");

        // Verify the employee still exists but no longer has the seat assigned
        Employee updatedEmployee = session.get(Employee.class, employee.getId());
        assertTrue(updatedEmployee != null, "Employee should still exist");
        assertTrue(updatedEmployee.getSeats().isEmpty(), "Employee should not have any seats assigned");
    }

    @Test
    public void testUpdateNonExistentSeat() {
        Seat seat = new Seat();
        seat.setSeatNumber("A1");
        seat.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(seat)
        .when()
            .put(getApiPath("/seats/99999"))
        .then()
            .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testMultipleEmployeesPerSeat() {
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

        // Create a seat
        Seat seat = new Seat();
        seat.setSeatNumber("A1");
        seat.setRoom(room);
        seat.setCreatedAt(LocalDateTime.now());
        session.save(seat);

        // Create first employee
        Employee employee1 = new Employee();
        employee1.setFullName("Test Employee 1");
        employee1.setOccupation("Developer");
        employee1.setCreatedAt(LocalDateTime.now());
        session.save(employee1);

        // Create second employee
        Employee employee2 = new Employee();
        employee2.setFullName("Test Employee 2");
        employee2.setOccupation("Designer");
        employee2.setCreatedAt(LocalDateTime.now());
        session.save(employee2);

        commitAndStartNewTransaction();

        // Assign first employee to seat
        given()
        .when()
            .put(getApiPath("/employees/" + employee1.getId() + "/assign-seat/" + seat.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(1))
            .body("seats[0].id", equalTo(seat.getId().intValue()));

        // Assign second employee to the same seat
        given()
        .when()
            .put(getApiPath("/employees/" + employee2.getId() + "/assign-seat/" + seat.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(1))
            .body("seats[0].id", equalTo(seat.getId().intValue()));

        // Verify the seat has both employees using a direct database query
        session.clear(); // Clear the session to ensure we get fresh data
        Seat verifiedSeat = session.createQuery(
            "SELECT DISTINCT s FROM Seat s " +
            "LEFT JOIN FETCH s.employees " +
            "WHERE s.id = :seatId", 
            Seat.class)
            .setParameter("seatId", seat.getId())
            .uniqueResult();
        
        assertEquals(2, verifiedSeat.getEmployees().size());
        assertTrue(verifiedSeat.getEmployees().stream()
            .map(Employee::getFullName)
            .anyMatch(name -> name.equals("Test Employee 1")));
        assertTrue(verifiedSeat.getEmployees().stream()
            .map(Employee::getFullName)
            .anyMatch(name -> name.equals("Test Employee 2")));

        // Unassign first employee
        given()
        .when()
            .delete(getApiPath("/employees/" + employee1.getId() + "/unassign-seat/" + seat.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode());

        // Verify seat still has second employee using a direct database query
        session.clear(); // Clear the session to ensure we get fresh data
        verifiedSeat = session.createQuery(
            "SELECT DISTINCT s FROM Seat s " +
            "LEFT JOIN FETCH s.employees " +
            "WHERE s.id = :seatId", 
            Seat.class)
            .setParameter("seatId", seat.getId())
            .uniqueResult();
        
        assertEquals(1, verifiedSeat.getEmployees().size());
        assertEquals("Test Employee 2", verifiedSeat.getEmployees().iterator().next().getFullName());
        assertEquals("Designer", verifiedSeat.getEmployees().iterator().next().getOccupation());
    }

    @Test
    public void testAdvancedMultipleEmployeesPerSeat() {
        // Create necessary test data
        Floor floor = new Floor();
        floor.setName("Advanced Test Floor");
        floor.setFloorNumber(3);
        floor.setCreatedAt(LocalDateTime.now());
        session.save(floor);

        OfficeRoom room = new OfficeRoom();
        room.setName("Advanced Test Room");
        room.setRoomNumber("301");
        room.setFloor(floor);
        room.setCreatedAt(LocalDateTime.now());
        session.save(room);

        // Create a seat
        Seat seat = new Seat();
        seat.setSeatNumber("Advanced Test Seat");
        seat.setRoom(room);
        seat.setCreatedAt(LocalDateTime.now());
        session.save(seat);

        // Create multiple employees (5 employees)
        Employee[] employees = new Employee[5];
        for (int i = 0; i < 5; i++) {
            employees[i] = new Employee();
            employees[i].setFullName("Advanced Test Employee " + (i + 1));
            employees[i].setOccupation("Role " + (i + 1));
            employees[i].setCreatedAt(LocalDateTime.now());
            session.save(employees[i]);
        }
        
        commitAndStartNewTransaction();
        flushAndClear();

        // Assign all 5 employees to the same seat
        for (int i = 0; i < 5; i++) {
            given()
            .when()
                .put(getApiPath("/employees/" + employees[i].getId() + "/assign-seat/" + seat.getId()))
            .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("seats", hasSize(1))
                .body("seats[0].id", equalTo(seat.getId().intValue()));
        }

        // Verify the seat has all 5 employees using a direct database query
        session.clear(); // Clear the session to ensure we get fresh data
        Seat verifiedSeat = session.createQuery(
            "SELECT DISTINCT s FROM Seat s " +
            "LEFT JOIN FETCH s.employees " +
            "WHERE s.id = :seatId", 
            Seat.class)
            .setParameter("seatId", seat.getId())
            .uniqueResult();
        
        assertEquals(5, verifiedSeat.getEmployees().size());
        
        // Verify each employee is correctly assigned
        for (int i = 0; i < 5; i++) {
            final Long employeeId = employees[i].getId();
            assertTrue(verifiedSeat.getEmployees().stream()
                .map(Employee::getId)
                .anyMatch(id -> id.equals(employeeId)));
        }

        // Unassign employees one by one and verify the remaining count
        for (int i = 0; i < 4; i++) {
            given()
            .when()
                .delete(getApiPath("/employees/" + employees[i].getId() + "/unassign-seat/" + seat.getId()))
            .then()
                .statusCode(Response.Status.OK.getStatusCode());

            // Verify the seat has one less employee
            session.clear();
            verifiedSeat = session.createQuery(
                "SELECT DISTINCT s FROM Seat s " +
                "LEFT JOIN FETCH s.employees " +
                "WHERE s.id = :seatId", 
                Seat.class)
                .setParameter("seatId", seat.getId())
                .uniqueResult();
            
            assertEquals(4 - i, verifiedSeat.getEmployees().size());
            
            // Verify the unassigned employee is no longer in the seat
            final Long unassignedEmployeeId = employees[i].getId();
            assertFalse(verifiedSeat.getEmployees().stream()
                .map(Employee::getId)
                .anyMatch(id -> id.equals(unassignedEmployeeId)));
            
            // Verify the remaining employees are still assigned
            for (int j = i + 1; j < 5; j++) {
                final Long remainingEmployeeId = employees[j].getId();
                assertTrue(verifiedSeat.getEmployees().stream()
                    .map(Employee::getId)
                    .anyMatch(id -> id.equals(remainingEmployeeId)));
            }
        }

        // Unassign the last employee
        given()
        .when()
            .delete(getApiPath("/employees/" + employees[4].getId() + "/unassign-seat/" + seat.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode());

        // Verify the seat has no employees
        session.clear();
        verifiedSeat = session.createQuery(
            "SELECT DISTINCT s FROM Seat s " +
            "LEFT JOIN FETCH s.employees " +
            "WHERE s.id = :seatId", 
            Seat.class)
            .setParameter("seatId", seat.getId())
            .uniqueResult();
        
        assertEquals(0, verifiedSeat.getEmployees().size());
        assertTrue(verifiedSeat.getEmployees().isEmpty());
    }
} 