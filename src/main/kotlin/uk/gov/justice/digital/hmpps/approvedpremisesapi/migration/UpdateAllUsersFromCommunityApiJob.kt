package uk.gov.justice.digital.hmpps.approvedpremisesapi.migration

import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.approvedpremisesapi.jpa.entity.UserRepository
import uk.gov.justice.digital.hmpps.approvedpremisesapi.service.UserService

class UpdateAllUsersFromCommunityApiJob(
  private val userRepository: UserRepository,
  private val userService: UserService
) : MigrationJob() {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun process() {
    userRepository.findAll().forEach {
      log.info("Updating user ${it.id}")
      try {
        userService.updateUserFromCommunityApiById(it.id)
      } catch (exception: Exception) {
        log.error("Unable to update user ${it.id}", exception)
      }
      Thread.sleep(500)
    }
  }
}