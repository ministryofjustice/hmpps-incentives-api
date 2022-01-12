package uk.gov.justice.digital.hmpps.incentivesapi.utils

import org.springframework.stereotype.Component

@Component
object UserContext {
  private val authToken = ThreadLocal<String>()
  fun getAuthToken() = authToken.get()
  fun setAuthToken(aToken: String?) = authToken.set(aToken)
}
