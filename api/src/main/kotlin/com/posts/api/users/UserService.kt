package com.posts.api.users

import com.posts.api.error.DataNotFoundException
import com.posts.api.error.FieldException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class UserService(private val userRepo: UserRepo) {

  fun findAll(): Iterable<User> = userRepo.findAll()

  fun findOne(id: Long): User = getUserById(id)

  @Transactional
  fun create(user: User, encodedPassword: String): User {
    if (userRepo.findOneByEmail(user.email) != null) throw FieldException(hashMapOf("email" to "the email already exists"))
    user.password = encodedPassword
    return userRepo.save(user)
  }

  @Transactional
  fun update(id: Long, updatedUser: User): User {
    val user = getUserById(id).apply {
      name = updatedUser.name
      updatedate = LocalDateTime.now()
    }
    return userRepo.save(user)
  }

  @Transactional
  fun delete(id: Long) = userRepo.deleteById(getUserById(id).id)

  fun getUserByEmail(email: String): User = userRepo.findOneByEmail(email)
    ?: throw FieldException(hashMapOf("email" to "the email doesn't exists"))

  private fun getUserById(id: Long): User = userRepo.findByIdOrNull(id)
    ?: throw DataNotFoundException("User with id not found.")
}