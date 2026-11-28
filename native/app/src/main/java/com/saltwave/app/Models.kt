package com.saltwave.app

data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val count: Int,
    val coverId: String?,
    val demoRes: Int? = null,
)

data class Playlist(
    val id: String,
    val name: String,
    val count: Int,
    val coverId: String?,
    val demoRes: Int? = null,
)

data class Artist(
    val id: String,
    val name: String,
    val count: Int,
    val coverId: String?,
    val demoRes: Int? = null,
)

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val coverId: String?,
    val albumId: String?,
    val demoRes: Int? = null,
)
