package com.ivarna.mkm.data.provider

object CpuPolicyMapping {
    fun parseCpuList(content: String): List<Int> = content
        .trim()
        .split(Regex("[,\\s]+"))
        .filter { it.isNotBlank() }
        .flatMap { token ->
            val parts = token.split('-', limit = 2)
            if (parts.size == 2) {
                val start = parts[0].toIntOrNull()
                val end = parts[1].toIntOrNull()
                if (start != null && end != null && end >= start) (start..end).toList() else emptyList()
            } else {
                listOfNotNull(token.toIntOrNull())
            }
        }
        .distinct()
        .sorted()
}
