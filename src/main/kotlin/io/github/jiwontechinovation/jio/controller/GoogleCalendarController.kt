package io.github.jiwontechinovation.jio.controller

import io.github.jiwontechinovation.jio.domain.User
import io.github.jiwontechinovation.jio.security.CurrentUser
import io.github.jiwontechinovation.jio.service.GoogleCalendarService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/calendar")
class GoogleCalendarController(
    private val calendarService: GoogleCalendarService
) {

    @GetMapping("/events")
    fun listEvents(@CurrentUser user: User): ResponseEntity<List<Map<String, Any>>> {
        val events = calendarService.listEvents(user)
        return ResponseEntity.ok(events)
    }

    @PostMapping("/events")
    fun createEvent(@CurrentUser user: User, @RequestBody eventData: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        val event = calendarService.createEvent(user, eventData)
        return ResponseEntity.ok(event)
    }

    @PutMapping("/events/{eventId}")
    fun updateEvent(
        @CurrentUser user: User,
        @PathVariable eventId: String,
        @RequestBody eventData: Map<String, Any>
    ): ResponseEntity<Map<String, Any>> {
        val event = calendarService.updateEvent(user, eventId, eventData)
        return ResponseEntity.ok(event)
    }

    @DeleteMapping("/events/{eventId}")
    fun deleteEvent(@CurrentUser user: User, @PathVariable eventId: String): ResponseEntity<Void> {
        calendarService.deleteEvent(user, eventId)
        return ResponseEntity.ok().build()
    }
}
