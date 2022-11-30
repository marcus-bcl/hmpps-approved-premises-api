package uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Repository
interface MoveOnCategoryRepository : JpaRepository<MoveOnCategoryEntity, UUID> {
  @Query("SELECT m FROM MoveOnCategoryEntity m WHERE m.serviceScope = :serviceName OR m.serviceScope = '*'")
  fun findAllByServiceScope(serviceName: String): List<MoveOnCategoryEntity>
}

@Entity
@Table(name = "move_on_categories")
data class MoveOnCategoryEntity(
  @Id
  val id: UUID,
  val name: String,
  val isActive: Boolean,
  val serviceScope: String,
) {
  override fun toString() = "MoveOnCategoryEntity:$id"
}
