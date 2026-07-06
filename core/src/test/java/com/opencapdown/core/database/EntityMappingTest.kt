package com.opencapdown.core.database
import com.opencapdown.core.domain.models.Chapter
import com.opencapdown.core.domain.models.IntegrityStatus
import com.opencapdown.core.database.entities.toDomain
import com.opencapdown.core.database.entities.toEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EntityMappingTest {
    @Test
    fun `chapter entity maps to domain and back`() {
        val domain = Chapter(
            id = "verdinha-123-c1",
            mangaId = "verdinha-123",
            number = 1f,
            title = "Capítulo 1",
            pageCount = 10,
            integrityStatus = IntegrityStatus.COMPLETE
        )
        val entity = domain.toEntity()
        val restored = entity.toDomain()
        assertEquals(domain, restored)
    }
}
