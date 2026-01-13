package io.github.jiwontechinovation.jio.service

import io.github.jiwontechinovation.jio.domain.User
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class GoogleCalendarService(
    private val googleOAuthService: GoogleOAuthService
) {
    private val restTemplate = RestTemplate()

    /**
     * List events for the authenticated user
     */
    fun listEvents(user: User): List<Map<String, Any>> {
        val accessToken = googleOAuthService.getAccessToken(user.id)
            ?: throw IllegalStateException("Google account not connected")

        val now = LocalDateTime.now()
        val timeMin = now.minusMonths(3).format(DateTimeFormatter.ISO_DATE_TIME) + "Z"
        val timeMax = now.plusMonths(3).format(DateTimeFormatter.ISO_DATE_TIME) + "Z"

        val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events" +
                "?timeMin=$timeMin&timeMax=$timeMax&maxResults=250&singleEvents=true&orderBy=startTime"

        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
        }
        val request = HttpEntity<Any>(headers)

        try {
            val response = restTemplate.exchange(url, HttpMethod.GET, request, Map::class.java)
            val body = response.body as Map<String, Any>
            val items = body["items"] as List<Map<String, Any>>
            return items.map { simplifyEvent(it) }
        } catch (e: Exception) {
            throw RuntimeException("Failed to fetch calendar events: ${e.message}")
        }
    }

    /**
     * Create a new event
     */
    fun createEvent(user: User, eventData: Map<String, Any>): Map<String, Any> {
        val accessToken = googleOAuthService.getAccessToken(user.id)
            ?: throw IllegalStateException("Google account not connected")

        val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events"

        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
        }
        val request = HttpEntity(eventData, headers)

        try {
            val response = restTemplate.postForEntity(url, request, Map::class.java)
            return simplifyEvent(response.body as Map<String, Any>)
        } catch (e: Exception) {
            throw RuntimeException("Failed to create event: ${e.message}")
        }
    }

    /**
     * Update an event
     */
    fun updateEvent(user: User, eventId: String, eventData: Map<String, Any>): Map<String, Any> {
        val accessToken = googleOAuthService.getAccessToken(user.id)
            ?: throw IllegalStateException("Google account not connected")

        val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events/$eventId"

        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
        }
        // Patch allows partial updates, but Google API usually requires PUT for full resource or PATCH for partial.
        // Let's use PATCH for flexibility.
        val request = HttpEntity(eventData, headers)

        try {
            // RestTemplate patchForObject is not standard, use exchange with PATCH
            // Note: older Spring RestTemplate might not support PATCH directly via some request factories. 
            // Standard JDK HttpClient doesn't support PATCH. 
            // We'll trust that the default setup works or user has configured it.
            // If it fails, we might need a different RequestFactory.
            // For safety, let's use POST with method override or PUT if we had full data.
            // But Google supports PATCH.
            // To support PATCH with default RestTemplate, we need to configure it.
            // For now, let's try calling it. If it fails, we fall back to PUT if feasible, but PUT needs full resource.
            // Alternatively, use a library or just accept simpler implementation.
            
            // Actually, let's use PUT if we assume full update, OR try PATCH.
            // Code shows 'update_event' in python did a GET then PUT.
            // Let's implement PATCH behavior by doing GET -> Merge -> PUT ?
            // Or just try PATCH.
             
             // Issue: Standard HttpURLConnection (default in RestTemplate) NOT support PATCH.
             // Workaround: Use Apache HttpClient or OkHttp.
             // OR: Fetch event, update fields, PUT. Safe and standard.
             
             val getUrl = "https://www.googleapis.com/calendar/v3/calendars/primary/events/$eventId"
             val getResp = restTemplate.exchange(getUrl, HttpMethod.GET, HttpEntity<Any>(headers), Map::class.java)
             val existingEvent = getResp.body as MutableMap<String, Any>
             
             // Merge/Overwrite fields
             eventData.forEach { (k, v) -> existingEvent[k] = v }
             
             val putResp = restTemplate.exchange(url, HttpMethod.PUT, HttpEntity(existingEvent, headers), Map::class.java)
             return simplifyEvent(putResp.body as Map<String, Any>)
             
        } catch (e: Exception) {
            throw RuntimeException("Failed to update event: ${e.message}")
        }
    }

    /**
     * Delete an event
     */
    fun deleteEvent(user: User, eventId: String) {
        val accessToken = googleOAuthService.getAccessToken(user.id)
            ?: throw IllegalStateException("Google account not connected")

        val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events/$eventId"

        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
        }
        val request = HttpEntity<Any>(headers)

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, request, Void::class.java)
        } catch (e: Exception) {
            throw RuntimeException("Failed to delete event: ${e.message}")
        }
    }

    private fun simplifyEvent(event: Map<String, Any>): Map<String, Any> {
        val start = event["start"] as? Map<String, Any>
        val end = event["end"] as? Map<String, Any>
        
        return mapOf(
            "id" to (event["id"] ?: ""),
            "summary" to (event["summary"] ?: "(No Title)"),
            "description" to (event["description"] ?: ""),
            "location" to (event["location"] ?: ""),
            "start" to (start?.get("dateTime") ?: start?.get("date") ?: ""),
            "end" to (end?.get("dateTime") ?: end?.get("date") ?: ""),
            "htmlLink" to (event["htmlLink"] ?: "")
        )
    }
}
