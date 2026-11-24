package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BookingServiceTests {
    @Autowired private EventRepository events;
    @Autowired private BookingRepository bookings;
    @Autowired private BookingService service;

    @Test
    void concurrentRequestsCannotOversellLastTicket() throws Exception {
        Event event = events.saveAndFlush(new Event("Last ticket", 1));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> attemptBooking(start, event.getId(), "Alice")),
                    executor.submit(() -> attemptBooking(start, event.getId(), "Bob")));
            start.countDown();
            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) successes++;
            }
            assertEquals(1, successes);
            assertEquals(0, events.findById(event.getId()).orElseThrow().getAvailableTickets());
            assertEquals(1, bookings.findAll().stream().filter(b -> event.getId().equals(b.getEventId())).count());
            assertEquals("Sold out", assertThrows(RuntimeException.class,
                    () -> service.bookTicket(event.getId(), "Charlie")).getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean attemptBooking(CountDownLatch start, Long eventId, String buyer) throws InterruptedException {
        start.await();
        try {
            service.bookTicket(eventId, buyer);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
