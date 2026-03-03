package com.example.securevaultcamera

import java.io.File

data class EncryptedPhoto(
    val file: File,
    val label: String
)
