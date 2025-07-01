package com.officemanagement.resource;

import com.officemanagement.model.Employee;
import com.officemanagement.model.Floor;
import com.officemanagement.model.OfficeRoom;
import com.officemanagement.model.Seat;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.LocalDateTime;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class EmployeeResourceTest extends BaseResourceTest {

    @Test
    public void testCreateEmployee() {
        Employee employee = new Employee();
        employee.setFullName("John Doe");
        employee.setOccupation("Software Engineer");
        employee.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(employee)
        .when()
            .post(getApiPath("/employees"))
        .then()
            .statusCode(Response.Status.CREATED.getStatusCode())
            .body("id", notNullValue())
            .body("fullName", equalTo("John Doe"))
            .body("occupation", equalTo("Software Engineer"));
    }

    @Test
    public void testGetEmployee() {
        // First create an employee directly in the database
        Employee employee = new Employee();
        employee.setFullName("Jane Smith");
        employee.setOccupation("Product Manager");
        employee.setCreatedAt(LocalDateTime.now());
        session.save(employee);
        commitAndStartNewTransaction();

        // Then get the employee through the API
        given()
        .when()
            .get(getApiPath("/employees/" + employee.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("fullName", equalTo("Jane Smith"))
            .body("occupation", equalTo("Product Manager"));
    }

    @Test
    public void testGetNonExistentEmployee() {
        given()
        .when()
            .get(getApiPath("/employees/999999"))
        .then()
            .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testSearchEmployees() {
        // Create test employees directly in the database
        createTestEmployee("John Developer", "Software Engineer");
        createTestEmployee("Jane Designer", "UI Designer");
        createTestEmployee("Bob Manager", "Product Manager");
        commitAndStartNewTransaction();
        flushAndClear();

        // Search by name
        given()
            .queryParam("search", "John")
            .queryParam("page", "0")
            .queryParam("size", "10")
        .when()
            .get(getApiPath("/employees/search"))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("content", hasSize(greaterThanOrEqualTo(1)))
            .body("content.find { it.fullName == 'John Developer' }.occupation", 
                  equalTo("Software Engineer"));

        // Search by occupation
        given()
            .queryParam("search", "Engineer")
            .queryParam("page", "0")
            .queryParam("size", "10")
        .when()
            .get(getApiPath("/employees/search"))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("content", hasSize(greaterThanOrEqualTo(1)))
            .body("content.find { it.occupation == 'Software Engineer' }", notNullValue());
    }

    @Test
    public void testAssignAndUnassignSeat() {
        // Create test data directly in the database
        Employee employee = new Employee();
        employee.setFullName("Test Employee");
        employee.setOccupation("Tester");
        employee.setCreatedAt(LocalDateTime.now());
        session.save(employee);

        // Create a floor first
        Floor floor = new Floor();
        floor.setName("Test Floor");
        floor.setFloorNumber(1);
        floor.setCreatedAt(LocalDateTime.now());
        session.save(floor);

        // Create a room
        OfficeRoom room = new OfficeRoom();
        room.setName("Test Room");
        room.setRoomNumber("101");
        room.setFloor(floor);
        room.setCreatedAt(LocalDateTime.now());
        session.save(room);

        // Create a seat with room reference
        Seat seat = new Seat();
        seat.setSeatNumber("Test Seat");
        seat.setCreatedAt(LocalDateTime.now());
        seat.setRoom(room);
        session.save(seat);
        
        commitAndStartNewTransaction();
        flushAndClear();

        // Assign seat to employee
        given()
        .when()
            .put(getApiPath("/employees/" + employee.getId() + "/assign-seat/" + seat.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(1))
            .body("seats[0].id", equalTo(seat.getId().intValue()));

        // Unassign seat from employee
        given()
        .when()
            .delete(getApiPath("/employees/" + employee.getId() + "/unassign-seat/" + seat.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(0));
    }

    @Test
    public void testCreateEmployeeWithInvalidData() {
        // Test with empty employee
        Employee emptyEmployee = new Employee();
        emptyEmployee.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(emptyEmployee)
        .when()
            .post(getApiPath("/employees"))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // Test with null values
        Employee nullEmployee = new Employee();
        nullEmployee.setFullName(null);
        nullEmployee.setOccupation(null);
        nullEmployee.setCreatedAt(LocalDateTime.now());

        given()
            .contentType(ContentType.JSON)
            .body(nullEmployee)
        .when()
            .post(getApiPath("/employees"))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // Test with invalid content type
        given()
            .contentType(ContentType.TEXT)
            .body("Invalid data")
        .when()
            .post(getApiPath("/employees"))
        .then()
            .statusCode(Response.Status.UNSUPPORTED_MEDIA_TYPE.getStatusCode());
    }

    @Test
    public void testAssignSeatWithInvalidIds() {
        // Test with non-existent employee ID
        given()
        .when()
            .put(getApiPath("/employees/99999/assign-seat/1"))
        .then()
            .statusCode(Response.Status.NOT_FOUND.getStatusCode());

        // Create an employee but use invalid seat ID
        Employee employee = new Employee();
        employee.setFullName("Test Employee");
        employee.setOccupation("Tester");
        employee.setCreatedAt(LocalDateTime.now());
        session.save(employee);
        commitAndStartNewTransaction();

        given()
        .when()
            .put(getApiPath("/employees/" + employee.getId() + "/assign-seat/99999"))
        .then()
            .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testSearchEmployeesWithInvalidParameters() {
        // Test with negative page number
        given()
            .queryParam("search", "John")
            .queryParam("page", "-1")
            .queryParam("size", "10")
        .when()
            .get(getApiPath("/employees/search"))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // Test with negative page size
        given()
            .queryParam("search", "John")
            .queryParam("page", "0")
            .queryParam("size", "-1")
        .when()
            .get(getApiPath("/employees/search"))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // Test with extremely large page size
        given()
            .queryParam("search", "John")
            .queryParam("page", "0")
            .queryParam("size", "1000000")
        .when()
            .get(getApiPath("/employees/search"))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    public void testAssignMultipleSeatsToOneEmployee() {
        // Create test data directly in the database
        Employee employee = new Employee();
        employee.setFullName("Multi-Seat Employee");
        employee.setOccupation("Flexible Worker");
        employee.setCreatedAt(LocalDateTime.now());
        session.save(employee);

        // Create a floor
        Floor floor = new Floor();
        floor.setName("Test Floor");
        floor.setFloorNumber(1);
        floor.setCreatedAt(LocalDateTime.now());
        session.save(floor);

        // Create two rooms
        OfficeRoom room1 = new OfficeRoom();
        room1.setName("Room 1");
        room1.setRoomNumber("101");
        room1.setFloor(floor);
        room1.setCreatedAt(LocalDateTime.now());
        session.save(room1);

        OfficeRoom room2 = new OfficeRoom();
        room2.setName("Room 2");
        room2.setRoomNumber("102");
        room2.setFloor(floor);
        room2.setCreatedAt(LocalDateTime.now());
        session.save(room2);

        // Create three seats in different rooms
        Seat seat1 = new Seat();
        seat1.setSeatNumber("Seat 1");
        seat1.setCreatedAt(LocalDateTime.now());
        seat1.setRoom(room1);
        session.save(seat1);

        Seat seat2 = new Seat();
        seat2.setSeatNumber("Seat 2");
        seat2.setCreatedAt(LocalDateTime.now());
        seat2.setRoom(room1);
        session.save(seat2);

        Seat seat3 = new Seat();
        seat3.setSeatNumber("Seat 3");
        seat3.setCreatedAt(LocalDateTime.now());
        seat3.setRoom(room2);
        session.save(seat3);
        
        commitAndStartNewTransaction();
        flushAndClear();

        // Assign first seat to employee
        given()
        .when()
            .put(getApiPath("/employees/" + employee.getId() + "/assign-seat/" + seat1.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(1))
            .body("seats[0].id", equalTo(seat1.getId().intValue()));

        // Assign second seat to the same employee
        given()
        .when()
            .put(getApiPath("/employees/" + employee.getId() + "/assign-seat/" + seat2.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(2))
            .body("seats.id", hasItems(seat1.getId().intValue(), seat2.getId().intValue()));

        // Assign third seat to the same employee (in a different room)
        given()
        .when()
            .put(getApiPath("/employees/" + employee.getId() + "/assign-seat/" + seat3.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(3))
            .body("seats.id", hasItems(
                seat1.getId().intValue(), 
                seat2.getId().intValue(),
                seat3.getId().intValue()
            ));

        // Verify the employee has all three seats using a direct database query
        session.clear(); // Clear the session to ensure we get fresh data
        Employee verifiedEmployee = session.createQuery(
            "SELECT DISTINCT e FROM Employee e " +
            "LEFT JOIN FETCH e.seats s " +
            "LEFT JOIN FETCH s.room " +
            "WHERE e.id = :employeeId", 
            Employee.class)
            .setParameter("employeeId", employee.getId())
            .uniqueResult();
        
        assertEquals(3, verifiedEmployee.getSeats().size());
        assertTrue(verifiedEmployee.getSeats().stream()
            .map(Seat::getId)
            .anyMatch(id -> id.equals(seat1.getId())));
        assertTrue(verifiedEmployee.getSeats().stream()
            .map(Seat::getId)
            .anyMatch(id -> id.equals(seat2.getId())));
        assertTrue(verifiedEmployee.getSeats().stream()
            .map(Seat::getId)
            .anyMatch(id -> id.equals(seat3.getId())));

        // Unassign one seat and verify the other two remain
        given()
        .when()
            .delete(getApiPath("/employees/" + employee.getId() + "/unassign-seat/" + seat2.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(2))
            .body("seats.id", hasItems(
                seat1.getId().intValue(),
                seat3.getId().intValue()
            ));

        // Verify the employee still has the other two seats
        session.clear();
        verifiedEmployee = session.createQuery(
            "SELECT DISTINCT e FROM Employee e " +
            "LEFT JOIN FETCH e.seats s " +
            "LEFT JOIN FETCH s.room " +
            "WHERE e.id = :employeeId", 
            Employee.class)
            .setParameter("employeeId", employee.getId())
            .uniqueResult();
        
        assertEquals(2, verifiedEmployee.getSeats().size());
        assertTrue(verifiedEmployee.getSeats().stream()
            .map(Seat::getId)
            .anyMatch(id -> id.equals(seat1.getId())));
        assertTrue(verifiedEmployee.getSeats().stream()
            .map(Seat::getId)
            .anyMatch(id -> id.equals(seat3.getId())));
        assertFalse(verifiedEmployee.getSeats().stream()
            .map(Seat::getId)
            .anyMatch(id -> id.equals(seat2.getId())));
    }

    @Test
    public void testComplexSeatAssignmentScenarios() {
        // Create test data: 3 employees and 3 seats
        Employee employee1 = new Employee();
        employee1.setFullName("Employee 1");
        employee1.setOccupation("Developer");
        employee1.setCreatedAt(LocalDateTime.now());
        session.save(employee1);

        Employee employee2 = new Employee();
        employee2.setFullName("Employee 2");
        employee2.setOccupation("Designer");
        employee2.setCreatedAt(LocalDateTime.now());
        session.save(employee2);

        Employee employee3 = new Employee();
        employee3.setFullName("Employee 3");
        employee3.setOccupation("Manager");
        employee3.setCreatedAt(LocalDateTime.now());
        session.save(employee3);

        // Create a floor
        Floor floor = new Floor();
        floor.setName("Complex Test Floor");
        floor.setFloorNumber(2);
        floor.setCreatedAt(LocalDateTime.now());
        session.save(floor);

        // Create a room
        OfficeRoom room = new OfficeRoom();
        room.setName("Complex Test Room");
        room.setRoomNumber("201");
        room.setFloor(floor);
        room.setCreatedAt(LocalDateTime.now());
        session.save(room);

        // Create three seats
        Seat seat1 = new Seat();
        seat1.setSeatNumber("Complex Seat 1");
        seat1.setCreatedAt(LocalDateTime.now());
        seat1.setRoom(room);
        session.save(seat1);

        Seat seat2 = new Seat();
        seat2.setSeatNumber("Complex Seat 2");
        seat2.setCreatedAt(LocalDateTime.now());
        seat2.setRoom(room);
        session.save(seat2);

        Seat seat3 = new Seat();
        seat3.setSeatNumber("Complex Seat 3");
        seat3.setCreatedAt(LocalDateTime.now());
        seat3.setRoom(room);
        session.save(seat3);
        
        commitAndStartNewTransaction();
        flushAndClear();

        // Scenario 1: Assign multiple employees to the same seat
        // Assign employee1 to seat1
        given()
        .when()
            .put(getApiPath("/employees/" + employee1.getId() + "/assign-seat/" + seat1.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(1))
            .body("seats[0].id", equalTo(seat1.getId().intValue()));

        // Assign employee2 to the same seat (seat1)
        given()
        .when()
            .put(getApiPath("/employees/" + employee2.getId() + "/assign-seat/" + seat1.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(1))
            .body("seats[0].id", equalTo(seat1.getId().intValue()));

        // Verify seat1 has both employees
        session.clear();
        Seat verifiedSeat1 = session.createQuery(
            "SELECT DISTINCT s FROM Seat s " +
            "LEFT JOIN FETCH s.employees " +
            "WHERE s.id = :seatId", 
            Seat.class)
            .setParameter("seatId", seat1.getId())
            .uniqueResult();
        
        assertEquals(2, verifiedSeat1.getEmployees().size());
        assertTrue(verifiedSeat1.getEmployees().stream()
            .map(Employee::getId)
            .anyMatch(id -> id.equals(employee1.getId())));
        assertTrue(verifiedSeat1.getEmployees().stream()
            .map(Employee::getId)
            .anyMatch(id -> id.equals(employee2.getId())));

        // Scenario 2: Assign multiple seats to the same employee
        // Assign employee3 to seat2
        given()
        .when()
            .put(getApiPath("/employees/" + employee3.getId() + "/assign-seat/" + seat2.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(1))
            .body("seats[0].id", equalTo(seat2.getId().intValue()));

        // Assign employee3 to seat3 as well
        given()
        .when()
            .put(getApiPath("/employees/" + employee3.getId() + "/assign-seat/" + seat3.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(2))
            .body("seats.id", hasItems(
                seat2.getId().intValue(),
                seat3.getId().intValue()
            ));

        // Verify employee3 has both seats
        session.clear();
        Employee verifiedEmployee3 = session.createQuery(
            "SELECT DISTINCT e FROM Employee e " +
            "LEFT JOIN FETCH e.seats " +
            "WHERE e.id = :employeeId", 
            Employee.class)
            .setParameter("employeeId", employee3.getId())
            .uniqueResult();
        
        assertEquals(2, verifiedEmployee3.getSeats().size());
        assertTrue(verifiedEmployee3.getSeats().stream()
            .map(Seat::getId)
            .anyMatch(id -> id.equals(seat2.getId())));
        assertTrue(verifiedEmployee3.getSeats().stream()
            .map(Seat::getId)
            .anyMatch(id -> id.equals(seat3.getId())));

        // Scenario 3: Complex reassignment - assign employee1 to a seat already assigned to employee3
        given()
        .when()
            .put(getApiPath("/employees/" + employee1.getId() + "/assign-seat/" + seat2.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(2))
            .body("seats.id", hasItems(
                seat1.getId().intValue(),
                seat2.getId().intValue()
            ));

        // Verify seat2 now has both employee1 and employee3
        session.clear();
        Seat verifiedSeat2 = session.createQuery(
            "SELECT DISTINCT s FROM Seat s " +
            "LEFT JOIN FETCH s.employees " +
            "WHERE s.id = :seatId", 
            Seat.class)
            .setParameter("seatId", seat2.getId())
            .uniqueResult();
        
        assertEquals(2, verifiedSeat2.getEmployees().size());
        assertTrue(verifiedSeat2.getEmployees().stream()
            .map(Employee::getId)
            .anyMatch(id -> id.equals(employee1.getId())));
        assertTrue(verifiedSeat2.getEmployees().stream()
            .map(Employee::getId)
            .anyMatch(id -> id.equals(employee3.getId())));

        // Scenario 4: Unassign a seat with multiple employees and verify other employee still has it
        given()
        .when()
            .delete(getApiPath("/employees/" + employee1.getId() + "/unassign-seat/" + seat1.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(1))
            .body("seats[0].id", equalTo(seat2.getId().intValue()));

        // Verify seat1 still has employee2
        session.clear();
        verifiedSeat1 = session.createQuery(
            "SELECT DISTINCT s FROM Seat s " +
            "LEFT JOIN FETCH s.employees " +
            "WHERE s.id = :seatId", 
            Seat.class)
            .setParameter("seatId", seat1.getId())
            .uniqueResult();
        
        assertEquals(1, verifiedSeat1.getEmployees().size());
        assertEquals(employee2.getId(), verifiedSeat1.getEmployees().iterator().next().getId());

        // Scenario 5: Attempt to assign the same seat twice to the same employee (should be idempotent)
        given()
        .when()
            .put(getApiPath("/employees/" + employee3.getId() + "/assign-seat/" + seat3.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(2)); // Still has 2 seats, no duplicates

        // Verify employee3 still has exactly 2 seats
        session.clear();
        verifiedEmployee3 = session.createQuery(
            "SELECT DISTINCT e FROM Employee e " +
            "LEFT JOIN FETCH e.seats " +
            "WHERE e.id = :employeeId", 
            Employee.class)
            .setParameter("employeeId", employee3.getId())
            .uniqueResult();
        
        assertEquals(2, verifiedEmployee3.getSeats().size());
    }

    @Test
    public void testSeatAssignmentEdgeCases() {
        // Create test data
        Employee employee = new Employee();
        employee.setFullName("Edge Case Employee");
        employee.setOccupation("Tester");
        employee.setCreatedAt(LocalDateTime.now());
        session.save(employee);

        // Create a floor
        Floor floor = new Floor();
        floor.setName("Edge Case Floor");
        floor.setFloorNumber(4);
        floor.setCreatedAt(LocalDateTime.now());
        session.save(floor);

        // Create a room
        OfficeRoom room = new OfficeRoom();
        room.setName("Edge Case Room");
        room.setRoomNumber("401");
        room.setFloor(floor);
        room.setCreatedAt(LocalDateTime.now());
        session.save(room);

        // Create a seat
        Seat seat = new Seat();
        seat.setSeatNumber("Edge Case Seat");
        seat.setCreatedAt(LocalDateTime.now());
        seat.setRoom(room);
        session.save(seat);
        
        commitAndStartNewTransaction();
        flushAndClear();

        // Edge Case 1: Unassign a seat that was never assigned
        given()
        .when()
            .delete(getApiPath("/employees/" + employee.getId() + "/unassign-seat/" + seat.getId()))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // Edge Case 2: Assign the same seat multiple times (should be idempotent)
        // First assignment
        given()
        .when()
            .put(getApiPath("/employees/" + employee.getId() + "/assign-seat/" + seat.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(1))
            .body("seats[0].id", equalTo(seat.getId().intValue()));

        // Second assignment of the same seat (should not duplicate)
        given()
        .when()
            .put(getApiPath("/employees/" + employee.getId() + "/assign-seat/" + seat.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(1))
            .body("seats[0].id", equalTo(seat.getId().intValue()));

        // Verify the employee still has only one seat
        session.clear();
        Employee verifiedEmployee = session.createQuery(
            "SELECT DISTINCT e FROM Employee e " +
            "LEFT JOIN FETCH e.seats " +
            "WHERE e.id = :employeeId", 
            Employee.class)
            .setParameter("employeeId", employee.getId())
            .uniqueResult();
        
        assertEquals(1, verifiedEmployee.getSeats().size());

        // Edge Case 3: Unassign the same seat multiple times (should be idempotent)
        // First unassignment
        given()
        .when()
            .delete(getApiPath("/employees/" + employee.getId() + "/unassign-seat/" + seat.getId()))
        .then()
            .statusCode(Response.Status.OK.getStatusCode())
            .body("seats", hasSize(0));

        // Second unassignment of the same seat (should not error)
        given()
        .when()
            .delete(getApiPath("/employees/" + employee.getId() + "/unassign-seat/" + seat.getId()))
        .then()
            .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        // Verify the employee has no seats
        session.clear();
        verifiedEmployee = session.createQuery(
            "SELECT DISTINCT e FROM Employee e " +
            "LEFT JOIN FETCH e.seats " +
            "WHERE e.id = :employeeId", 
            Employee.class)
            .setParameter("employeeId", employee.getId())
            .uniqueResult();
        
        assertTrue(verifiedEmployee.getSeats().isEmpty());

        // Edge Case 4: Assign and unassign in rapid succession
        for (int i = 0; i < 5; i++) {
            // Assign
            given()
            .when()
                .put(getApiPath("/employees/" + employee.getId() + "/assign-seat/" + seat.getId()))
            .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("seats", hasSize(1));

            // Unassign
            given()
            .when()
                .delete(getApiPath("/employees/" + employee.getId() + "/unassign-seat/" + seat.getId()))
            .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("seats", hasSize(0));
        }

        // Verify the final state is correct
        session.clear();
        verifiedEmployee = session.createQuery(
            "SELECT DISTINCT e FROM Employee e " +
            "LEFT JOIN FETCH e.seats " +
            "WHERE e.id = :employeeId", 
            Employee.class)
            .setParameter("employeeId", employee.getId())
            .uniqueResult();
        
        assertTrue(verifiedEmployee.getSeats().isEmpty());

        Seat verifiedSeat = session.createQuery(
            "SELECT DISTINCT s FROM Seat s " +
            "LEFT JOIN FETCH s.employees " +
            "WHERE s.id = :seatId", 
            Seat.class)
            .setParameter("seatId", seat.getId())
            .uniqueResult();
        
        assertTrue(verifiedSeat.getEmployees().isEmpty());
    }

    @Test
    public void testDeleteEmployeeWithAssignedSeats() {
        // Create test data directly in the database
        Employee employee = new Employee();
        employee.setFullName("Test Employee");
        employee.setOccupation("Tester");
        employee.setCreatedAt(LocalDateTime.now());
        session.save(employee);

        // Create a floor first
        Floor floor = new Floor();
        floor.setName("Test Floor");
        floor.setFloorNumber(1);
        floor.setCreatedAt(LocalDateTime.now());
        session.save(floor);

        // Create a room
        OfficeRoom room = new OfficeRoom();
        room.setName("Test Room");
        room.setRoomNumber("101");
        room.setFloor(floor);
        room.setCreatedAt(LocalDateTime.now());
        session.save(room);

        // Create two seats with room reference
        Seat seat1 = new Seat();
        seat1.setSeatNumber("Test Seat 1");
        seat1.setCreatedAt(LocalDateTime.now());
        seat1.setRoom(room);
        session.save(seat1);
        
        Seat seat2 = new Seat();
        seat2.setSeatNumber("Test Seat 2");
        seat2.setCreatedAt(LocalDateTime.now());
        seat2.setRoom(room);
        session.save(seat2);
        
        // Assign both seats to the employee
        employee.addSeat(seat1);
        employee.addSeat(seat2);
        session.flush();
        commitAndStartNewTransaction();
        flushAndClear();

        // Delete the employee with assigned seats
        given()
        .when()
            .delete(getApiPath("/employees/" + employee.getId()))
        .then()
            .statusCode(Response.Status.NO_CONTENT.getStatusCode());

        // Verify the employee is deleted
        session.clear();
        Employee deletedEmployee = session.get(Employee.class, employee.getId());
        assertFalse(deletedEmployee != null, "Employee should be deleted");

        // Verify the seats still exist but no longer have the employee assigned
        Seat updatedSeat1 = session.get(Seat.class, seat1.getId());
        Seat updatedSeat2 = session.get(Seat.class, seat2.getId());
        
        assertTrue(updatedSeat1 != null, "Seat 1 should still exist");
        assertTrue(updatedSeat2 != null, "Seat 2 should still exist");
        
        assertTrue(updatedSeat1.getEmployees().isEmpty(), "Seat 1 should not have any employees assigned");
        assertTrue(updatedSeat2.getEmployees().isEmpty(), "Seat 2 should not have any employees assigned");
    }

    @Test
    public void testDeleteNonExistentEmployee() {
        given()
        .when()
            .delete(getApiPath("/employees/999999"))
        .then()
            .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }

    private void createTestEmployee(String fullName, String occupation) {
        Employee employee = new Employee();
        employee.setFullName(fullName);
        employee.setOccupation(occupation);
        employee.setCreatedAt(LocalDateTime.now());
        session.save(employee);
        session.flush();
    }
} 