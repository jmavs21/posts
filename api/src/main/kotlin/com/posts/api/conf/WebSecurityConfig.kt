package com.posts.api.conf

import com.posts.api.error.ErrorFieldException
import com.posts.api.model.User
import com.posts.api.repo.UserRepo
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException
import java.security.Key
import java.util.*
import javax.servlet.FilterChain
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
class WebSecurityConfig : WebSecurityConfigurerAdapter() {
  @Autowired
  private lateinit var jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint

  @Autowired
  private lateinit var jwtUserDetailsService: JwtUserDetailsService

  @Autowired
  private lateinit var jwtRequestFilter: JwtRequestFilter

  @Autowired
  @Throws(Exception::class)
  fun configureGlobal(auth: AuthenticationManagerBuilder) {
    auth.userDetailsService(jwtUserDetailsService).passwordEncoder(passwordEncoder())
  }

  @Throws(Exception::class)
  override fun configure(httpSecurity: HttpSecurity) {
    val pat = arrayOf("/api/posts/**")
    httpSecurity.cors().and().csrf().disable().headers().frameOptions().deny().and().authorizeRequests()
        .antMatchers(HttpMethod.GET, *pat).permitAll().antMatchers("/api/auth", "/api/users")
        .permitAll().anyRequest().authenticated().and().exceptionHandling()
        .authenticationEntryPoint(jwtAuthenticationEntryPoint).and().sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
    httpSecurity.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter::class.java)
  }

  @Throws(Exception::class)
  override fun configure(web: WebSecurity) {
    web.ignoring().antMatchers("/api/v2/api-docs", "/api/configuration/ui", "/api/swagger-resources/**",
        "/api/configuration/security", "/api/swagger-ui.html", "/api/webjars/**", "/h2/**")
  }

  @Bean
  fun passwordEncoder(): PasswordEncoder {
    return BCryptPasswordEncoder()
  }

  @Bean
  fun corsConfigurationSource(): CorsConfigurationSource? {
    val configuration = CorsConfiguration()
    configuration.allowedOrigins = listOf("*")
    configuration.allowedMethods = listOf("HEAD", "GET", "PUT", "POST", "DELETE", "PATCH")
    configuration.allowedHeaders = listOf("*")
    val source = UrlBasedCorsConfigurationSource()
    source.registerCorsConfiguration("/**", configuration)
    return source
  }
}


@Component
class JwtRequestFilter : OncePerRequestFilter() {
  @Autowired
  private lateinit var jwtUserDetailsService: JwtUserDetailsService

  @Autowired
  private lateinit var jwtTokenUtil: JwtTokenUtil

  @Throws(ServletException::class, IOException::class)
  override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
    val jwtToken = request.getHeader("x-auth-token")
    var username: String? = null
    if (jwtToken != null && jwtToken.contains(".")) {
      try {
        username = jwtTokenUtil.getUsernameFromToken(jwtToken)
      } catch (e: IllegalArgumentException) {
        logger.error("Unable to get JWT Token.")
      } catch (e: ExpiredJwtException) {
        logger.error("JWT Token has expired.")
      }
    }
    if (username != null && SecurityContextHolder.getContext().authentication == null) {
      val userDetails = jwtUserDetailsService.loadUserByUsername(username)
      if (jwtTokenUtil.validateToken(jwtToken, userDetails)) {
        val usernamePasswordAuthenticationToken = UsernamePasswordAuthenticationToken(
            userDetails, null, userDetails.authorities)
        usernamePasswordAuthenticationToken.details = WebAuthenticationDetailsSource().buildDetails(request)
        SecurityContextHolder.getContext().authentication = usernamePasswordAuthenticationToken
      }
    }
    filterChain.doFilter(request, response)
  }
}

@Component
class JwtAuthenticationEntryPoint : AuthenticationEntryPoint {
  @Throws(IOException::class, ServletException::class)
  override fun commence(request: HttpServletRequest?, response: HttpServletResponse,
                        authException: AuthenticationException?) = response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
}

@Service
class JwtUserDetailsService(private val userRepo: UserRepo) : UserDetailsService {
  @Throws(UsernameNotFoundException::class)
  override fun loadUserByUsername(email: String): UserDetails = userRepo.findOneByEmail(email)
      ?: throw ErrorFieldException(hashMapOf("email" to "the email doesn't exists"), HttpStatus.BAD_REQUEST)
}

@Component
class JwtTokenUtil {

  private val secret: Key = Keys.secretKeyFor(SignatureAlgorithm.HS256)

  @Value("\${jwt.expiration.millis}")
  private var jwtExpirationMillis: Long = 0

  fun getUsernameFromToken(token: String): String {
    return getAllClaimsFromToken(token).subject
  }

  fun generateToken(user: User): String {
    val claims: HashMap<String, Any> = HashMap()
    claims["id"] = user.id
    claims["name"] = user.name
    claims["email"] = user.email
    return doGenerateToken(claims, user.email)
  }

  fun validateToken(token: String, userDetails: UserDetails): Boolean {
    val username = getUsernameFromToken(token)
    return username == userDetails.username && !isTokenExpired(token)
  }

  private fun getAllClaimsFromToken(token: String?): Claims {
    return Jwts.parserBuilder().setSigningKey(secret).build().parseClaimsJws(token).body
  }

  private fun doGenerateToken(claims: Map<String, Any>, email: String): String {
    return Jwts.builder().setClaims(claims).setSubject(email).setIssuedAt(Date(System.currentTimeMillis()))
        .setExpiration(Date(System.currentTimeMillis() + jwtExpirationMillis))
        .signWith(secret).compact()
  }

  private fun isTokenExpired(token: String): Boolean {
    val expiration: Date = getAllClaimsFromToken(token).expiration
    return expiration.before(Date())
  }
}