package com.example.demo

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.data.repository.query.Param
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ResponseStatus
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import kotlin.reflect.KClass

fun <T> Optional<T>.unwrap(): T? = orElse(null) // https://stackoverflow.com/a/38767769/

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class BadRequestException(message: String): RuntimeException(message)

@Entity
data class Product(@Id @JsonProperty("product_id") val productId: String, val name: String, val category: String)
@Entity
data class ProductScore(@Id @JsonProperty("product_id") val productId: String, val score: Double)
data class ProductWithScore(val product: Product, val score: Double)
@Entity
data class Category(@Id val category: String, val score: Double?)
@Entity
data class Interest(@Id @JsonIgnore val id: String,
                    @JsonProperty("product_id") val productId: String,
                    @JsonProperty("user_id") val userId: String,
                    val score: Float){
    fun genId() = "$productId$userId"
}
@Entity
data class User(@Id @JsonProperty("user_id") val userId: String)

interface ProductsRepository : JpaRepository<Product, String> {
    fun findAllByCategory(category: String) : List<Product>
    @Query(value = "SELECT p FROM Product p JOIN ProductScore")
    fun findAllWithScore(pageable: Pageable): Page<Product>
}
interface ProductScoreRepository : JpaRepository<ProductScore, String>
interface CategoryRepository : JpaRepository<Category, String>
interface UserRepository : JpaRepository<User, String>

// TODO improve this
const val INTERESTS_VIEW = """
    Interest i 
        INNER JOIN Product p ON i.productId = p.productId
        INNER JOIN Category c ON p.category = c.category
"""

interface InterestsRepository : JpaRepository<Interest, String> {
    fun findAllByProductId(productId: String) : List<Interest>
//    @Query("SELECT p.productId as productId, AVG(score) as score $INTERESTS_VIEW WHERE p.productId = :pi")
//    fun findProductScore(@Param("pi") productId: String): ProductScore?
//    @Query("SELECT c.category as productId, AVG(score) as score $INTERESTS_VIEW WHERE c.category = :cat")
//    fun findCategoryScore(@Param("cat") category: String): Category?
    @Query("SELECT new ProductScore(p.productId, AVG(i.score) as scoreMean) FROM $INTERESTS_VIEW GROUP BY p.productId")
    fun findAllProductScores(pageable: Pageable): Page<ProductScore>
    @Query("SELECT new Category (c.category, AVG(i.score) as scoreMean) FROM $INTERESTS_VIEW GROUP BY c.category")
    fun findAllCategoryScores(pageable: Pageable): Page<Category>
}

class NotFoundException(private val msg: String) : Exception(msg)

abstract class MyService<T : Any>(private val repository: CrudRepository<T, String>) {
    private fun findById(id: String) : T? = repository.findById(id).unwrap()
    fun findByIdOrFail(id: String) : T = findById(id) ?: throw NotFoundException("${this.javaClass.simpleName}: id [$id] doesn't exist.")
    fun save(obj: T) : T = repository.save(obj)
}

abstract class MyServiceWithScore<T : Any,S>(private val repository: JpaRepository<T, String>) : MyService<T>(repository) {
    abstract fun findAll(q: String, limit: Int, reverse: Boolean, save: Boolean = true) : List<S>
}

@Service
class ProductsService(val products: ProductsRepository,
                      val productScores: ProductScoreRepository,
                      val categories: CategoryRepository,
                      val interests: InterestsRepository) : MyServiceWithScore<Product, ProductWithScore>(products) {

    override fun findAll(q: String, limit: Int, reverse: Boolean, save: Boolean): List<ProductWithScore> {
        val scores = updateScores(q, limit, reverse, save)
        return products.findAllById(scores.mapNotNull { it.productId })
            .map{ p -> ProductWithScore(p, scores.find { it.productId == p.productId }!!.score) }
    }

    fun updateScores(q: String, limit: Int, reverse: Boolean, save: Boolean = true): List<ProductScore> {
        val sort = if (reverse) { Sort.by("scoreMean") } else { Sort.by("scoreMean").descending() }
        val scores = interests.findAllProductScores(PageRequest.of(0, limit, sort))
        return if(save) productScores.saveAll(scores) else scores.content
    }

    fun put(products: List<Product>): List<Product> {
        val newCategories = products.map { it.category }.distinct()
        this.categories.deleteAll()
        this.categories.saveAll(newCategories.map { Category(it, null) })

        this.interests.deleteAll()

        this.products.deleteAll()
        return this.products.saveAll(products)
    }
}

@Service
class CategoriesService(val categories: CategoryRepository,
                        val interests: InterestsRepository) : MyServiceWithScore<Category, Category>(categories) {
    override fun findAll(q: String, limit: Int, reverse: Boolean, save: Boolean): List<Category> {
        val sort = if (reverse) { Sort.by("scoreMean") } else { Sort.by("scoreMean").descending() }
        val scores = interests.findAllCategoryScores(PageRequest.of(0, limit, sort))
        val cats = categories.findAllById(scores.mapNotNull { it.category })
            .map{ c -> c.copy(score= scores.find { it.category == c.category }!!.score!!) }
        return if(save) categories.saveAll(cats) else cats
    }
}

enum class UpdateScore { ON_WRITE, ON_READ }
val updateScore: UpdateScore = UpdateScore.ON_WRITE

@Service
class InterestsService(val interests: InterestsRepository,
                       val productsService: ProductsService,
                       val categoriesService: CategoriesService) : MyService<Interest>(interests) {
    fun saveInterest(interest: Interest): Interest {
        val found = interests.findById(interest.genId())
        if(found.isPresent){
            found.get().apply {
                throw BadRequestException("Interest already exists for user $userId and product $productId.") }
        } else {
            val newInterest = interest.copy(id = interest.genId())
            val product = productsService.findByIdOrFail(interest.productId)
            val category = categoriesService.findByIdOrFail(product.category)
            if(updateScore == UpdateScore.ON_WRITE){
//                productsService.updateScores(product, productsService.calculateScore(product))
//                categoriesService.updateScores(category, categoriesService.calculateScore(category))
            }
            return interests.save(newInterest) // TODO rename enclosing method
        }
    }

    fun findAllByProductId(productId: String) : List<Interest> = interests.findAllByProductId(productId)
}