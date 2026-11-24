package com.example.demo;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;

    public BookingService(EventRepository eventRepository, BookingRepository bookingRepository) {
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public Booking bookTicket(Long eventId, String buyerName) {
        if (eventId == null || buyerName == null || buyerName.isBlank()) {
            throw new IllegalArgumentException("Event ID and buyer name are required");
        }
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        if (event.getAvailableTickets() <= 0) {
            throw new RuntimeException("Sold out");
        }
        event.setAvailableTickets(event.getAvailableTickets() - 1);
        eventRepository.save(event);
        return bookingRepository.save(new Booking(eventId, buyerName, "CONFIRMED", LocalDateTime.now()));
    }
}
