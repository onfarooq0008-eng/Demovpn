package com.fastvpnn.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class ServerMergeTest {
    private val wg = Server(id = "w1", endpointHost = "h", serverPublicKey = "k")
    private val ov = Server(id = "ovpn-1", protocol = Server.PROTOCOL_OPENVPN, endpointHost = "1.1.1.1")
    private val boom = Result.failure<List<Server>>(IOException("down"))

    @Test fun combinesBoth() =
        assertEquals(listOf("w1", "ovpn-1"), ServerMerge.merge(Result.success(listOf(wg)), Result.success(listOf(ov)), null).map { it.id })

    @Test fun openVpnDownKeepsCachedOpenVpn() =
        assertEquals(listOf("w1", "ovpn-1"), ServerMerge.merge(Result.success(listOf(wg)), boom, listOf(ov)).map { it.id })

    @Test fun backendDownStillShowsOpenVpn() =
        assertEquals(listOf("ovpn-1"), ServerMerge.merge(boom, Result.success(listOf(ov)), null).map { it.id })

    @Test fun bothDownThrows() {
        assertThrows(IOException::class.java) { ServerMerge.merge(boom, boom, listOf(wg)) }
    }
}
