package com.example.demo

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.JpaRepository

import javax.persistence.Entity
import javax.persistence.Id
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*

@SpringBootApplication
class DemoApplication

@RestController
class ProductsController(val productsService: ProductsService) {
	@GetMapping("/products")
	fun get(@RequestParam(defaultValue = "score") q: String,
			@RequestParam(defaultValue = "10") limit: Int,
			@RequestParam(defaultValue = "false") reverse: Boolean
	) = productsService.findAll(q, limit, reverse)

	@PutMapping("/products")
	fun put(@RequestBody products: List<Product>): List<Product> = productsService.put(products)
}

@RestController
class CategoriesController(val categoriesService: CategoriesService) {
	@GetMapping("/categories")
	fun get(@RequestParam(defaultValue = "score") q: String,
			@RequestParam(defaultValue = "10") limit: Int,
			@RequestParam(defaultValue = "false") reverse: Boolean
	) = categoriesService.findAll(q, limit, reverse)

	@PostMapping("/categories")
	fun post(@RequestBody category: String) = categoriesService.save(Category(category, null))
}

@RestController
class InterestsController(val interestsService: InterestsService) {
	@PostMapping("/interest")
	fun post(@RequestBody interest: Interest): Interest = interestsService.saveInterest(interest)
}


@Configuration
internal class LoadDatabase {
	@Bean
	fun initDatabase(products: ProductsRepository, users: UserRepository): CommandLineRunner {
		return CommandLineRunner { _ : Array<String?>? ->
			run { listOf("alice", "bob", "charlie").map{ println("Preloading " + users.save(User(it))) }}

			println("Preloading " + products.save(Product("kotlin","Kotlin", "programming_languages")))
			println("Preloading " + products.save(Product("js","JavaScript", "programming_languages")))
		}
	}

//	companion object {
//		private val log: Logger = LoggerFactory.getLogger(LoadDatabase::class.java)
//	}
}

fun main(args: Array<String>) {
	runApplication<DemoApplication>(*args)
}
