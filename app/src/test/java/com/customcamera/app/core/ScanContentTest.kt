package com.customcamera.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanContentTest {

    @Test
    fun `http urls are urls`() {
        val c = ScanContent.parse("https://example.com/a?b=1")
        assertTrue(c is ScanContent.Url)
        assertEquals("https://example.com/a?b=1", (c as ScanContent.Url).url)
    }

    @Test
    fun `bare domains get https prefix`() {
        val c = ScanContent.parse("example.org/path") as ScanContent.Url
        assertEquals("https://example.org/path", c.url)
    }

    @Test
    fun `sentences are not urls`() {
        assertTrue(ScanContent.parse("visit example.com today") is ScanContent.Text)
        assertTrue(ScanContent.parse("1234567890128") is ScanContent.Text)
    }

    @Test
    fun `wifi with all fields in any order`() {
        val c = ScanContent.parse("WIFI:S:Home Net;T:WPA;P:p@ss;H:true;;") as ScanContent.Wifi
        assertEquals("Home Net", c.ssid)
        assertEquals("p@ss", c.password)
        assertEquals("WPA", c.security)
        assertTrue(c.hidden)

        val reordered = ScanContent.parse("WIFI:T:WEP;P:abc;S:Cafe;;") as ScanContent.Wifi
        assertEquals("Cafe", reordered.ssid)
        assertEquals("WEP", reordered.security)
        assertFalse(reordered.hidden)
    }

    @Test
    fun `wifi escapes are honoured`() {
        val c = ScanContent.parse("""WIFI:S:My\;Net;P:a\:b\\c;T:WPA;;""") as ScanContent.Wifi
        assertEquals("My;Net", c.ssid)
        assertEquals("a:b\\c", c.password)
    }

    @Test
    fun `open wifi has no password`() {
        val c = ScanContent.parse("WIFI:S:Guest;T:nopass;P:;;") as ScanContent.Wifi
        assertEquals("nopass", c.security)
        assertNull(c.password)
    }

    @Test
    fun `wifi without ssid falls back to text`() {
        assertTrue(ScanContent.parse("WIFI:T:WPA;P:x;;") is ScanContent.Text)
    }

    @Test
    fun `mailto with subject and body`() {
        val c = ScanContent.parse("mailto:me@example.com?subject=Hi%20there&body=Yo") as ScanContent.Email
        assertEquals("me@example.com", c.address)
        assertEquals("Hi there", c.subject)
        assertEquals("Yo", c.body)
    }

    @Test
    fun `matmsg email`() {
        val c = ScanContent.parse("MATMSG:TO:a@b.co;SUB:Order;BODY:Thanks;;") as ScanContent.Email
        assertEquals("a@b.co", c.address)
        assertEquals("Order", c.subject)
        assertEquals("Thanks", c.body)
    }

    @Test
    fun `phone and sms`() {
        assertEquals("+15551234", (ScanContent.parse("tel:+15551234") as ScanContent.Phone).number)
        val sms = ScanContent.parse("SMSTO:+1555:hello") as ScanContent.Sms
        assertEquals("+1555", sms.number)
        assertEquals("hello", sms.message)
        val bare = ScanContent.parse("sms:+1666") as ScanContent.Sms
        assertNull(bare.message)
    }

    @Test
    fun `geo coordinates are validated`() {
        val g = ScanContent.parse("geo:40.7128,-74.0060?q=NYC") as ScanContent.Geo
        assertEquals(40.7128, g.latitude, 1e-9)
        assertEquals(-74.006, g.longitude, 1e-9)
        assertTrue(ScanContent.parse("geo:200,10") is ScanContent.Text)
        assertTrue(ScanContent.parse("geo:abc") is ScanContent.Text)
    }

    @Test
    fun `raw text is preserved untrimmed`() {
        val c = ScanContent.parse("  hello  ")
        assertEquals("  hello  ", c.raw)
    }
}
