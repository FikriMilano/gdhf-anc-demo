package dev.ohs.demo.anc

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
