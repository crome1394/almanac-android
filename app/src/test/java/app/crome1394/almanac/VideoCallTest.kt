package app.crome1394.almanac

import app.crome1394.almanac.data.VideoCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoCallTest {
    @Test fun findsServices() {
        assertEquals(VideoCall("Google Meet", "https://meet.google.com/abc-defg-hij"),
            VideoCall.find("", "Join: https://meet.google.com/abc-defg-hij."))
        assertEquals("Zoom", VideoCall.find("https://us02web.zoom.us/j/123?pwd=x")?.service)
        assertEquals("Microsoft Teams", VideoCall.find("<a href=\"https://teams.microsoft.com/l/meetup-join/1\">join</a>")?.service)
        assertEquals("Jitsi Meet", VideoCall.find("https://meet.jit.si/OurRoom")?.service)
        assertEquals("Nextcloud Talk", VideoCall.find("https://cloud.example.com/call/ab12cd34")?.service)
    }

    @Test fun locationFirst() =
        assertEquals("Zoom", VideoCall.find("https://zoom.us/j/1", "https://meet.google.com/x")?.service)

    @Test fun ignoresLookalikesAndHttp() {
        assertNull(VideoCall.find("https://meet.google.com.evil.example/x"))
        assertNull(VideoCall.find("https://notzoom.us/j/1"))
        assertNull(VideoCall.find("http://meet.google.com/x"))
        assertNull(VideoCall.find("Room 4", "Agenda: https://example.com/agenda"))
    }
}
